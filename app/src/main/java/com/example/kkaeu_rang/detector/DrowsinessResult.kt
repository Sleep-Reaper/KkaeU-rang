package com.example.kkaeu_rang.detector

enum class Level { NORMAL, CAUTION, WARNING }

data class DrowsinessResult(
    val score: Int = 0,                    // 0~100
    val level: Level = Level.NORMAL,
    val reasons: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
