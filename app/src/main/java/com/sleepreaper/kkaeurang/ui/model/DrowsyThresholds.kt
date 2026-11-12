package com.sleepreaper.kkaeurang.ui.model

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

    /** 연속 주행 시간이 이 값을 넘으면 ReasonBlock에서 강조한다 (분) */
    const val LONG_DRIVE_MIN = 120

    fun levelOf(score: Float): DrowsyLevel = when {
        score >= CRITICAL_MIN -> DrowsyLevel.CRITICAL
        score >= WARNING_MIN -> DrowsyLevel.WARNING
        score >= CAUTION_MIN -> DrowsyLevel.CAUTION
        else -> DrowsyLevel.NORMAL
    }
}
