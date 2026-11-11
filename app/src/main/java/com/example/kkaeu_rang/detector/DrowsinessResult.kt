package com.example.kkaeu_rang.detector

/**
 * 졸음 위험 단계. 선언 순서(ordinal)로 크기 비교하므로 순서를 바꾸지 말 것.
 * NORMAL(0~39) < CAUTION(40~69) < WARNING(70~79) < DANGER(80~)
 */
enum class Level { NORMAL, CAUTION, WARNING, DANGER }

data class DrowsinessResult(
    val score: Int = 0,                    // 0~100
    val level: Level = Level.NORMAL,
    val reasons: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
