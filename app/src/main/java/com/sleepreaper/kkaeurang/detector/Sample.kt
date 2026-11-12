package com.sleepreaper.kkaeurang.detector

/** 1초마다 찍는 차량 상태 한 장 */
data class Sample(
    val time: Long,            // ms
    val speedKmh: Float,
    val steeringDeg: Float,
    val turnSignalOn: Boolean,
    val brakeOn: Boolean,
    val isPark: Boolean
)
