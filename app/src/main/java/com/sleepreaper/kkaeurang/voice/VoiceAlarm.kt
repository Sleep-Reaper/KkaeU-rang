package com.sleepreaper.kkaeurang.voice

import ai.pleos.playground.tts.constant.ErrorType
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** 음성 경고가 끝난 이유 */
enum class StopReason {
    /** 호출한 쪽이 멈춤: stopAlarm() / stopAll() / askAndListen() / release() */
    STOPPED_BY_CALLER,

    /** maxDurationMs가 지나 자동으로 멈춤 (stopAlarm()이 불리지 않았을 때를 대비한 안전장치) */
    MAX_DURATION,

    /** 다른 소리(전화·긴급 안내 등)에 신호음의 오디오 포커스를 뺏겨 멈춤 */
    AUDIO_FOCUS_LOST,

    /** setSilenced(true) 상태라 멈추거나 시작하지 않음 */
    SILENCED
}

/**
 * 졸음 경고: 경고 문구를 stop()이 불릴 때까지 반복해서 말하고, 그 아래에 신호음을 깐다.
 * 대답을 듣거나 판단하지 않는다.
 *
 * 신호음 방식
 * - CONCURRENT: 신호음을 계속 울리면서 그 위로 문구를 말한다 (기본으로 먼저 시도).
 * - GAPS_ONLY (fallback): 문구를 말하는 동안에는 신호음을 끄고(포커스 반납), 문구 사이(intervalMs)에만 울린다.
 *   TTS는 Pleos 음성 서비스가, 신호음은 우리 앱이 재생하므로 차량 포커스 정책상 둘이 같이 못 나올 수 있다.
 *   그래서 다음 경우에 자동으로 GAPS_ONLY로 바꾼다.
 *     · 문구를 말하는 중에 신호음이 포커스를 잃음 (TTS가 포커스를 가져감)
 *     · 신호음이 울리는 중에 TTS가 에러 / 끊김(onStop) / 응답 없음
 *     · 처음부터 신호음 포커스를 받지 못함
 *   문구를 말하지 않는 구간에 신호음이 포커스를 잃으면 다른 소리 때문이므로 경고를 멈춘다 (AUDIO_FOCUS_LOST).
 *
 * 모든 동작은 메인 스레드에서 처리하고, onStopped도 메인 스레드에서 부른다. 로그 태그는 KkaeuAlert.
 */
internal class VoiceAlarm(
    private val tts: TtsManager,
    private val beepLayer: BeepLayer
) {

    private enum class BeepMode {
        OFF,        // withBeep = false
        CONCURRENT, // 문구와 신호음을 동시에 재생
        GAPS_ONLY   // fallback: 신호음은 문구 사이에만
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 경고가 진행 중인지. 상태는 메인 스레드에서 바뀐다 */
    @Volatile
    var isRunning = false
        private set

    // ---- 이하 메인 스레드 전용 ----
    private var released = false
    private var silenced = false

    // start/stop마다 1씩 증가. 콜백 시점에 값이 다르면 이전 경고의 콜백이라 버린다
    private var generation = 0
    private var text = DEFAULT_TEXT
    private var intervalMs = DEFAULT_INTERVAL_MS
    private var maxDurationMs: Long? = DEFAULT_MAX_DURATION_MS
    private var onStopped: ((StopReason) -> Unit)? = null
    private var beepMode = BeepMode.OFF
    private var repeatCount = 0      // 지금까지 말하기 시작한 문구 수 (현재 문구 번호)
    private var startedAt = 0L
    private var ttsActive = false    // 지금 문구를 말하는 중인지 (speak 요청 ~ 완료·실패)
    private var ttsEndedAt = 0L
    private var concurrentConfirmed = false

    private val nextPhraseRunnable = Runnable { speakNext() }
    private val maxDurationRunnable = Runnable { onMaxDuration() }
    private val ttsWatchdogRunnable = Runnable { onTtsWatchdog() }

    init {
        beepLayer.onFocusLost = { change -> onBeepFocusLost(change) }
    }

    /**
     * 경고를 시작한다. [text]를 말하고 → [intervalMs] 쉬고 → 다시 말하기를 stop()이 불릴 때까지 반복한다.
     * - [withBeep]: 신호음을 함께 깐다
     * - [maxDurationMs]: 이 시간이 지나면 자동으로 멈춘다 (MAX_DURATION). null이면 제한 없음
     * - [onStopped]: 멈출 때 이유와 함께 한 번 불린다 (메인 스레드)
     * 이미 경고 중이면 무시한다 (로그만 남김, 이번 호출의 onStopped는 불리지 않음).
     */
    fun start(
        text: String = DEFAULT_TEXT,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        withBeep: Boolean = true,
        maxDurationMs: Long? = DEFAULT_MAX_DURATION_MS,
        onStopped: ((reason: StopReason) -> Unit)? = null
    ) = runOnMain {
        if (released) {
            Log.w(TAG, "해제된 뒤라 경고 시작 무시")
            return@runOnMain
        }
        if (isRunning) {
            Log.w(TAG, "이미 경고 중 → start 무시 (진행 중인 경고 #$repeatCount 유지, 신호음 방식=$beepMode)")
            return@runOnMain
        }
        if (silenced) {
            Log.w(TAG, "무음 상태(setSilenced=true) → 경고 시작 안 함")
            onStopped?.invoke(StopReason.SILENCED)
            return@runOnMain
        }

        generation++
        isRunning = true
        this.text = text
        this.intervalMs = intervalMs.coerceAtLeast(0L)
        this.maxDurationMs = maxDurationMs
        this.onStopped = onStopped
        repeatCount = 0
        startedAt = SystemClock.elapsedRealtime()
        ttsActive = false
        ttsEndedAt = 0L
        concurrentConfirmed = false
        Log.i(
            TAG,
            "경고 시작: \"$text\" (간격 ${this.intervalMs}ms, 신호음 ${if (withBeep) "켬" else "끔"}, " +
                "최대 ${maxDurationMs?.let { "${it}ms" } ?: "제한 없음"})"
        )

        if (maxDurationMs != null) mainHandler.postDelayed(maxDurationRunnable, maxDurationMs.coerceAtLeast(0L))

        beepMode = when {
            !withBeep -> BeepMode.OFF
            beepLayer.start() -> {
                Log.i(TAG, "신호음 방식: CONCURRENT (문구와 신호음을 동시에 재생 시도)")
                BeepMode.CONCURRENT
            }
            else -> {
                Log.w(TAG, "신호음 방식: GAPS_ONLY (fallback, 처음부터 신호음 포커스를 받지 못함) → 신호음은 문구 사이에만")
                BeepMode.GAPS_ONLY
            }
        }
        speakNext()
    }

    /** 경고를 즉시 멈춘다 (TTS·신호음 모두). 경고 중이 아니면 무시한다. */
    fun stop(reason: StopReason = StopReason.STOPPED_BY_CALLER) = runOnMain {
        if (!isRunning) {
            Log.d(TAG, "경고 중이 아님 → stop 무시 ($reason)")
            return@runOnMain
        }
        Log.i(TAG, "경고 중단 요청 ($reason)")
        stopInternal(reason)
    }

    /** true면 진행 중인 경고를 SILENCED로 멈추고, false로 바꿀 때까지 새 경고를 시작하지 않는다. */
    fun setSilenced(silenced: Boolean) = runOnMain {
        if (this.silenced == silenced) return@runOnMain
        this.silenced = silenced
        Log.i(TAG, if (silenced) "무음 켬 → 경고를 재생하지 않음" else "무음 끔 → 경고 재생 가능")
        if (silenced && isRunning) stopInternal(StopReason.SILENCED)
    }

    fun release() = runOnMain {
        if (isRunning) stopInternal(StopReason.STOPPED_BY_CALLER)
        released = true
        beepLayer.onFocusLost = null
    }

    // ---- 반복 ----

    private fun speakNext() {
        if (!isRunning) return
        // fallback이면 문구를 말하는 동안 신호음을 끄고 포커스를 돌려준다 (TTS가 포커스를 받을 수 있게)
        if (beepMode == BeepMode.GAPS_ONLY) beepLayer.stop()

        repeatCount++
        val gen = generation
        val n = repeatCount
        ttsActive = true
        Log.d(TAG, "#$n 문구 말하기 요청 (신호음 방식=$beepMode${if (beepLayer.isPlaying) ", 신호음 재생 중" else ""})")
        mainHandler.postDelayed(ttsWatchdogRunnable, ttsTimeoutMs())

        tts.speakWithCallbacks(
            text,
            TtsManager.SpeakCallbacks(
                onStart = { postIfCurrent(gen, n) { onTtsStart(n) } },
                onDone = { postIfCurrent(gen, n) { onTtsDone(n) } },
                onError = { type: ErrorType? -> postIfCurrent(gen, n) { onTtsFailed(n, type?.let { "에러 $it" }) } },
                onInterrupted = { postIfCurrent(gen, n) { onTtsFailed(n, "stop 요청 없이 끊김(onStop)") } }
            )
        )
    }

    private fun onTtsStart(n: Int) {
        if (!ttsActive) return
        val beepNote = when {
            beepMode == BeepMode.CONCURRENT && beepLayer.isPlaying -> " — 신호음과 동시 재생 중"
            beepMode == BeepMode.CONCURRENT -> " — 신호음이 멈춰 있음"
            else -> ""
        }
        Log.d(TAG, "#$n TTS 재생 시작$beepNote")
    }

    private fun onTtsDone(n: Int) {
        if (!ttsActive) return
        finishPhrase()
        if (beepMode == BeepMode.CONCURRENT && beepLayer.isPlaying && !concurrentConfirmed) {
            concurrentConfirmed = true
            Log.i(TAG, "동시 재생 확인: 신호음이 끊기지 않은 채 #$n 문구를 끝까지 말함 → CONCURRENT 유지")
        }
        Log.d(TAG, "#$n 문구 끝 → ${intervalMs}ms 쉼")
        scheduleNext(intervalMs)
    }

    /** [reason]이 null이면 TTS가 아직 준비 안 된 것 (포커스 문제가 아님) */
    private fun onTtsFailed(n: Int, reason: String?) {
        if (!ttsActive) return
        finishPhrase()
        val retryDelay = maxOf(intervalMs, MIN_RETRY_DELAY_MS)
        when {
            reason == null ->
                Log.w(TAG, "#$n TTS가 아직 준비 안 됨 → ${retryDelay}ms 뒤 다시 말하기")
            beepMode == BeepMode.CONCURRENT && beepLayer.isPlaying ->
                switchToGapsOnly("신호음과 함께 말한 #$n 문구가 실패: $reason (TTS 거절로 판단)")
            else ->
                Log.w(TAG, "#$n 문구 실패: $reason → ${retryDelay}ms 뒤 다시 말하기")
        }
        scheduleNext(retryDelay)
    }

    // 문구가 끝나지도 실패하지도 않고 멈춰 있음 → 요청을 정리하고 실패로 처리
    private fun onTtsWatchdog() {
        if (!isRunning || !ttsActive) return
        Log.w(TAG, "#$repeatCount 문구가 ${ttsTimeoutMs()}ms 안에 끝나지 않음 → TTS stop 후 실패로 처리")
        tts.stop()
        onTtsFailed(repeatCount, "응답 없음")
    }

    private fun finishPhrase() {
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        ttsActive = false
        ttsEndedAt = SystemClock.elapsedRealtime()
    }

    // 쉼 구간: fallback이면 이때 신호음을 울리고, [delayMs] 뒤 다음 문구를 말한다
    private fun scheduleNext(delayMs: Long) {
        if (!isRunning) return
        if (beepMode == BeepMode.GAPS_ONLY && delayMs > 0 && !beepLayer.start()) {
            Log.w(TAG, "문구 사이 신호음 포커스를 받지 못함 → 이번 쉼은 신호음 없이")
        }
        mainHandler.removeCallbacks(nextPhraseRunnable)
        mainHandler.postDelayed(nextPhraseRunnable, delayMs)
    }

    // ---- 포커스·fallback ----

    private fun onBeepFocusLost(change: Int) {
        if (!isRunning) return
        val changeName = BeepLayer.focusChangeName(change)
        val duringTts = ttsActive ||
            (ttsEndedAt != 0L && SystemClock.elapsedRealtime() - ttsEndedAt < TTS_FOCUS_GRACE_MS)
        if (beepMode == BeepMode.CONCURRENT && duringTts) {
            // TTS가 포커스를 가져가 신호음이 끊김. TTS는 계속 말하게 두고, 끝나면 문구 사이에 신호음을 울린다
            switchToGapsOnly("#$repeatCount 문구를 말하는 중에 신호음이 포커스를 잃음($changeName)")
        } else {
            Log.w(TAG, "문구를 말하지 않는 중에 신호음 포커스를 잃음($changeName) → 다른 소리 때문으로 보고 경고 중단")
            stopInternal(StopReason.AUDIO_FOCUS_LOST)
        }
    }

    private fun switchToGapsOnly(reason: String) {
        if (beepMode != BeepMode.CONCURRENT) return
        beepMode = BeepMode.GAPS_ONLY
        beepLayer.stop()
        Log.w(TAG, "동시 재생 실패: $reason → 신호음 방식: GAPS_ONLY (fallback, 신호음은 문구 사이 ${intervalMs}ms에만)")
    }

    // ---- 중단 ----

    private fun onMaxDuration() {
        if (!isRunning) return
        Log.w(TAG, "최대 시간 ${maxDurationMs}ms 초과 → 자동 중단 (stopAlarm()이 불리지 않았는지 확인 필요)")
        stopInternal(StopReason.MAX_DURATION)
    }

    private fun stopInternal(reason: StopReason) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val mode = beepMode
        isRunning = false
        generation++
        mainHandler.removeCallbacks(nextPhraseRunnable)
        mainHandler.removeCallbacks(maxDurationRunnable)
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        beepLayer.stop()
        if (ttsActive) tts.stop()
        ttsActive = false
        beepMode = BeepMode.OFF
        Log.i(TAG, "경고 종료: $reason (문구 ${repeatCount}회, ${elapsed}ms, 신호음 방식=$mode)")

        // 콜백 안에서 다시 start()를 부를 수 있도록 상태를 다 정리한 뒤 부른다
        val callback = onStopped
        onStopped = null
        callback?.invoke(reason)
    }

    // ---- 유틸 ----

    private fun ttsTimeoutMs(): Long = TTS_TIMEOUT_BASE_MS + text.length * TTS_TIMEOUT_PER_CHAR_MS

    // SDK 스레드 → 메인 스레드. 그 사이 경고가 멈췄거나 다음 문구로 넘어갔으면 버린다
    private fun postIfCurrent(gen: Int, n: Int, block: () -> Unit) {
        mainHandler.post {
            if (isRunning && gen == generation && n == repeatCount) block()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        const val DEFAULT_TEXT = "졸음 감지. 졸음 감지."
        const val DEFAULT_INTERVAL_MS = 800L
        const val DEFAULT_MAX_DURATION_MS = 300_000L // 5분

        private const val TAG = "KkaeuAlert"

        // TTS 실패 후 다시 말하기까지 최소 대기 (준비 안 됨 등으로 빠르게 반복 실패하는 것을 막음)
        private const val MIN_RETRY_DELAY_MS = 1_000L

        // 문구가 끝난 직후 이 시간 안에 신호음이 포커스를 잃어도 TTS 때문으로 본다 (콜백 순서 차이 대비)
        private const val TTS_FOCUS_GRACE_MS = 500L

        // 문구 하나가 이 시간 안에 끝나지 않으면 TTS가 멈춘 것으로 본다 (엔진 준비 시간 포함 여유)
        private const val TTS_TIMEOUT_BASE_MS = 10_000L
        private const val TTS_TIMEOUT_PER_CHAR_MS = 300L
    }
}
