package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.CabinControl

/**
 * 에뮬레이터(LIVE_SIMULATED)·단위 테스트용 가짜 실내 제어. 실제 차량은 건드리지 않고 상태만 들고 있다.
 *
 * - 창문을 움직이면 실제 차처럼 [onWindowChanged]로 바뀐 위치를 알려 준다.
 * - 디버그 패널·테스트에서 [simulateDriverMove]로 "운전자가 직접 창문을 움직임"을,
 *   [raining]으로 비를, [failWindow]·[failCoolAir]로 제어 실패를 흉내 낼 수 있다.
 */
class SimulatedCabinControl(
    initialWindowPercent: Int = 0,
    private val log: (String) -> Unit = {},
) : CabinControl {

    /** 비가 오는 상황을 흉내 낸다 */
    var raining = false

    /** true면 창문 제어가 실패한다 (권한 거부·차량 거부 흉내) */
    var failWindow = false

    /** true면 공조 제어가 실패한다 */
    var failCoolAir = false

    /** 지금 찬바람이 켜져 있는지 (테스트 확인용) */
    var coolAirOn = false
        private set

    private var started = false
    private var windowPercent = initialWindowPercent.coerceIn(0, 100)

    override val windowOpenPercent: Int? get() = if (started) windowPercent else null

    override val isRaining: Boolean get() = raining

    override var onWindowChanged: ((percent: Int) -> Unit)? = null

    override fun start() {
        started = true
        log("가짜 실내 제어 시작 (창문 $windowPercent%)")
    }

    override fun stop() {
        started = false
        log("가짜 실내 제어 종료")
    }

    override fun setWindowOpenPercent(percent: Int, onResult: (success: Boolean) -> Unit) {
        if (!started || failWindow) {
            log("가짜 창문 ${percent.coerceIn(0, 100)}% 실패")
            onResult(false)
            return
        }
        moveWindow(percent.coerceIn(0, 100))
        log("가짜 창문 $windowPercent%")
        onResult(true)
    }

    override fun setCoolAir(on: Boolean, onResult: (success: Boolean) -> Unit) {
        if (!started || failCoolAir) {
            log("가짜 찬바람 ${if (on) "켜기" else "끄기"} 실패")
            onResult(false)
            return
        }
        coolAirOn = on
        log("가짜 찬바람 ${if (on) "켜기" else "끄기(원래대로)"}")
        onResult(true)
    }

    /** 운전자가 직접 창문 스위치로 [percent]%까지 움직인 상황을 흉내 낸다 */
    fun simulateDriverMove(percent: Int) {
        moveWindow(percent.coerceIn(0, 100))
        log("가짜 창문: 운전자가 직접 $windowPercent%로 조작")
    }

    private fun moveWindow(percent: Int) {
        if (percent == windowPercent) return
        windowPercent = percent
        onWindowChanged?.invoke(percent)
    }
}
