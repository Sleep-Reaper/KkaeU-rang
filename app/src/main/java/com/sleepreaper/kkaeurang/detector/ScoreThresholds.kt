package com.sleepreaper.kkaeurang.detector

/**
 * 안전 점수 기준을 한곳에 모은다. 엔진·창문·알림이 모두 이 값을 쓴다.
 *
 * ## 점수 방식
 * **안전 점수 100에서 시작해서 위험한 사건이 있으면 감점**한다. 시간이 지나면 사건 감점은 조금씩 회복된다.
 * 화면의 안전 점수와 같은 값이다. (높을수록 안전, 0~100)
 *
 * ## 단계 (점수가 이 값 이하이면 해당 단계)
 * | 안전 점수 | 단계 |
 * | --- | --- |
 * | 61~100 | NORMAL |
 * | 31~60 | CAUTION ([CAUTION_MAX] 이하) |
 * | 21~30 | WARNING ([WARNING_MAX] 이하) |
 * | 0~20 | DANGER ([DANGER_MAX] 이하) |
 *
 * 단계가 **나빠질 때**는 기준에서 바로 바뀌고, **좋아질 때**는 기준보다 [HYSTERESIS]점 더 올라야 바뀐다
 * (기준 근처에서 알람이 켜졌다 꺼졌다 하는 것을 막는다).
 */
object ScoreThresholds {
    /** 감점이 없는 점수 (주행 시작 점수) */
    const val FULL = 100

    const val CAUTION_MAX = 60
    const val WARNING_MAX = 30
    const val DANGER_MAX = 20

    /** 단계가 좋아질 때 기준보다 더 올라야 하는 점수 */
    const val HYSTERESIS = 2

    /** 점수만으로 정한 단계 (여유 없음) */
    fun levelOf(safety: Int): Level = when {
        safety <= DANGER_MAX -> Level.DANGER
        safety <= WARNING_MAX -> Level.WARNING
        safety <= CAUTION_MAX -> Level.CAUTION
        else -> Level.NORMAL
    }

    /**
     * 직전 단계 [previous]를 고려한 단계. 나빠질 때는 [levelOf]와 같고,
     * 좋아질 때는 기준보다 [HYSTERESIS]점 더 올라야 내려간다.
     */
    fun levelFor(safety: Int, previous: Level): Level {
        val byScore = levelOf(safety)
        if (byScore >= previous) return byScore
        val holdMax = when (previous) {
            Level.DANGER -> DANGER_MAX
            Level.WARNING -> WARNING_MAX
            Level.CAUTION -> CAUTION_MAX
            Level.NORMAL -> return byScore
        } + HYSTERESIS
        return if (safety <= holdMax) previous else byScore
    }
}
