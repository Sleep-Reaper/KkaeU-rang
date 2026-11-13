package com.sleepreaper.kkaeurang.integration

import android.util.Log
import com.sleepreaper.kkaeurang.detector.NaviModule
import com.sleepreaper.kkaeurang.reststop.RestStopHelper

/**
 * 엔진의 졸음쉼터 안내([NaviModule])를 [RestStopHelper]에 연결한다.
 * 가장 가까운 졸음쉼터를 찾아 내비에 경유지로 추가하고, 성공·실패를 엔진에 알린다.
 * 실패하면 엔진이 "졸음쉼터 안내에 실패했어요" 음성 대체 안내를 한다.
 *
 * [RestStopHelper]는 서비스가 만들고 [RestStopHelper.start]를 부른 뒤 넘겨야 하며,
 * 결과 콜백은 메인 스레드에서 정확히 한 번 온다.
 */
class RestStopNaviModule(private val helper: RestStopHelper) : NaviModule {

    override fun guideToRestArea() = guideToRestArea { }

    override fun guideToRestArea(onResult: (Boolean) -> Unit) {
        helper.addNearestRestStop { result ->
            result
                .onSuccess { Log.d(TAG, "경유지 추가 요청 성공: ${it.stop.name} (${"%.1f".format(it.distanceKm)}km)") }
                .onFailure { Log.w(TAG, "경유지 추가 실패: ${it.message}") }
            onResult(result.isSuccess)
        }
    }

    private companion object {
        const val TAG = "Kkaeu.Navi"
    }
}
