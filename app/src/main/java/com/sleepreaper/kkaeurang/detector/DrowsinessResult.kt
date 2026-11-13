package com.sleepreaper.kkaeurang.detector

/**
 * 졸음 위험 단계. 선언 순서(ordinal)로 크기 비교하므로 순서를 바꾸지 말 것.
 * NORMAL(안전 점수 61~100) < CAUTION(31~60) < WARNING(21~30) < DANGER(0~20). 기준은 [ScoreThresholds]
 */
enum class Level { NORMAL, CAUTION, WARNING, DANGER }

data class DrowsinessResult(
    /** 안전 점수 0~100. 100에서 시작해 사건이 있으면 감점한다 (높을수록 안전, 화면의 안전 점수와 같은 값) */
    val score: Int = ScoreThresholds.FULL,
    val level: Level = Level.NORMAL,
    val reasons: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/** UI에 보여 주는 사건 로그 한 줄. [time]은 발생 시각(ms). */
data class LogEntry(
    val time: Long,
    val message: String
)
