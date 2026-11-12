package com.sleepreaper.kkaeurang.integration

import android.util.Log
import com.sleepreaper.kkaeurang.detector.WindowModule

/**
 * 에뮬레이터(LIVE_SIMULATED)용 가짜 창문. 실제 차량 제어는 하지 않고 로그만 남기지만,
 * NoOpWindowModule과 달리 "열었다"고 보고 화면(위험 단계의 창문 열림 표시)까지 이어지게 한다.
 * 실차(LIVE)에서는 쓰지 않는다.
 */
class SimulatedWindowModule : WindowModule {
    override fun open() {
        Log.d(TAG, "가짜 창문 열기 (에뮬레이터: 화면 표시만)")
    }

    private companion object {
        const val TAG = "Kkaeu.SimWindow"
    }
}
