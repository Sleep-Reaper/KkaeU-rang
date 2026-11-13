package com.sleepreaper.kkaeurang.ui.state

import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel

/**
 * 경보 화면(주의·경고·위험)을 운전자가 닫은 상태.
 *
 * 운전자가 "괜찮다"고 대답하거나 화면 버튼을 눌러 졸리지 않다고 알리면 [dismiss]를 부른다.
 * 그 뒤로는 같은 단계 이하에서 경보 화면 대신 홈 계기판을 보여 주고, 아래 경우에 다시 경보 화면을 띄운다.
 * - 단계가 닫을 때보다 더 올라감
 * - 졸음 점수가 닫은 뒤의 가장 낮은 점수보다 [REALERT_RISE]점 이상 오름 (같은 단계에서 새 졸음 신호가 들어옴)
 * - 엔진이 새로 질문을 시작함 ([clear])
 * 안전 단계로 내려오면 닫힌 상태는 풀린다.
 *
 * 판단 엔진의 점수·단계는 건드리지 않는다. 화면에 무엇을 띄울지만 정한다. 메인 스레드에서만 쓴다.
 */
class AlertDismissal {
    private var dismissedLevel: DrowsyLevel? = null
    private var floorScore = 0f

    val isDismissed: Boolean get() = dismissedLevel != null

    /** 운전자가 졸리지 않다고 알림. 안전 단계에서는 닫을 화면이 없으므로 무시한다 */
    fun dismiss(level: DrowsyLevel, score: Float) {
        if (level == DrowsyLevel.NORMAL) return
        dismissedLevel = maxOf(level, dismissedLevel ?: level)
        floorScore = score
    }

    fun clear() {
        dismissedLevel = null
    }

    /** 매 틱마다 부른다. 아직 닫힌 상태면 true */
    fun update(level: DrowsyLevel, score: Float): Boolean {
        val dismissed = dismissedLevel ?: return false
        if (level == DrowsyLevel.NORMAL || level > dismissed || score >= floorScore + REALERT_RISE) {
            dismissedLevel = null
            return false
        }
        floorScore = minOf(floorScore, score)
        return true
    }

    companion object {
        /** 닫은 뒤 졸음 점수가 이만큼 오르면 다시 경보 화면 (가장 작은 졸음 이벤트가 +10점) */
        const val REALERT_RISE = 10f
    }
}
