package com.sleepreaper.kkaeurang.detector

import kotlin.math.abs

/**
 * [WindowController]의 판단을 [CabinControl]로 실행한다. 판단 결과가 바뀔 때만 차량에 명령하고, 아래 두 가지를 지킨다.
 *
 * ## 원래 상태 복원 — 우리가 연 만큼만 되돌린다
 * - 처음 열기 직전의 창문 위치를 기억해 두고, 닫을 때는 0%가 아니라 **그 위치로** 되돌린다.
 * - 운전자가 원래 더 열어 두었으면 그보다 닫지 않는다 (목표 = max(판단 값, 원래 위치)).
 * - 찬바람은 [CabinControl.setCoolAir]가 켜기 직전 공조 상태를 기억했다가 끌 때 되돌린다.
 *
 * ## 운전자 우선
 * - 우리가 창문을 움직인 뒤, 운전자가 창문 스위치로 직접 움직이면 **이번 주행(P단이 될 때까지)** 동안 창문은 손대지 않는다.
 *   창문은 운전자가 둔 자리 그대로 두고, 찬바람 판단은 계속한다.
 * - 우리가 움직이는 중(명령 후 [MOVE_TIMEOUT_MS] 안)에 들어오는 위치 변화는 우리 것으로 본다.
 *   창문은 천천히 움직여서 중간 위치가 여러 번 들어오기 때문이다.
 *
 * 모든 호출은 메인 스레드에서 일어난다.
 */
class CabinActuator(
    private val cabin: CabinControl,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {

    enum class Event {
        WINDOW_FAILED,      // 창문 명령 실패 (권한 거부·차량 거부 등)
        COOL_AIR_FAILED,    // 찬바람 명령 실패
        DRIVER_OVERRIDE,    // 운전자가 직접 창문을 움직여 자동 조절을 멈춤
    }

    /** 실패·운전자 조작 같은 사건 알림 (엔진 로그·음성 대체 안내용) */
    var onEvent: ((Event) -> Unit)? = null

    /** 운전자가 직접 창문을 움직여 이번 주행 동안 창문 자동 조절을 멈춘 상태 */
    var driverOverride = false
        private set

    private var originalPercent: Int? = null   // 처음 열기 직전 창문 위치. null이면 우리가 연 적 없음
    private var commandedPercent: Int? = null  // 마지막으로 보낸 창문 목표. null이면 우리가 움직인 적 없음
    private var movingUntil = 0L               // 이 시각까지 들어오는 위치 변화는 우리가 움직인 것으로 본다
    private var coolAirRequested = false

    init {
        cabin.onWindowChanged = ::onWindowChanged
    }

    /** 매초 [WindowController.update] 결과와 함께 부른다 */
    fun apply(decision: WindowController.Decision, isPark: Boolean) {
        applyWindow(decision.windowPercent)
        applyCoolAir(decision.coolAir)
        if (isPark && driverOverride) {
            driverOverride = false
            log("정차(P단) → 창문 자동 조절 다시 사용")
        }
    }

    private fun applyWindow(wanted: Int) {
        if (driverOverride) return

        val target: Int? = if (wanted > 0) {
            val original = originalPercent ?: (cabin.windowOpenPercent ?: 0).also {
                originalPercent = it
                log("창문 원래 위치 기억: $it%")
            }
            maxOf(wanted, original)
        } else {
            when {
                originalPercent == null -> null
                commandedPercent == null -> {      // 기억만 하고 움직인 적은 없음 (예: 바로 비가 옴)
                    originalPercent = null
                    null
                }
                else -> originalPercent            // 원래 위치로 되돌리기
            }
        }
        if (target == null || target == commandedPercent) return

        val restoring = wanted == 0
        commandedPercent = target
        movingUntil = clock() + MOVE_TIMEOUT_MS
        log(if (restoring) "창문 원래대로($target%) 되돌리기" else "창문 $target% 열기")
        cabin.setWindowOpenPercent(target) { ok ->
            if (!ok) {
                log("창문 $target% 실패")
                movingUntil = 0L
                onEvent?.invoke(Event.WINDOW_FAILED)
                // 같은 목표로는 다시 시도하지 않는다 (매초 실패 반복 방지). 목표가 바뀌면 다시 보낸다.
                return@setWindowOpenPercent
            }
            if (restoring && commandedPercent == target) {
                originalPercent = null
                commandedPercent = null
            }
        }
    }

    private fun applyCoolAir(wanted: Boolean) {
        if (wanted == coolAirRequested) return
        coolAirRequested = wanted
        log(if (wanted) "찬바람 켜기" else "찬바람 끄기(원래대로)")
        cabin.setCoolAir(wanted) { ok ->
            if (!ok) {
                log("찬바람 ${if (wanted) "켜기" else "끄기"} 실패")
                onEvent?.invoke(Event.COOL_AIR_FAILED)
            }
        }
    }

    private fun onWindowChanged(percent: Int) {
        if (driverOverride) return
        val commanded = commandedPercent ?: return   // 우리가 움직인 적 없으면 운전자 조작은 신경 쓰지 않는다

        if (clock() < movingUntil) {
            if (abs(percent - commanded) <= TOLERANCE_PERCENT) movingUntil = 0L   // 도착
            return
        }
        if (abs(percent - commanded) > TOLERANCE_PERCENT) {
            driverOverride = true
            originalPercent = null
            commandedPercent = null
            log("운전자가 창문을 직접 조작($percent%) → 이번 주행 동안 창문 자동 조절 중지")
            onEvent?.invoke(Event.DRIVER_OVERRIDE)
        }
    }

    companion object {
        /** 명령 후 이 시간 안에 들어오는 위치 변화는 우리가 움직인 것으로 본다 */
        const val MOVE_TIMEOUT_MS = 10_000L

        /** 이 정도 차이는 같은 위치로 본다 (센서 오차) */
        const val TOLERANCE_PERCENT = 5
    }
}
