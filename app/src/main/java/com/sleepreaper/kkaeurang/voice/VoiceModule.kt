package com.sleepreaper.kkaeurang.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * 음성 모듈 진입점. 다른 팀원은 TtsManager / SttManager 대신 이 클래스만 쓰면 된다.
 *
 * 사용 순서: VoiceModule(context) → init() → askAndListen() / speak() → release()
 *
 * 대답 내용을 판단하지 않는다. 인식된 문장과 응답 시간을 [VoiceInputResult]로 그대로 넘길 뿐이다.
 */
class VoiceModule(context: Context) {

    private val tts = TtsManager(context)
    private val stt = SttManager(context)
    private val listeningBeep = ListeningBeep()
    private val mainHandler = Handler(Looper.getMainLooper())

    // askAndListen 호출마다 1씩 증가. 콜백 시점에 값이 바뀌어 있으면 취소된(또는 새 질문에 밀린) 세션이라 버린다
    private val sessionCounter = AtomicInteger(0)

    fun init() {
        tts.init()
        stt.init()
    }

    /** 듣지 않고 말만 한다. */
    fun speak(text: String) {
        tts.speak(text)
    }

    /**
     * [question]을 말한 뒤 사용자 대답을 듣고, 결과를 [onResult]로 한 번 전달한다.
     * - 질문이 끝나고 마이크가 실제로 열리면 '띵' 신호음을 낸 뒤 [onListening]을 부른다. ("지금 말하세요" 안내용)
     *   그때부터 [timeoutMs] 동안 듣고, 응답 시간도 그 시점부터 잰다.
     * - [onListening]과 [onResult]는 항상 메인 스레드에서 호출된다.
     * - 진행 중에 askAndListen을 다시 부르거나 stopAll()을 부르면, 이전 호출의 콜백은 호출되지 않는다.
     */
    fun askAndListen(
        question: String,
        timeoutMs: Long = SttManager.DEFAULT_TIMEOUT_MS,
        onListening: (() -> Unit)? = null,
        onResult: (VoiceInputResult) -> Unit
    ) {
        val session = sessionCounter.incrementAndGet()
        Log.d(TAG, "askAndListen #$session 시작: $question")

        tts.speak(
            question,
            onDone = {
                postIfCurrent(session) {
                    Log.d(TAG, "askAndListen #$session 질문 끝 → 듣기 요청")
                    stt.listen(
                        timeoutMs,
                        onListening = {
                            if (session == sessionCounter.get()) {
                                Log.d(TAG, "askAndListen #$session 마이크 열림 → 신호음")
                                listeningBeep.play()
                                onListening?.invoke()
                            }
                        }
                    ) { result ->
                        if (session != sessionCounter.get()) return@listen
                        Log.d(TAG, "askAndListen #$session 결과: $result")
                        onResult(result)
                    }
                }
            },
            onError = {
                postIfCurrent(session) {
                    val result = VoiceInputResult(text = null, responseTimeMs = 0L, isTimeout = false)
                    Log.w(TAG, "askAndListen #$session 질문을 말하지 못함 → $result")
                    onResult(result)
                }
            }
        )
    }

    /** 말하기·듣기를 모두 멈춘다. 진행 중인 askAndListen의 결과는 전달되지 않는다. */
    fun stopAll() {
        sessionCounter.incrementAndGet()
        Log.d(TAG, "stopAll")
        tts.stop()
        stt.stop()
    }

    fun release() {
        sessionCounter.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        tts.release()
        stt.release()
        listeningBeep.release()
    }

    // 메인 스레드로 넘기되, 그 사이 세션이 취소됐으면 실행하지 않는다
    private fun postIfCurrent(session: Int, block: () -> Unit) {
        mainHandler.post {
            if (session == sessionCounter.get()) block()
        }
    }

    private companion object {
        const val TAG = "KkaeuVoice"
    }
}
