package com.sleepreaper.kkaeurang.reststop

/** 휴게소/졸음쉼터 한 곳의 정보 (C의 struct 같은 역할) */
data class RestStop(
    val name: String,              // 이름
    val lat: Double,               // 위도
    val lon: Double,               // 경도
    val destLat: Double? = null,   // 방향 도시 위도 (예: 부산방향 → 부산)
    val destLon: Double? = null    // 방향 도시 경도
)

/** 찾기 결과: 어떤 쉼터가, 몇 km 떨어져 있는지 */
data class FoundRestStop(
    val stop: RestStop,
    val distanceKm: Double
)