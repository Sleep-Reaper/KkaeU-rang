package com.sleepreaper.kkaeurang.ui.model

import kotlin.math.roundToInt

/**
 * 점수 → 단계 경계값. 아직 확정 전이라 한곳에 모아 둔다.
 * NORMAL 0~39 / CAUTION 40~69 / WARNING 70~79 / CRITICAL 80 이상
 *
 * TODO(integration): 판단 엔진 팀과 경계값을 확정하면 여기 값을 맞춘다.
 *  (실제 단계는 판단 엔진이 DrowsinessUiState.level로 내려주고, UI는 게이지 구간 표시에만 이 값을 쓴다)
 */
object DrowsyThresholds {
    const val CAUTION_MIN = 40f
    const val WARNING_MIN = 70f
    const val CRITICAL_MIN = 80f

    /** 연속 주행 시간이 이 값을 넘으면 헤더에서 노란색으로 강조한다 (분) */
    const val LONG_DRIVE_MIN = 120

    /** 화면의 안전 점수 = 100 − 졸음 점수 (0~100, 반올림) */
    fun safetyOf(drowsyScore: Float): Int = (100f - drowsyScore).coerceIn(0f, 100f).roundToInt()

    /**
     * 안전 점수 계기판의 단계 경계 (왼쪽 → 오른쪽: 위험 · 경고 · 주의 · 안전).
     * 졸음 점수 경계를 뒤집은 값이다: 0 · 20 · 30 · 60 · 100
     */
    val SAFETY_STOPS: List<Float> = listOf(0f, 100f - CRITICAL_MIN, 100f - WARNING_MIN, 100f - CAUTION_MIN, 100f)

    fun levelOf(score: Float): DrowsyLevel = when {
        score >= CRITICAL_MIN -> DrowsyLevel.CRITICAL
        score >= WARNING_MIN -> DrowsyLevel.WARNING
        score >= CAUTION_MIN -> DrowsyLevel.CAUTION
        else -> DrowsyLevel.NORMAL
    }
}
