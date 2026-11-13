package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.LogEntry
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.TripLogEntry
import com.sleepreaper.kkaeurang.ui.model.TripLogKind

/**
 * 판단 엔진 사건 로그 한 줄 → 주행 리포트 "상세 로그" 한 줄.
 * 엔진 문장 형식(DrowsinessChecker.note)에 맞춰 읽는다. 엔진 문구가 바뀌면 여기도 맞춘다.
 * 엔진 점수는 이미 안전 점수(100에서 감점)라서 변환 없이 그대로 쓴다. 사건 로그의 "-25점"이 곧 안전 점수 변화량이다.
 */
object TripLogMapper {

    fun fromEngine(entry: LogEntry): TripLogEntry {
        val msg = entry.message
        LEVEL_CHANGE.matchEntire(msg)?.let { m ->
            val from = levelOf(m.groupValues[1])
            val to = levelOf(m.groupValues[2])
            val safety = m.groupValues[3].toInt()   // 엔진 점수 = 안전 점수
            val kind = if (from != null && to != null && to < from) TripLogKind.LEVEL_DOWN else TripLogKind.LEVEL_UP
            return TripLogEntry(
                entry.time, kind,
                "${uiName(m.groupValues[1])} → ${uiName(m.groupValues[2])} 단계 (안전 점수 ${safety}점)",
                level = to,
            )
        }
        SCORE_EVENT.matchEntire(msg)?.let { m ->
            // 엔진이 안전 점수 변화량으로 적는다 (감점 -25, 디버그 회복 +10 등)
            return TripLogEntry(entry.time, TripLogKind.EVENT, m.groupValues[1], safetyDelta = m.groupValues[2].toInt())
        }
        val kind = when {
            msg.startsWith("운전자에게 질문") -> TripLogKind.QUESTION
            msg.startsWith("운전자 응답") || msg.startsWith("무응답") -> TripLogKind.ANSWER
            msg.startsWith("알람") -> TripLogKind.ALARM
            msg.startsWith("창문") || msg.startsWith("졸음쉼터") || msg.contains("찬바람") -> TripLogKind.VEHICLE
            msg.startsWith("정차") -> TripLogKind.REST
            msg.startsWith("출발 전 컨디션") -> TripLogKind.CONDITION
            else -> TripLogKind.OTHER
        }
        return TripLogEntry(entry.time, kind, msg)
    }

    private fun levelOf(name: String): DrowsyLevel? = when (name) {
        "정상" -> DrowsyLevel.NORMAL
        "주의" -> DrowsyLevel.CAUTION
        "경고" -> DrowsyLevel.WARNING
        "위험" -> DrowsyLevel.CRITICAL
        else -> null
    }

    /** 엔진은 안전 단계를 "정상"이라 부르고 화면은 "안전"이라 부른다 */
    private fun uiName(name: String) = if (name == "정상") "안전" else name

    private val LEVEL_CHANGE = Regex("""^단계 변경: (\S+) → (\S+) \(점수 (\d+)\)$""")
    private val SCORE_EVENT = Regex("""^(.+) \(([+-]\d+)점\)$""")
}
