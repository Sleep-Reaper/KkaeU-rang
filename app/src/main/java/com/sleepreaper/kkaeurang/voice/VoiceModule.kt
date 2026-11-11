package com.sleepreaper.kkaeurang.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * 음성 모듈 진입점. 다른 팀원은 TtsManager / SttManager 대신 이 클래스만 쓰면 된다.
 *
 * 사용 순서: VoiceModule(context) → init() → askAndListen() / speak() / startAlarm() → release()
 *
 * 대답 내용을 판단하지 않는다. 인식된 문장과 응답 시간을 [VoiceInputResult]로 그대로 넘길 뿐이다.
 */
class VoiceModule(context: Context) {

    private val tts = TtsManager(context)
    private val stt = SttManager(context)
    private val listeningBeep = ListeningBeep()
    private val beepLayer = BeepLayer(context)
    private val voiceAlarm = VoiceAlarm(tts, beepLayer)
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
     * - 음성 경고 중에 부르면 경고를 먼저 멈추고(STOPPED_BY_CALLER) 질문한다.
     */
    fun askAndListen(
        question: String,
        timeoutMs: Long = SttManager.DEFAULT_TIMEOUT_MS,
        onListening: (() -> Unit)? = null,
        onResult: (VoiceInputResult) -> Unit
    ) {
        val session = sessionCounter.incrementAndGet()
        // 경고 중단과 질문 시작의 순서를 지키기 위해 메인 스레드에서 이어서 처리한다
        runOnMain {
            if (session != sessionCounter.get()) return@runOnMain
            if (voiceAlarm.isRunning) {
                Log.i(ALERT_TAG, "askAndListen #$session 호출 → 진행 중인 음성 경고를 먼저 중단")
                voiceAlarm.stop(StopReason.STOPPED_BY_CALLER)
            }
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
    }

    // ---------------- 음성 경고 ----------------

    /** 음성 경고가 진행 중인지. (상태는 메인 스레드에서 바뀌므로, 다른 스레드에서 start 직후 읽으면 늦게 반영될 수 있다) */
    val isAlarmRunning: Boolean
        get() = voiceAlarm.isRunning

    /**
     * 졸음 경고를 시작한다. [text]를 말하고 → [intervalMs] 쉬고 → 다시 말하기를 stopAlarm()이 불릴 때까지 반복한다.
     * - [withBeep]이면 그 아래에 "삐- 삐-" 신호음을 깐다. 음성과 동시에 못 울리는 환경이면 자동으로 문구 사이에만 울린다.
     * - [maxDurationMs]가 지나면 자동으로 멈춘다 (안전장치, 기본 5분). null이면 제한 없음.
     * - 멈추면 [onStopped]가 이유와 함께 한 번 불린다 (메인 스레드).
     * - 이미 경고 중이면 무시한다. setSilenced(true) 상태면 시작하지 않고 바로 onStopped(SILENCED)를 부른다.
     */
    fun startAlarm(
        text: String = VoiceAlarm.DEFAULT_TEXT,
        intervalMs: Long = VoiceAlarm.DEFAULT_INTERVAL_MS,
        withBeep: Boolean = true,
        maxDurationMs: Long? = VoiceAlarm.DEFAULT_MAX_DURATION_MS,
        onStopped: ((reason: StopReason) -> Unit)? = null
    ) {
        voiceAlarm.start(text, intervalMs, withBeep, maxDurationMs, onStopped)
    }

    /** 음성 경고를 즉시 멈춘다 (문구·신호음 모두). onStopped(STOPPED_BY_CALLER)가 불린다. */
    fun stopAlarm() {
        voiceAlarm.stop(StopReason.STOPPED_BY_CALLER)
    }

    /**
     * true: 진행 중인 경고를 즉시 멈추고(SILENCED), false로 바꿀 때까지 새 경고를 재생하지 않는다.
     * (차량 자체 경고가 울리는 동안 앱 경고를 끄는 용도. speak / askAndListen은 막지 않는다)
     */
    fun setSilenced(silenced: Boolean) {
        voiceAlarm.setSilenced(silenced)
    }

    // ---------------- 정리 ----------------

    /** 말하기·듣기·음성 경고를 모두 멈춘다. 진행 중인 askAndListen의 결과는 전달되지 않는다. */
    fun stopAll() {
        sessionCounter.incrementAndGet()
        Log.d(TAG, "stopAll")
        voiceAlarm.stop(StopReason.STOPPED_BY_CALLER)
        tts.stop()
        stt.stop()
    }

    fun release() {
        sessionCounter.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        // 경고가 TTS를 쓰고 있을 수 있으므로 TTS보다 먼저 정리한다
        voiceAlarm.release()
        runOnMain { beepLayer.release() }
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

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object {
        const val TAG = "KkaeuVoice"
        const val ALERT_TAG = "KkaeuAlert"
    }
}
