package com.sleepreaper.kkaeurang.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * Pleos SpeechToText SDK 래퍼.
 *
 * ON_DEVICE 모드는 차량 내장 엔진을 쓰므로 앱 등록(registerApp) 없이 동작한다.
 * 마이크는 Pleos 음성 서비스가 직접 열기 때문에 RECORD_AUDIO 권한은 필요 없다 (STT_SERVICE만 필요).
 *
 * 사용 순서: init() → (준비 완료 후) listen() / stop() → release()
 *
 * request() 후 엔진이 준비되어 마이크가 열리기(onReady)까지 수 초~수십 초 걸릴 수 있다.
 * 그래서 대기 시간과 응답 시간은 request()가 아니라 onReady 시점부터 잰다.
 */
class SttManager(context: Context) {

    private val speechToText = SpeechToText(context.applicationContext, Mode.ON_DEVICE)

    // SDK 콜백이 어느 스레드로 올지 문서에 없으므로, 세션 상태는 전부 메인 스레드에서만 다룬다
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isReady = false

    // ---- 진행 중인 listen 세션 상태 (메인 스레드 전용). pendingResult == null이면 세션 없음 ----
    private var pendingResult: ((VoiceInputResult) -> Unit)? = null
    private var pendingOnListening: (() -> Unit)? = null
    private var listenTimeoutMs = DEFAULT_TIMEOUT_MS
    private var requestedAt = 0L
    private var listeningStartedAt = 0L // 0이면 아직 마이크가 안 열림
    private var firstSpeechAt = 0L      // 0이면 아직 글자가 인식된 적 없음

    private val startupTimeoutRunnable = Runnable { onStartupTimeout() }
    private val listenTimeoutRunnable = Runnable { onListenTimeout() }

    private val resultListener = object : ResultListener {
        override fun onReady() {
            val readyAt = SystemClock.elapsedRealtime()
            Log.d(TAG, "onReady")
            mainHandler.post { onMicReady(readyAt) }
        }

        // 중간 결과가 나올 때마다 반복해서 호출된다
        override fun onStartedRecognition() {
            Log.d(TAG, "onStartedRecognition")
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            val updatedAt = SystemClock.elapsedRealtime()
            if (completed) {
                Log.d(TAG, "onUpdated(최종): $stt")
                mainHandler.post { deliverFinalText(stt, updatedAt) }
            } else {
                Log.d(TAG, "onUpdated(중간): $stt")
                mainHandler.post { markFirstSpeech(stt, updatedAt) }
            }
        }

        // 음성 구간 위치·입력 크기 값. 지금은 쓰지 않고, 자주 호출되므로 로그도 남기지 않는다
        // (에뮬레이터에서 EPD 시작 위치가 실제 발화 시점과 맞지 않아 첫 마디 시간 측정에도 쓰지 않음)
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        // 최종 결과(onUpdated completed=true)보다 먼저 올 수 있으므로, 여기서 세션을 끝내지 않는다
        override fun onEndedRecognition() {
            Log.d(TAG, "onEndedRecognition")
        }

        override fun onError(errorType: ErrorType) {
            val errorAt = SystemClock.elapsedRealtime()
            Log.e(TAG, "onError: $errorType")
            mainHandler.post { deliverError(errorType, errorAt) }
        }
    }

    fun init() {
        Log.d(TAG, "initialize 요청 (mode=ON_DEVICE)")
        speechToText.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.d(TAG, "initialize 성공")
                speechToText.addListener(resultListener)
                speechToText.setLocale(Locale.KOREA)
                isReady = true
                Log.d(TAG, "준비 완료 (locale=${Locale.KOREA})")
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "initialize 실패: $errorType")
            }
        })
    }

    /**
     * 음성 인식을 시작하고, 결과를 [onResult]로 한 번 전달한다 (메인 스레드).
     *
     * - 마이크가 열리면 [onListening]을 부르고, 그때부터 [timeoutMs] 동안 듣는다.
     * - 시간 값(responseTimeMs, speechStartTimeMs)은 마이크가 열린 시점부터 잰다 (엔진 준비 시간은 빠짐).
     * - 최종 문장 인식: (문장, isTimeout=false)
     * - 듣는 동안 최종 결과 없음 / 빈 문장 / SDK TIMEOUT: (null, isTimeout=true)
     * - 준비 안 됨 / 엔진이 끝내 준비되지 않음 / 그 외 에러: (null, isTimeout=false)
     */
    fun listen(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onListening: (() -> Unit)? = null,
        onResult: (VoiceInputResult) -> Unit
    ) = runOnMain {
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, listen 무시 → 인식 실패로 전달")
            onResult(VoiceInputResult(text = null, responseTimeMs = 0L, isTimeout = false, speechStartTimeMs = null))
            return@runOnMain
        }
        if (pendingResult != null) {
            Log.w(TAG, "이전 듣기가 진행 중이라 취소하고 새로 시작")
            finishSession()
            speechToText.stop()
        }
        pendingResult = onResult
        pendingOnListening = onListening
        listenTimeoutMs = timeoutMs
        requestedAt = SystemClock.elapsedRealtime()
        mainHandler.postDelayed(startupTimeoutRunnable, STARTUP_TIMEOUT_MS)
        Log.d(TAG, "request (마이크가 열리면 ${timeoutMs}ms 동안 듣기)")
        speechToText.request()
    }

    /** 진행 중인 듣기를 취소한다. 취소된 세션의 결과 콜백은 호출되지 않는다. */
    fun stop() = runOnMain {
        if (pendingResult != null) {
            Log.d(TAG, "진행 중인 듣기 취소 (결과 전달 안 함)")
        }
        finishSession()
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, stop 무시")
            return@runOnMain
        }
        Log.d(TAG, "stop 요청")
        speechToText.stop()
    }

    fun release() {
        Log.d(TAG, "release")
        isReady = false
        mainHandler.removeCallbacksAndMessages(null)
        pendingResult = null
        pendingOnListening = null
        speechToText.removeListener(resultListener)
        speechToText.release()
    }

    // 엔진 준비 완료 = 마이크 열림. 여기서부터 대기 시간과 응답 시간을 잰다
    private fun onMicReady(readyAt: Long) {
        if (pendingResult == null) {
            Log.d(TAG, "진행 중인 듣기 없음, onReady 무시 (이미 끝났거나 취소된 요청)")
            return
        }
        if (listeningStartedAt != 0L) return
        listeningStartedAt = readyAt
        mainHandler.removeCallbacks(startupTimeoutRunnable)
        mainHandler.postDelayed(listenTimeoutRunnable, listenTimeoutMs)
        Log.d(TAG, "마이크 열림 (엔진 준비 ${readyAt - requestedAt}ms) → ${listenTimeoutMs}ms 동안 듣기")
        pendingOnListening?.invoke()
    }

    // 글자가 처음 나온 결과(중간·최종)의 시각을 세션당 한 번만 기록한다
    private fun markFirstSpeech(text: String, at: Long) {
        if (pendingResult == null || listeningStartedAt == 0L || firstSpeechAt != 0L || text.isBlank()) return
        firstSpeechAt = at
        Log.d(TAG, "첫 마디 인식 (마이크 열린 뒤 ${at - listeningStartedAt}ms)")
    }

    private fun deliverFinalText(text: String, finalAt: Long) {
        val callback = pendingResult ?: return
        markFirstSpeech(text, finalAt)
        val elapsed = elapsedSinceListening(finalAt)
        val speechStart = speechStartTimeMs()
        finishSession()
        if (text.isBlank()) {
            Log.d(TAG, "결과 전달: 빈 문장 → 무응답")
            callback(VoiceInputResult(text = null, responseTimeMs = elapsed, isTimeout = true, speechStartTimeMs = speechStart))
        } else {
            Log.d(TAG, "결과 전달: $text (말 시작 ${speechStart}ms, 확정 ${elapsed}ms)")
            callback(VoiceInputResult(text = text, responseTimeMs = elapsed, isTimeout = false, speechStartTimeMs = speechStart))
        }
    }

    private fun onListenTimeout() {
        val callback = pendingResult ?: return
        val elapsed = elapsedSinceListening(SystemClock.elapsedRealtime())
        val speechStart = speechStartTimeMs()
        finishSession()
        Log.w(TAG, "마이크 열린 뒤 ${listenTimeoutMs}ms 동안 최종 결과 없음 (말 시작: ${speechStart ?: "없음"}) → stop, 무응답 전달")
        speechToText.stop()
        callback(VoiceInputResult(text = null, responseTimeMs = elapsed, isTimeout = true, speechStartTimeMs = speechStart))
    }

    // 사용자가 대답할 기회 자체가 없었으므로 무응답이 아니라 인식 실패로 전달한다
    private fun onStartupTimeout() {
        val callback = pendingResult ?: return
        finishSession()
        Log.e(TAG, "엔진이 ${STARTUP_TIMEOUT_MS}ms 안에 준비되지 않음 → stop, 인식 실패 전달")
        speechToText.stop()
        callback(VoiceInputResult(text = null, responseTimeMs = 0L, isTimeout = false, speechStartTimeMs = null))
    }

    private fun deliverError(errorType: ErrorType, errorAt: Long) {
        val callback = pendingResult ?: return
        val micWasOpen = listeningStartedAt != 0L
        val elapsed = elapsedSinceListening(errorAt)
        val speechStart = speechStartTimeMs()
        finishSession()
        // 마이크가 열린 뒤의 TIMEOUT만 무응답으로 본다. 열리기 전 에러는 대답할 기회가 없었으므로 인식 실패
        val isTimeout = micWasOpen && errorType == ErrorType.TIMEOUT
        Log.w(TAG, "에러로 듣기 종료: $errorType → ${if (isTimeout) "무응답" else "인식 실패"} 전달")
        callback(VoiceInputResult(text = null, responseTimeMs = elapsed, isTimeout = isTimeout, speechStartTimeMs = speechStart))
    }

    private fun elapsedSinceListening(at: Long): Long =
        if (listeningStartedAt == 0L) 0L else at - listeningStartedAt

    private fun speechStartTimeMs(): Long? =
        if (listeningStartedAt == 0L || firstSpeechAt == 0L) null else firstSpeechAt - listeningStartedAt

    private fun finishSession() {
        pendingResult = null
        pendingOnListening = null
        listeningStartedAt = 0L
        firstSpeechAt = 0L
        mainHandler.removeCallbacks(startupTimeoutRunnable)
        mainHandler.removeCallbacks(listenTimeoutRunnable)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        /** 마이크가 열린 뒤 대답을 기다리는 기본 시간 */
        const val DEFAULT_TIMEOUT_MS = 8_000L

        // request() 후 엔진 준비(onReady)를 기다리는 최대 시간.
        // 에뮬레이터에서 첫 요청 때 엔진 로딩에 약 30초가 걸린 적이 있어 여유를 둔다
        private const val STARTUP_TIMEOUT_MS = 45_000L

        private const val TAG = "KkaeuSTT"
    }
}
