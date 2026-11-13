package com.sleepreaper.kkaeurang.reststop

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

class RestStopHelper(context: Context) {

    private val TAG = "RestStop"
    private val appContext = context.applicationContext
    private val naviHelper = NaviHelper(appContext)
    private val finder by lazy { RestStopFinder(RestStopRepository.loadFromAssets(appContext)) }
    private val handler = Handler(Looper.getMainLooper())   // 결과를 메인 스레드에서 전달

    private var callback: ((Result<FoundRestStop>) -> Unit)? = null
    private var step = 0              // 0: 대기, 1: 첫 위치 기다림, 2: 두 번째 위치 기다림
    private var firstLat = 0.0
    private var firstLon = 0.0

    private val timeout = Runnable { finish(Result.failure(IllegalStateException("내비 응답 없음"))) }

    // 내비에서 오는 응답 받기
    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            handler.post { onLocation(currentLocationInfo.latitude, currentLocationInfo.longitude) }
        }
        override fun onError(errorCode: NaviErrorCode) {
            handler.post { finish(Result.failure(IllegalStateException("내비 에러: $errorCode"))) }
        }
    }

    // MainActivity onCreate에서 1회
    fun start() {
        naviHelper.initialize()
        naviHelper.addListener(listener)
    }

    // MainActivity onDestroy에서 1회
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        naviHelper.removeListener(listener)
        naviHelper.release()
    }

    // ★ 팀원이 호출하는 함수
    fun addNearestRestStop(onResult: (Result<FoundRestStop>) -> Unit) {
        if (callback != null) {
            onResult(Result.failure(IllegalStateException("이미 처리 중")))
            return
        }
        callback = onResult
        step = 1
        handler.postDelayed(timeout, 10_000)          // 10초 제한
        naviHelper.getCurrentLocationInfo()           // ① 첫 위치 요청
    }

    private fun onLocation(lat: Double, lon: Double) {
        when (step) {
            1 -> {                                    // 첫 위치 도착
                firstLat = lat
                firstLon = lon
                step = 2
                handler.postDelayed({ naviHelper.getCurrentLocationInfo() }, 2_000)  // ② 2초 뒤 재요청
            }
            2 -> {                                    // 두 번째 위치 도착
                step = 0
                val moved = GeoUtils.distanceKm(firstLat, firstLon, lat, lon)
                val heading = if (moved > 0.01) GeoUtils.bearingDeg(firstLat, firstLon, lat, lon) else null  // ③ 10m 이상 움직였을 때만
                addWaypoint(lat, lon, heading)
            }
        }
    }

    private fun addWaypoint(lat: Double, lon: Double, heading: Double?) {
        val found = finder.findNearest(lat, lon, heading)   // ④
        if (found == null) {
            finish(Result.failure(NoSuchElementException("조건에 맞는 쉼터 없음")))
            return
        }
        naviHelper.addWaypoint(                             // ⑤
            RequestWaypointInfo(
                longitude = found.stop.lon,
                latitude = found.stop.lat,
                poiName = found.stop.name,
                waypointIndex = WaypointIndex.FIRST,        // 첫 번째 경유지 = 우선 안내
                poiId = "",
                address = "",
                poiSubId = ""
            )
        )
        Log.d(TAG, "경유지 추가 요청: ${found.stop.name} (${"%.1f".format(found.distanceKm)}km)")
        finish(Result.success(found))                       // ⑥
    }

    private fun finish(result: Result<FoundRestStop>) {
        handler.removeCallbacks(timeout)
        step = 0
        val cb = callback ?: return
        callback = null
        cb(result)
    }
}