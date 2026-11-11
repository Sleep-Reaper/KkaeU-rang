package com.sleepreaper.kkaeurang.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 래퍼.
 *
 * ON_DEVICE 모드는 차량 내장 엔진을 쓰므로 앱 등록(registerApp) 없이 동작한다.
 * 사용 순서: init() → (준비 완료 후) speak() / stop() → release()
 */
class TtsManager(context: Context) {

    private val textToSpeech = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    // SDK 콜백 스레드와 메인 스레드에서 함께 읽고 쓰므로 @Volatile
    @Volatile
    private var isReady = false

    // 현재 speak 요청의 완료·실패 콜백. SDK 콜백 스레드와 함께 쓰므로 lock으로 보호
    private val callbackLock = Any()
    private var pendingOnDone: (() -> Unit)? = null
    private var pendingOnError: (() -> Unit)? = null

    private val eventListener = object : EventListener {
        override fun onReady() {
            Log.d(TAG, "onReady")
        }

        override fun onStart() {
            Log.d(TAG, "onStart")
        }

        // 재생 중 계속 호출되므로 로그를 남기지 않는다
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            Log.d(TAG, "onDone")
            takePendingCallbacks().first?.invoke()
        }

        // stop()으로 멈춘 경우는 완료도 실패도 아니므로 콜백을 부르지 않는다
        override fun onStop() {
            Log.d(TAG, "onStop")
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "onError: $errorType")
            takePendingCallbacks().second?.invoke()
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
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, speak 무시: $text")
            onError?.invoke()
            return
        }
        // 새 요청이 들어오면 이전 요청의 콜백은 버린다
        synchronized(callbackLock) {
            pendingOnDone = onDone
            pendingOnError = onError
        }
        Log.d(TAG, "speak: $text")
        textToSpeech.speak(text)
    }

    fun stop() {
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, stop 무시")
            return
        }
        Log.d(TAG, "stop 요청")
        textToSpeech.stop()
    }

    fun release() {
        Log.d(TAG, "release")
        isReady = false
        takePendingCallbacks()
        textToSpeech.removeEventListener(eventListener)
        textToSpeech.release()
    }

    // 대기 중인 (onDone, onError)를 꺼내고 비운다. 한 요청의 콜백은 한 번만 불리게 하기 위함
    private fun takePendingCallbacks(): Pair<(() -> Unit)?, (() -> Unit)?> =
        synchronized(callbackLock) {
            val callbacks = pendingOnDone to pendingOnError
            pendingOnDone = null
            pendingOnError = null
            callbacks
        }

    private companion object {
        const val TAG = "KkaeuTTS"
    }
}
