package com.example.kkaeu_rang.detector

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * 3-4 동작 확인용 임시 화면. 판단 로직 없이 수집된 최신값만 1초마다 보여 준다.
 * 화면/음성 담당의 실제 화면이 붙으면 삭제한다.
 */
class SignalTestActivity : AppCompatActivity() {

    private lateinit var collector: VehicleSignalCollector
    private lateinit var statusText: TextView
    private var started = false

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val denied = granted.filterValues { !it }.keys
            if (denied.isNotEmpty()) Log.e(TAG, "권한 거부: $denied")
            // 테스트용이라 일부 거부돼도 시작한다. 어떤 신호가 안 들어오는지 로그로 확인하기 위함.
            startCollector()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collector = VehicleSignalCollector(this)

        statusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            val pad = (48 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(statusText) })

        permissionLauncher.launch(PERMISSIONS)
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    override fun onDestroy() {
        if (started) collector.stop()
        super.onDestroy()
    }

    private fun startCollector() {
        if (started) return
        try {
            collector.start()
            started = true
            Log.d(TAG, "수집 시작")
        } catch (e: Exception) {
            Log.e(TAG, "수집 시작 실패", e)
        }
    }

    private fun render() {
        statusText.text = buildString {
            appendLine("신호 수집 테스트 (${if (started) "수집 중" else "대기"})")
            appendLine()
            appendLine("차속: %.1f km/h".format(collector.speedKmh))
            appendLine("핸들: %.1f°".format(collector.steeringDeg))
            appendLine("방향지시등: ${onOff(collector.turnSignalOn)}")
            appendLine("브레이크: ${onOff(collector.brakeOn)}")
            appendLine("기어: ${if (collector.isPark) "P" else "P 아님"}")
        }
    }

    private fun onOff(on: Boolean) = if (on) "ON" else "OFF"

    companion object {
        private const val TAG = "Kkaeu.SignalTest"

        private val PERMISSIONS = arrayOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_LIGHTS"
        )
    }
}
