package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.DrowsinessRules
import com.sleepreaper.kkaeurang.detector.Sample
import com.sleepreaper.kkaeurang.detector.SignalSource
import com.sleepreaper.kkaeurang.ui.debug.DriveScenario

/**
 * 실차가 아닌 환경(에뮬레이터)용 가짜 차량 신호. 판단 엔진은 이 값을 실제 신호처럼 받아 규칙 ①~④를 그대로 돌린다.
 *
 * - 평소: D단 약 80km/h, 핸들이 ±3° 안에서 조금씩 움직임 (어떤 규칙에도 걸리지 않는 정상 주행)
 * - [run]으로 시나리오를 넣으면 1초(엔진 샘플 1장)마다 한 프레임씩 재생한다
 * - 모든 호출은 메인 스레드 (엔진 틱과 디버그 패널 버튼)
 */
class SimulatedSignalSource : SignalSource {

    private data class Frame(
        val speedKmh: Float,
        val steeringDeg: Float,
        val turnSignalOn: Boolean = false,
        val brakeOn: Boolean = false,
    )

    private val script = ArrayDeque<Frame>()
    private var tick = 0

    var isPark = false
        private set
    var turnSignalOn = false
    var brakeOn = false

    /** 지금 재생 중인 시나리오 (없으면 null) */
    var runningScenario: DriveScenario? = null
        private set

    override fun start() = Unit
    override fun stop() = Unit

    override fun sample(now: Long): Sample {
        tick++
        if (isPark) return Sample(now, 0f, 0f, turnSignalOn, brakeOn, isPark = true)
        val f = script.removeFirstOrNull() ?: cruise()
        if (script.isEmpty()) runningScenario = null
        return Sample(now, f.speedKmh, f.steeringDeg, f.turnSignalOn || turnSignalOn, f.brakeOn || brakeOn, isPark = false)
    }

    fun setParked(parked: Boolean) {
        isPark = parked
        if (parked) clearScenario()
    }

    fun clearScenario() {
        script.clear()
        runningScenario = null
    }

    /** 시나리오를 처음부터 재생한다 (재생 중이던 것은 버림). P단이면 먼저 D단으로 바꾼다 */
    fun run(scenario: DriveScenario) {
        isPark = false
        script.clear()
        script.addAll(framesOf(scenario))
        runningScenario = scenario
    }

    private fun cruise(t: Int = tick) = Frame(
        speedKmh = CRUISE_KMH + CRUISE_SPEED_WOBBLE[t % CRUISE_SPEED_WOBBLE.size],
        steeringDeg = CRUISE_STEER_WOBBLE[t % CRUISE_STEER_WOBBLE.size],
    )

    private fun framesOf(s: DriveScenario): List<Frame> {
        val frozen = Frame(CRUISE_KMH, FROZEN_DEG)
        return when (s) {
            // ① 고속에서 10초 넘게 핸들 고정 → +15
            DriveScenario.STEERING_FROZEN -> List(DrowsinessRules.FREEZE_SECONDS + 2) { frozen }

            // ① 고정 10초(+15) 직후 1초 사이 크게 꺾음 ② → +25 (합 +40)
            DriveScenario.FROZEN_THEN_JERK ->
                List(DrowsinessRules.FREEZE_SECONDS + 1) { frozen } +
                    Frame(CRUISE_KMH, FROZEN_DEG + DrowsinessRules.CORRECTION_DEG + 5f) +
                    Frame(CRUISE_KMH, 4f)

            // ③ 깜빡이 없이 한쪽으로 3초 넘게 쏠림 → +15
            DriveScenario.DRIFT -> List(DrowsinessRules.DRIFT_SECONDS + 1) {
                Frame(CRUISE_KMH, DrowsinessRules.DRIFT_DEG + 2f)
            }

            // ④ 브레이크 없이 1분 동안 속도가 크게 오르내림 → +10 (표준편차가 기준을 넘는 순간, 보통 20~30초)
            DriveScenario.UNSTABLE_SPEED -> List(DrowsinessRules.SPEED_WINDOW + 2) { i ->
                Frame(
                    speedKmh = if (i % 2 == 0) CRUISE_KMH - UNSTABLE_SWING else CRUISE_KMH + UNSTABLE_SWING,
                    steeringDeg = CRUISE_STEER_WOBBLE[i % CRUISE_STEER_WOBBLE.size],
                )
            }
        }
    }

    private companion object {
        const val CRUISE_KMH = 80f
        const val FROZEN_DEG = 0.5f
        const val UNSTABLE_SWING = 15f

        // 10초 범위가 2°를 넘고(① 아님), 8°를 넘지 않으며(③ 아님), 속도 편차가 작은(④ 아님) 평소 주행
        val CRUISE_STEER_WOBBLE = floatArrayOf(-3f, 0f, 3f, 0f)
        val CRUISE_SPEED_WOBBLE = floatArrayOf(0f, 1f, -1f, 0.5f)
    }
}
