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
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "onError: $errorType")
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

    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "아직 준비 안 됨, speak 무시: $text")
            return
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
        textToSpeech.removeEventListener(eventListener)
        textToSpeech.release()
    }

    private companion object {
        const val TAG = "KkaeuTTS"
    }
}
