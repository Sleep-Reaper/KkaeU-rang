package com.sleepreaper.kkaeurang.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 래퍼.
 *
 * ON_DEVICE 모드는 차량 내장 엔진을 쓰므로 앱 등록(registerApp) 없이 동작한다.
 * 사용 순서: init() → (준비 완료 후) speak() / stop() → release()
 *
 * 소리는 Pleos 음성 서비스가 재생한다. SDK 기본 AudioAttributes는 USAGE_MEDIA / CONTENT_TYPE_SPEECH 이다.
 */
class TtsManager(context: Context) {

    private val textToSpeech = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    // SDK 콜백 스레드와 메인 스레드에서 함께 읽고 쓰므로 @Volatile
    @Volatile
    private var isReady = false

    // 현재 speak 요청의 콜백. SDK 콜백 스레드와 함께 쓰므로 lock으로 보호
    private val callbackLock = Any()
    private var pending: SpeakCallbacks? = null

    // 우리가 stop()을 부른 시각. 그 직후 오는 onStop은 우리가 멈춘 것이고,
    // 그 밖의 onStop은 다른 소리에 밀려 '끊긴' 것으로 본다
    @Volatile
    private var lastStopRequestAt = 0L

    /**
     * speak 한 번의 결과 콜백 (SDK 콜백 스레드에서 호출될 수 있다). 한 요청에서 셋 중 하나만 불린다.
     * - [onDone]: 끝까지 말함
     * - [onError]: 준비 안 됨(null) 또는 SDK 에러(ErrorType)로 말하지 못함
     * - [onInterrupted]: 우리가 stop()을 부르지 않았는데 멈춤 (다른 소리에 밀리는 등)
     * [onStart]는 실제 재생이 시작될 때 불린다.
     */
    internal class SpeakCallbacks(
        val onStart: (() -> Unit)? = null,
        val onDone: (() -> Unit)? = null,
        val onError: ((ErrorType?) -> Unit)? = null,
        val onInterrupted: (() -> Unit)? = null
    )

    private val eventListener = object : EventListener {
        override fun onReady() {
            Log.d(TAG, "onReady")
        }

        override fun onStart() {
            Log.d(TAG, "onStart")
            synchronized(callbackLock) { pending?.onStart }?.invoke()
        }

        // 재생 중 계속 호출되므로 로그를 남기지 않는다
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            Log.d(TAG, "onDone")
            takePending { true }?.onDone?.invoke()
        }

        // stop()으로 멈춘 경우는 완료도 실패도 아니므로 콜백을 부르지 않는다.
        // stop 요청 없이 멈췄으면, onInterrupted를 등록한 요청에만 알린다 (등록 안 한 요청의 콜백은 그대로 둔다)
        override fun onStop() {
            val stopAt = lastStopRequestAt
            val requestedByUs = stopAt != 0L && SystemClock.elapsedRealtime() - stopAt < STOP_ATTRIBUTION_MS
            if (requestedByUs) {
                Log.d(TAG, "onStop")
                return
            }
            Log.w(TAG, "onStop (stop 요청 없이 멈춤)")
            takePending { it.onInterrupted != null }?.onInterrupted?.invoke()
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "onError: $errorType")
            takePending { true }?.onError?.invoke(errorType)
        }
    }

    fun init() {
        Log.d(TAG, "initialize 요청 (mode=ON_DEVICE)")
        textToSpeech.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.d(TAG, "initialize 성공")
                textToSpeech.addEventListener(eventListener)
                textToSpeech.setLocale(Locale.KOREA)
                isReady = true
                Log.d(TAG, "준비 완료 (locale=${Locale.KOREA})")
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "initialize 실패: $errorType")
            }
        })
    }

    /**
     * [text]를 말한다. 끝까지 말하면 [onDone]을 부른다.
     * 콜백은 SDK 콜백 스레드에서 호출될 수 있다.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        speak(text, onDone, onError = null)
    }

    /**
     * [speak]에 실패 콜백을 더한 버전.
     * 준비 안 됨·재생 에러로 끝까지 말하지 못하면 [onError]를 부른다. (stop()으로 멈추면 둘 다 안 부름)
     */
    fun speak(text: String, onDone: (() -> Unit)?, onError: (() -> Unit)?) {
        speakWithCallbacks(
            text,
            SpeakCallbacks(onDone = onDone, onError = onError?.let { callback -> { _: ErrorType? -> callback() } })
        )
    }

    /** 시작·에러 종류·끊김까지 구분해서 받는 버전 (VoiceAlarm용). */
    internal fun speakWithCallbacks(text: String, callbacks: SpeakCallbacks) {
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, speak 무시: $text")
            callbacks.onError?.invoke(null)
            return
        }
        // 새 요청이 들어오면 이전 요청의 콜백은 버린다
        synchronized(callbackLock) { pending = callbacks }
        Log.d(TAG, "speak: $text")
        textToSpeech.speak(text)
    }

    /** 말하기를 멈춘다. 멈춘 요청의 콜백은 호출되지 않는다. */
    fun stop() {
        takePending { true }
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, stop 무시")
            return
        }
        lastStopRequestAt = SystemClock.elapsedRealtime()
        Log.d(TAG, "stop 요청")
        textToSpeech.stop()
    }

    fun release() {
        Log.d(TAG, "release")
        isReady = false
        takePending { true }
        textToSpeech.removeEventListener(eventListener)
        textToSpeech.release()
    }

    // 조건이 맞으면 대기 중인 콜백을 꺼내고 비운다. 한 요청의 콜백은 한 번만 불리게 하기 위함
    private fun takePending(condition: (SpeakCallbacks) -> Boolean): SpeakCallbacks? =
        synchronized(callbackLock) {
            val callbacks = pending
            if (callbacks != null && condition(callbacks)) {
                pending = null
                callbacks
            } else {
                null
            }
        }

    private companion object {
        const val TAG = "KkaeuTTS"

        // stop() 요청 후 이 시간 안에 오는 onStop은 우리가 멈춘 것으로 본다
        const val STOP_ATTRIBUTION_MS = 1_500L
    }
}
