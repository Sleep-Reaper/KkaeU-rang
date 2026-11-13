package com.sleepreaper.kkaeurang.integration

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.sleepreaper.kkaeurang.MainActivity
import com.sleepreaper.kkaeurang.voice.VoiceModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 졸음 감시를 화면과 상관없이 계속 돌리는 포그라운드 서비스.
 *
 * 이 서비스가 [VoiceModule]과 [KkaeuLiveSource](판단 엔진 포함)를 만들고 정리한다.
 * 화면은 [KkaeuRuntime.source]로 가져다 보기만 한다. 상단 알림은 [DrowsinessNotifier]가 맡는다.
 *
 * 시작: [start] (권한을 받은 뒤, 화면이 떠 있을 때 부른다)
 * 종료: 알림의 "종료" 버튼 또는 [stop]
 */
class DrowsinessService : Service() {

    private var scope: CoroutineScope? = null
    private var voiceModule: VoiceModule? = null
    private var source: KkaeuLiveSource? = null
    private var notifier: DrowsinessNotifier? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, DrowsinessService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        notifier = DrowsinessNotifier(this, openApp, stop).also { it.createChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService()로 시작됐으면 5초 안에 startForeground()를 불러야 하므로 항상 먼저 부른다
        startInForeground()

        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "알림의 종료 버튼 → 서비스 종료")
            stopSelf()
            return START_NOT_STICKY
        }

        if (source == null) {
            // 이미 떠 있는 서비스에 start가 또 와도(화면 회전 등) 엔진을 다시 만들지 않는다
            createEngine(simulated = intent?.getBooleanExtra(EXTRA_SIMULATED, false) ?: false)
        }
        // 시스템이 죽인 뒤에는 자동 재시작하지 않는다 (모드·권한 정보 없이 엔진을 만들 수 없음)
        return START_NOT_STICKY
    }

    private fun createEngine(simulated: Boolean) {
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val voice = VoiceModule(this).also { it.init() }
        val live = if (simulated) {
            KkaeuLiveSource(
                this, voice, newScope,
                signalSource = SimulatedSignalSource(),
                // 가짜 창문·공조: 점수에 따라 창문 열림·찬바람 화면까지 확인할 수 있게 (Logcat 태그 Kkaeu.Cabin)
                cabin = SimulatedCabinControl(log = { Log.d("Kkaeu.Cabin", it) }),
            )
        } else {
            KkaeuLiveSource(
                this, voice, newScope,
                // 실차 창문·공조 (Pleos Vehicle SDK). 권한이 거부되면 명령이 실패하고 음성으로 대체 안내한다
                cabin = PleosCabinControl(this, log = { Log.d("Kkaeu.Cabin", it) }),
            )
        }
        scope = newScope
        voiceModule = voice
        source = live
        KkaeuRuntime.publish(live)

        // 단계가 바뀔 때마다 상단 알림 갱신 (같은 단계면 notifier가 무시한다)
        newScope.launch {
            live.checker.result.collect { notifier?.update(it.level, it.score) }
        }

        try {
            live.start()
            Log.d(TAG, "졸음 감시 시작 (simulated=$simulated)")
        } catch (e: Exception) {
            Log.e(TAG, "졸음 판단 시작 실패", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "졸음 감시 종료")
        KkaeuRuntime.clear()
        source?.stop()          // 엔진을 voiceModule보다 먼저 정리
        voiceModule?.release()
        scope?.cancel()
        notifier?.cancelAlert()
        source = null
        voiceModule = null
        scope = null
        notifier = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = notifier?.currentStatusNotification() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: 매니페스트의 foregroundServiceType(microphone)과 같은 타입을 지정해야 한다.
            // RECORD_AUDIO가 허용된 뒤, 화면이 떠 있는 동안에만 시작할 수 있다
            startForeground(
                DrowsinessNotifier.STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(DrowsinessNotifier.STATUS_ID, notification)
        }
    }

    companion object {
        private const val TAG = "Kkaeu.Service"
        private const val ACTION_STOP = "com.sleepreaper.kkaeurang.action.STOP_MONITOR"
        private const val EXTRA_SIMULATED = "simulated"

        /** 졸음 감시를 시작한다. [simulated]가 true면 가짜 차량 신호(에뮬레이터용) */
        fun start(context: Context, simulated: Boolean) {
            val intent = Intent(context, DrowsinessService::class.java)
                .putExtra(EXTRA_SIMULATED, simulated)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DrowsinessService::class.java))
        }
    }
}
