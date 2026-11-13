package com.sleepreaper.kkaeurang.reststop

import android.content.Context

object RestStopRepository {

    // 앱 안의 assets/rest_stops.csv 파일 읽기 (실제 앱에서 사용)
    fun loadFromAssets(context: Context, fileName: String = "rest_stops.csv"): List<RestStop> {
        val text = context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCsv(text)
    }

    // CSV 글자 → 쉼터 목록 (PC 테스트에서도 사용)
    fun parseCsv(text: String): List<RestStop> {
        return text.lineSequence()
            .drop(1)                          // 첫 줄(헤더) 건너뛰기
            .map { it.trim() }
            .filter { it.isNotEmpty() }       // 빈 줄 제외
            .mapNotNull { line ->
                val cols = line.split(",")
                if (cols.size < 3) return@mapNotNull null                       // 칸 부족 → 버림
                val lat = cols[1].trim().toDoubleOrNull() ?: return@mapNotNull null  // 숫자 아님 → 버림
                val lon = cols[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                val destLat = cols.getOrNull(3)?.trim()?.toDoubleOrNull()   // 4번째 칸
                val destLon = cols.getOrNull(4)?.trim()?.toDoubleOrNull()   // 5번째 칸
                RestStop(cols[0].trim(), lat, lon, destLat, destLon)
            }
            .toList()
    }
}