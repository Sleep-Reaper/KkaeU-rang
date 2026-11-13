package com.sleepreaper.kkaeurang.reststop

import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    // 두 좌표 사이 거리 (km)
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    // 점1에서 점2를 바라보는 방향 (0~360°, 0=북 90=동)
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // 두 방향의 차이 (0~180°). 예: 350°와 10° → 20°
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    // 목표가 진행 방향 기준 오른쪽에 있는지 (외적 부호로 판별)
    fun isOnRightSide(
        myLat: Double, myLon: Double, headingDeg: Double,
        targetLat: Double, targetLon: Double
    ): Boolean {
        val dx = (targetLon - myLon) * cos(Math.toRadians(myLat))  // 동쪽 성분
        val dy = (targetLat - myLat)                               // 북쪽 성분
        val hx = sin(Math.toRadians(headingDeg))                   // 진행 방향 x
        val hy = cos(Math.toRadians(headingDeg))                   // 진행 방향 y
        return hx * dy - hy * dx < 0                               // 외적 z < 0 → 오른쪽
    }
}