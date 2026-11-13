package com.sleepreaper.kkaeurang.integration

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.sleepreaper.kkaeurang.detector.CabinControl
import kotlin.math.roundToInt

/**
 * 실차용 실내 제어. Pleos Vehicle SDK의 Window·HVAC·Wiper로 운전석 창문과 찬바람을 움직인다.
 *
 * 필요한 권한: pleos.car.permission.CAR_WINDOWS, CONTROL_CAR_WINDOWS, CAR_CLIMATE, CONTROL_CAR_CLIMATE
 * (거부되면 명령이 실패로 돌아오고, 화면에 창문 닫힘으로 보인다)
 *
 * ## 가정 (실차에서 확인 필요)
 * - 창문 원시 값은 차종마다 범위가 달라서, 시작할 때 [checkWindowPositionCapabilities] 범위를 읽어 %로 바꾼다.
 *   **최솟값 = 완전히 닫힘, 최댓값 = 완전히 열림**으로 본다 (AAOS WINDOW_POS와 같은 방식). 반대면 [WINDOW_MIN_IS_CLOSED]를 바꾼다.
 * - 비는 앞유리 와이퍼가 ON이면 온다고 본다.
 * - SDK 콜백이 어느 스레드로 오는지 알 수 없어서, 모든 콜백을 메인 스레드로 넘긴다.
 */
class PleosCabinControl(
    context: Context,
    private val log: (String) -> Unit = {},
) : CabinControl {

    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)

    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var hvacArea = SeatArea(SeatValue.ROW_1_LEFT)   // 시작할 때 지원 영역을 보고 바꾼다

    private var windowBounds: AreaBounds<Int>? = null
    private var fanBounds: AreaBounds<Int>? = null
    private var tempBounds: AreaBounds<Float>? = null

    private var started = false
    private var pendingOps = 0              // 아직 결과가 안 온 명령 수 (다 끝나야 SDK를 해제한다)
    private var releaseWhenIdle = false

    private var currentPercent: Int? = null
    private var raining = false
    private var savedHvac: HvacSnapshot? = null   // 찬바람 켜기 직전 공조 상태

    override val windowOpenPercent: Int? get() = currentPercent
    override val isRaining: Boolean get() = raining
    override var onWindowChanged: ((percent: Int) -> Unit)? = null

    // ── 리스너 (해제할 때 같은 객체를 넘겨야 해서 필드로 둔다) ──

    private val windowListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position == null) return
            onMain {
                val percent = toPercent(position) ?: return@onMain
                if (percent == currentPercent) return@onMain
                currentPercent = percent
                onWindowChanged?.invoke(percent)
            }
        }

        override fun onFailed(e: Exception) = onMain { log("창문 위치 리스너 실패: ${e.message}") }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = onMain {
            val now = state == WiperState.ON
            if (now != raining) log("와이퍼 ${if (now) "작동 → 비로 판단" else "멈춤"}")
            raining = now
        }

        override fun onFailed(e: Exception) = onMain { log("와이퍼 리스너 실패: ${e.message}") }
    }

    // ───────────────────────── 시작·종료 ─────────────────────────

    override fun start() {
        if (started) return
        started = true
        releaseWhenIdle = false
        safely("SDK 초기화") { vehicle.initialize() }
        startWindow()
        startHvac()
        safely("와이퍼 리스너 등록") { vehicle.getWiper().registerWiperState(windshield, wiperListener) }
    }

    override fun stop() {
        if (!started) return
        started = false
        safely("창문 리스너 해제") { vehicle.getWindow().unregisterWindowPosition(driverWindow, windowListener) }
        safely("와이퍼 리스너 해제") { vehicle.getWiper().unregisterWiperState(windshield, wiperListener) }
        // 종료 직전에 보낸 "원래대로" 명령이 끝날 때까지 기다렸다가 해제한다 (최대 RELEASE_TIMEOUT_MS)
        releaseWhenIdle = true
        if (pendingOps == 0) release() else main.postDelayed(::release, RELEASE_TIMEOUT_MS)
    }

    private fun release() {
        if (!releaseWhenIdle) return
        releaseWhenIdle = false
        main.removeCallbacksAndMessages(null)
        safely("SDK 해제") { vehicle.release() }
        log("실내 제어 종료")
    }

    private fun startWindow() {
        safely("창문 범위 조회") {
            vehicle.getWindow().checkWindowPositionCapabilities(
                { _: CapabilityConfig, bounds: Map<WindowArea, AreaBounds<Int>> ->
                    onMain {
                        windowBounds = bounds.entries.firstOrNull { WindowValue.ROW_1_LEFT in it.key.values }?.value
                        log("운전석 창문 범위: $windowBounds")
                        readWindowPosition()
                    }
                },
                { e: Exception -> onMain { log("창문 범위 조회 실패: ${e.message}") } },
            )
        }
        safely("창문 리스너 등록") { vehicle.getWindow().registerWindowPosition(driverWindow, windowListener) }
    }

    private fun readWindowPosition() {
        safely("창문 위치 읽기") {
            vehicle.getWindow().getCurrentWindowPosition(
                driverWindow,
                { position: Int? -> onMain { currentPercent = position?.let(::toPercent); log("운전석 창문 지금 $currentPercent%") } },
                { e: Exception -> onMain { log("창문 위치 읽기 실패: ${e.message}") } },
            )
        }
    }

    private fun startHvac() {
        val hvac = vehicle.getHVAC()
        safely("공조 영역 조회") {
            hvac.checkHvacPowerCapabilities(
                { _: CapabilityConfig, areas: List<SeatArea> ->
                    onMain {
                        // 운전석을 포함하는 영역을 쓴다 (전체 하나로 묶인 차도 있다)
                        areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values }?.let { hvacArea = it }
                        log("공조 영역: ${hvacArea.values.toList()}")
                    }
                },
                { e: Exception -> onMain { log("공조 영역 조회 실패: ${e.message}") } },
            )
        }
        safely("풍량 범위 조회") {
            hvac.checkHvacFanSpeedCapabilities(
                { _: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> -> onMain { fanBounds = boundsFor(bounds); log("풍량 범위: $fanBounds") } },
                { e: Exception -> onMain { log("풍량 범위 조회 실패: ${e.message}") } },
            )
        }
        safely("온도 범위 조회") {
            hvac.checkHvacTemperatureCapabilities(
                { _: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Float>>, _: HvacTemperatureConfigArray? -> onMain { tempBounds = boundsFor(bounds); log("온도 범위: $tempBounds") } },
                { e: Exception -> onMain { log("온도 범위 조회 실패: ${e.message}") } },
            )
        }
    }

    private fun <T> boundsFor(map: Map<SeatArea, AreaBounds<T>>): AreaBounds<T>? =
        map[hvacArea] ?: map.entries.firstOrNull { SeatValue.ROW_1_LEFT in it.key.values }?.value ?: map.values.firstOrNull()

    // ───────────────────────── 창문 ─────────────────────────

    override fun setWindowOpenPercent(percent: Int, onResult: (success: Boolean) -> Unit) {
        val raw = toRaw(percent.coerceIn(0, 100))
        if (!started || raw == null) {
            log("창문 $percent% 실패: ${if (!started) "시작 전" else "창문 범위를 모름"}")
            onResult(false)
            return
        }
        pendingOps++
        val ok = safely("창문 $percent% 명령") {
            vehicle.getWindow().setWindowPosition(
                driverWindow, raw,
                { _: WindowArea, _: Int? -> onMain { finishOp(); onResult(true) } },
                { e: Exception -> onMain { log("창문 $percent% 실패: ${e.message}"); finishOp(); onResult(false) } },
            )
        }
        if (!ok) { finishOp(); onResult(false) }
    }

    private fun toPercent(raw: Int): Int? {
        val b = windowBounds ?: return null
        val span = b.maxValue - b.minValue
        if (span <= 0) return null
        val open = (raw - b.minValue).toFloat() / span
        return ((if (WINDOW_MIN_IS_CLOSED) open else 1f - open) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun toRaw(percent: Int): Int? {
        val b = windowBounds ?: return null
        val open = if (WINDOW_MIN_IS_CLOSED) percent / 100f else 1f - percent / 100f
        return (b.minValue + (b.maxValue - b.minValue) * open).roundToInt()
    }

    // ───────────────────────── 찬바람 ─────────────────────────

    /** 찬바람 켜기 직전 공조 상태. 읽지 못한 항목은 null이고 되돌리지 않는다 */
    private data class HvacSnapshot(
        var power: Boolean? = null,
        var ac: Boolean? = null,
        var recirculation: Boolean? = null,
        var fanSpeed: Int? = null,
        var fanDirection: FanDirectionType? = null,
        var temperatureC: Float? = null,
    )

    override fun setCoolAir(on: Boolean, onResult: (success: Boolean) -> Unit) {
        if (!started) {
            onResult(false)
            return
        }
        if (on) {
            readSnapshot { snapshot ->
                if (savedHvac == null) savedHvac = snapshot   // 이미 켜 둔 상태면 처음 기억한 것을 지킨다
                runSteps(coolAirSteps(), onResult)
            }
        } else {
            val snapshot = savedHvac
            if (snapshot == null) {
                onResult(true)                                // 켠 적이 없으면 되돌릴 것도 없다
                return
            }
            runSteps(restoreSteps(snapshot)) { ok ->
                savedHvac = null
                onResult(ok)
            }
        }
    }

    /** 공조 상태를 차례로 읽는다. 읽기 실패는 무시하고 그 항목만 비워 둔다 */
    private fun readSnapshot(done: (HvacSnapshot) -> Unit) {
        val hvac = vehicle.getHVAC()
        val s = HvacSnapshot()
        val reads: List<Step> = listOf(
            Step("전원 읽기", required = false) { cb -> hvac.getHvacPowerState(hvacArea, { v: Boolean? -> s.power = v; cb(true) }, { cb(false) }) },
            Step("에어컨 읽기", required = false) { cb -> hvac.getHvacAcState(hvacArea, { v: Boolean? -> s.ac = v; cb(true) }, { cb(false) }) },
            Step("내기순환 읽기", required = false) { cb -> hvac.getHvacRecirculationState(hvacArea, { v: Boolean? -> s.recirculation = v; cb(true) }, { cb(false) }) },
            Step("풍량 읽기", required = false) { cb -> hvac.getHvacFanSpeed(hvacArea, { v: Int? -> s.fanSpeed = v; cb(true) }, { cb(false) }) },
            Step("바람 방향 읽기", required = false) { cb -> hvac.getHvacFanDirection(hvacArea, { v: FanDirectionType? -> s.fanDirection = v; cb(true) }, { cb(false) }) },
            Step("온도 읽기", required = false) { cb ->
                hvac.getHvacTemperature(hvacArea, TemperatureUnit.CELSIUS, { v: Float? -> s.temperatureC = v; cb(true) }, { cb(false) })
            },
        )
        runSteps(reads) {
            log("찬바람 켜기 전 공조 상태 기억: $s")
            done(s)
        }
    }

    /** 찬바람: 전원·에어컨 켜기(필수) + 외기 유입·얼굴 방향·센 풍량·낮은 온도(가능하면) */
    private fun coolAirSteps(): List<Step> {
        val hvac = vehicle.getHVAC()
        val steps = mutableListOf(
            Step("공조 전원 켜기", required = true) { cb -> hvac.setHvacPower(hvacArea, true, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) },
            Step("에어컨 켜기", required = true) { cb -> hvac.setHvacAc(hvacArea, true, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) },
            Step("외기 유입", required = false) { cb -> hvac.setHvacRecirculation(hvacArea, false, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) },
            Step("얼굴 방향 바람", required = false) { cb ->
                hvac.setHvacFanDirection(hvacArea, FanDirectionType.FACE, { _: SeatArea, _: FanDirectionType? -> cb(true) }, { cb(false) })
            },
        )
        fanBounds?.let { b ->
            val speed = (b.minValue + (b.maxValue - b.minValue) * COOL_FAN_RATIO).roundToInt()
            steps += Step("풍량 $speed", required = false) { cb -> hvac.setHvacFanSpeed(hvacArea, speed, { _: SeatArea, _: Int? -> cb(true) }, { cb(false) }) }
        }
        tempBounds?.let { b ->
            val temp = b.minValue.coerceAtLeast(COOL_MIN_TEMP_C)
            steps += Step("온도 $temp℃", required = false) { cb ->
                hvac.setHvacTemperature(hvacArea, temp, TemperatureUnit.CELSIUS, { _: SeatArea, _: Float? -> cb(true) }, { cb(false) })
            }
        }
        return steps
    }

    /** 기억해 둔 공조 상태로 되돌린다. 전원은 마지막에 (끄면 다른 설정이 안 먹을 수 있다) */
    private fun restoreSteps(s: HvacSnapshot): List<Step> {
        val hvac = vehicle.getHVAC()
        val steps = mutableListOf<Step>()
        s.temperatureC?.let { v ->
            steps += Step("온도 되돌리기", required = false) { cb ->
                hvac.setHvacTemperature(hvacArea, v, TemperatureUnit.CELSIUS, { _: SeatArea, _: Float? -> cb(true) }, { cb(false) })
            }
        }
        s.fanSpeed?.let { v -> steps += Step("풍량 되돌리기", required = false) { cb -> hvac.setHvacFanSpeed(hvacArea, v, { _: SeatArea, _: Int? -> cb(true) }, { cb(false) }) } }
        s.fanDirection?.let { v ->
            steps += Step("바람 방향 되돌리기", required = false) { cb ->
                hvac.setHvacFanDirection(hvacArea, v, { _: SeatArea, _: FanDirectionType? -> cb(true) }, { cb(false) })
            }
        }
        s.recirculation?.let { v -> steps += Step("내기순환 되돌리기", required = false) { cb -> hvac.setHvacRecirculation(hvacArea, v, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) } }
        s.ac?.let { v -> steps += Step("에어컨 되돌리기", required = true) { cb -> hvac.setHvacAc(hvacArea, v, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) } }
        s.power?.let { v -> steps += Step("공조 전원 되돌리기", required = true) { cb -> hvac.setHvacPower(hvacArea, v, { _: SeatArea, _: Boolean? -> cb(true) }, { cb(false) }) } }
        return steps
    }

    // ───────────────────────── 순서대로 실행 ─────────────────────────

    /** SDK 비동기 명령 한 개. [run]은 결과를 cb(성공 여부)로 정확히 한 번 알린다 (어느 스레드든 상관없다) */
    private class Step(val name: String, val required: Boolean, val run: (cb: (Boolean) -> Unit) -> Unit)

    /** [steps]를 차례로 실행하고, 필수(required) 단계가 모두 성공했는지 [done]으로 알린다 (메인 스레드) */
    private fun runSteps(steps: List<Step>, done: (Boolean) -> Unit) {
        pendingOps++
        fun next(i: Int, allRequiredOk: Boolean) {
            if (i == steps.size) {
                finishOp()
                done(allRequiredOk)
                return
            }
            val step = steps[i]
            var answered = false
            val cb: (Boolean) -> Unit = { ok ->
                onMain {
                    if (answered) return@onMain
                    answered = true
                    if (!ok) log("${step.name} 실패")
                    next(i + 1, allRequiredOk && (ok || !step.required))
                }
            }
            if (!safely(step.name) { step.run(cb) }) cb(false)
        }
        next(0, true)
    }

    private fun finishOp() {
        pendingOps = (pendingOps - 1).coerceAtLeast(0)
        if (pendingOps == 0 && releaseWhenIdle) release()
    }

    // ───────────────────────── 공통 ─────────────────────────

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** SDK가 동기로 예외를 던지는 경우(초기화 전·권한 없음 등)를 잡는다. 성공하면 true */
    private inline fun safely(what: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        log("$what 실패: ${e.message}")
        false
    }

    private companion object {
        /** 창문 원시 값의 최솟값이 닫힘이면 true (실차에서 반대로 움직이면 false로) */
        const val WINDOW_MIN_IS_CLOSED = true

        /** 찬바람 풍량: 범위의 이 비율 (너무 시끄럽지 않게 최대보다 조금 낮게) */
        const val COOL_FAN_RATIO = 0.8f

        /** 찬바람 온도 하한 (℃) */
        const val COOL_MIN_TEMP_C = 18f

        const val RELEASE_TIMEOUT_MS = 5_000L
    }
}
