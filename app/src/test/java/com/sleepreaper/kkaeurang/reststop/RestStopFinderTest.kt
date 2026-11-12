package com.sleepreaper.kkaeurang.reststop

import org.junit.Assert.*
import org.junit.Test

class RestStopFinderTest {

    @Test
    fun 거리계산_서울_부산_약325km() {
        // 서울시청 ~ 부산시청 직선거리는 약 325km
        val d = GeoUtils.distanceKm(37.5663, 126.9779, 35.1798, 129.0750)
        assertEquals(325.0, d, 5.0)   // 325 ± 5 안이면 통과
    }

    @Test
    fun 반대방향_쉼터는_제외되고_내방향_쉼터가_선택됨() {
        // 차가 북쪽(0°)으로 달리는 상황
        val stops = listOf(
            RestStop("반대방향 쉼터", 37.010, 127.0, destLat = 35.0, destLon = 127.0), // 남쪽 도시 방향 → 제외
            RestStop("내방향 쉼터", 37.012, 127.0, destLat = 38.0, destLon = 127.0),   // 북쪽 도시 방향 → 선택
            RestStop("지나친 쉼터", 36.995, 127.0, destLat = 38.0, destLon = 127.0)    // 뒤쪽 → 제외
        )
        val result = RestStopFinder(stops).findNearest(37.0, 127.0, headingDeg = 0.0)
        assertEquals("내방향 쉼터", result?.stop?.name)
    }

    @Test
    fun 방향을_모르면_최단거리() {
        val stops = listOf(
            RestStop("가까운곳", 37.001, 127.0),
            RestStop("먼곳", 37.1, 127.0)
        )
        val result = RestStopFinder(stops).findNearest(37.0, 127.0, headingDeg = null)
        assertEquals("가까운곳", result?.stop?.name)
    }

    @Test
    fun CSV_파싱() {
        val csv = """
            name,lat,lon
            A휴게소,37.1,127.1
            잘못된줄
            B쉼터,36.5,127.5
        """.trimIndent()
        val list = RestStopRepository.parseCsv(csv)
        assertEquals(2, list.size)          // 잘못된 줄은 버려져서 2개
        assertEquals("B쉼터", list[1].name)
    }
    @Test
    fun 실제데이터_253곳_읽기() {
        val text = java.io.File("src/main/assets/rest_stops.csv").readText()
        val list = RestStopRepository.parseCsv(text)
        assertEquals(253, list.size)
    }
    @Test
    fun 실제데이터_오산_방향별_선택() {
        val text = java.io.File("src/main/assets/rest_stops.csv").readText()
        val finder = RestStopFinder(RestStopRepository.parseCsv(text))

        // 오산 북쪽에서 남쪽(190°)으로 달릴 때 → 부산방향
        val south = finder.findNearest(37.20, 127.10, headingDeg = 190.0)
        assertEquals("오산졸음쉼터(경부선 부산방향)", south?.stop?.name)

        // 오산 남쪽에서 북쪽(10°)으로 달릴 때 → 서울방향
        val north = finder.findNearest(37.10, 127.08, headingDeg = 10.0)
        assertEquals("오산졸음쉼터(경부선 서울방향)", north?.stop?.name)
    }
}
