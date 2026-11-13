package com.sleepreaper.kkaeurang.reststop

class RestStopFinder(
    private val stops: List<RestStop>,          // 전체 쉼터 목록
    private val aheadAngleDeg: Double = 60.0,   // 진행 방향 ±60° 안이면 "앞쪽"
    private val maxDistanceKm: Double = 50.0    // 50km 넘으면 제외
) {

    // headingDeg: 진행 방향. 모르면 null → 방향 무시하고 최단거리
    fun findNearest(myLat: Double, myLon: Double, headingDeg: Double?): FoundRestStop? {
        return stops
            .map { FoundRestStop(it, GeoUtils.distanceKm(myLat, myLon, it.lat, it.lon)) }  // 1
            .filter { it.distanceKm <= maxDistanceKm }                                      // 2
            .filter { headingDeg == null || isInMyDirection(myLat, myLon, headingDeg, it.stop) } // 3
            .minByOrNull { it.distanceKm }                                                  // 4
    }

    // 앞쪽이면서 오른쪽에 있는지
    // 앞쪽이면서, 그 쉼터의 방향 도시 쪽으로 내가 가고 있는지
    private fun isInMyDirection(myLat: Double, myLon: Double, headingDeg: Double, s: RestStop): Boolean {
        // 1. 쉼터가 앞쪽에 있나 (진행 방향 ±60°)
        val toStop = GeoUtils.bearingDeg(myLat, myLon, s.lat, s.lon)
        val ahead = GeoUtils.angleDiffDeg(toStop, headingDeg) <= aheadAngleDeg
        if (!ahead) return false

        // 2. 방향 도시 정보가 없으면 앞쪽인지만 보고 통과
        if (s.destLat == null || s.destLon == null) return true

        // 3. 그 쉼터의 방향 도시가 내 진행 방향 쪽인가 (±90°)
        val toDest = GeoUtils.bearingDeg(myLat, myLon, s.destLat, s.destLon)
        return GeoUtils.angleDiffDeg(toDest, headingDeg) <= 90.0
    }
}