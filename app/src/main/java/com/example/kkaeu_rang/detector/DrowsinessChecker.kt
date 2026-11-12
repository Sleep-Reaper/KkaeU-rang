package com.example.kkaeu_rang.detector

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 졸음 판단 엔진. 1초마다 샘플을 찍고 → 규칙 ①~⑥ → 점수 → 4단계를 정해 [result]로 내보낸다.
 * 단계가 오르면 알람·창문·내비·질문을 직접 호출한다.
 *
 * 모든 동작은 **메인 스레드**에서 일어난다. (음성 콜백도 메인이라 별도 동기화가 필요 없다.)
 * 차량 신호 리스너는 다른 스레드여도 되지만 [SignalSource]가 최신값만 들고 있어야 한다.
 */
class DrowsinessChecker(
    private val source: SignalSource,
    private val voice: DriverVoice,
    private val alarm: AlarmModule,
    private val window: WindowModule,
    private val navi: NaviModule,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { Log.d(TAG, it) }
) {

    private val samples = ArrayDeque<Sample>()

    private var eventScore = 0f                              // ①~④ + 운전자 응답 가산, 시간이 지나면 감소
    private var driveStartMs: Long? = null                   // ⑤ 연속 주행 시작 시각
    private val recentEvents = ArrayDeque<Pair<Long, String>>()  // 이유 문장용

    private var lastLevel = Level.NORMAL
    private var forcedLevel = Level.NORMAL                   // 무응답으로 올려 둔 단계
    private var forcedUntil = 0L
    private var lastWindowNaviMs: Long? = null
    private var lastAskMs: Long? = null
    private var asking = false
    private var retryCount = 0

    private val _result = MutableStateFlow(DrowsinessResult())
    val result: StateFlow<DrowsinessResult> = _result

    private var handler: Handler? = null
    private val loop = object : Runnable {
        override fun run() {
            tick(clock())
            handler?.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        source.start()
        handler = Handler(Looper.getMainLooper()).also { it.post(loop) }
    }

    fun stop() {
        handler?.removeCallbacks(loop)
        handler = null
        voice.stopAll()          // 취소되면 onResult가 오지 않으므로 상태를 직접 초기화한다
        asking = false
        alarm.stop()
        source.stop()
    }

    internal fun tick(now: Long) {
        // 1) 샘플 찍기, 최근 10분만 보관
        val s = source.sample(now)
        samples.addLast(s)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        val list = samples.toList()

        // 2) 연속 주행 시간 (P단이면 휴식으로 보고 초기화, 신호 대기(D단)는 유지)
        driveStartMs = if (s.isPark) null else (driveStartMs ?: now)

        // 3) 이벤트 규칙 ①~④ — 시작된 순간에만 점수 추가
        if (DrowsinessRules.isSuddenCorrection(list)) addEvent(now, 25f, "핸들 고정 후 급조작")
        else if (justStarted(list, DrowsinessRules::isSteeringFrozen)) addEvent(now, 15f, "핸들 조작 없음 10초 이상")
        if (justStarted(list, DrowsinessRules::isDriftWithoutSignal)) addEvent(now, 15f, "방향지시등 없이 한쪽으로 쏠림")
        if (justStarted(list, DrowsinessRules::isSpeedUnstable)) addEvent(now, 10f, "속도 유지 불안정")

        // 4) 이벤트 점수 감소: 1초에 0.05점 → 10분이면 30점
        eventScore = (eventScore - DECAY_PER_TICK).coerceIn(0f, MAX_EVENT_SCORE)
        while (recentEvents.isNotEmpty() && now - recentEvents.first().first > EVENT_MEMORY_MS)
            recentEvents.removeFirst()

        // 5) 기본 위험도 ⑤⑥
        val reasons = mutableListOf<String>()
        var base = 0f
        if (DrowsinessRules.isLongDrive(driveStartMs, now)) {
            base += 10f
            val min = (now - driveStartMs!!) / 60_000
            reasons += "연속 주행 ${min / 60}시간 ${min % 60}분"
        }
        if (DrowsinessRules.isRiskyHour(now)) {
            base += 15f
            reasons += "졸음 위험 시간대"
        }

        // 6) 합산 → 4단계. 무응답으로 올려 둔 단계가 더 높으면 그걸 쓴다.
        val score = (base + eventScore).roundToInt().coerceIn(0, 100)
        val scoreLevel = when {
            score >= 80 -> Level.DANGER
            score >= 70 -> Level.WARNING
            score >= 40 -> Level.CAUTION
            else -> Level.NORMAL
        }
        val level = if (now < forcedUntil && forcedLevel > scoreLevel) forcedLevel else scoreLevel

        reasons += recentEvents.groupingBy { it.second }.eachCount()
            .map { (msg, n) -> if (n > 1) "$msg ${n}회" else msg }

        _result.value = DrowsinessResult(score, level, reasons, now)
        log("score=$score level=$level reasons=$reasons spd=${s.speedKmh} str=${s.steeringDeg}")

        if (level != lastLevel) {
            val prev = lastLevel
            lastLevel = level
            onLevelChanged(prev, level, now)
        }
    }

    /** 단계가 바뀐 순간에 한 번만 호출된다. */
    private fun onLevelChanged(prev: Level, next: Level, now: Long) {
        log("단계 변경: $prev → $next")

        if (next == Level.CAUTION && prev < Level.CAUTION) {
            retryCount = 0
            if (canAsk(now)) askDriver(now)
        }
        if (next >= Level.WARNING && prev < Level.WARNING) {
            alarm.start()
        }
        if (next == Level.DANGER && prev < Level.DANGER) {
            val last = lastWindowNaviMs
            if (last == null || now - last > WINDOW_NAVI_COOLDOWN_MS) {
                window.open()
                navi.guideToRestArea()
                lastWindowNaviMs = now
            }
        }
        if (next < Level.WARNING && prev >= Level.WARNING) {
            alarm.stop()
        }
    }

    // ── 운전자 질문 ──

    private fun canAsk(now: Long): Boolean {
        val last = lastAskMs
        return !asking && (last == null || now - last >= ASK_COOLDOWN_MS)
    }

    private fun askDriver(now: Long) {
        asking = true
        lastAskMs = now
        voice.ask(QUESTION) { answer -> onAnswer(answer, clock()) }
    }

    private fun onAnswer(answer: VoiceAnswer, now: Long) {
        asking = false
        val response = DriverResponseClassifier.classify(answer)
        log("운전자 응답: $response ($answer)")

        when (response) {
            DriverResponse.OK -> lastAskMs = now                     // 5분간 다시 묻지 않음
            DriverResponse.ADMITTED -> addEvent(now, 30f, "운전자가 졸림을 호소")
            DriverResponse.UNCLEAR, DriverResponse.SLOW ->
                if (retryCount < MAX_RETRY) {
                    retryCount++
                    askDriver(now)                                   // 1회 재질문
                }
            DriverResponse.NO_ANSWER -> {
                forcedLevel = if (lastLevel >= Level.WARNING) Level.DANGER else Level.WARNING
                forcedUntil = now + FORCED_MS
                recentEvents.addLast(now to "운전자 응답 없음")
            }
            DriverResponse.ERROR -> Unit                             // 기기 문제일 수 있어 판단 보류
        }
    }

    private fun addEvent(now: Long, point: Float, msg: String) {
        eventScore = (eventScore + point).coerceAtMost(MAX_EVENT_SCORE)
        recentEvents.addLast(now to msg)
    }

    /** 같은 상태가 계속되는 동안 매초 점수가 쌓이지 않도록, 직전 틱에는 거짓이었다가 이번에 참이 된 순간만 true */
    private fun justStarted(list: List<Sample>, rule: (List<Sample>) -> Boolean): Boolean =
        rule(list) && !(list.size > 1 && rule(list.dropLast(1)))

    companion object {
        private const val TAG = "Kkaeu.Checker"
        private const val QUESTION = "주행이 평소와 조금 달라요. 괜찮으세요?"

        private const val TICK_MS = 1000L
        private const val MAX_SAMPLES = 600
        private const val DECAY_PER_TICK = 0.05f
        private const val MAX_EVENT_SCORE = 100f
        private const val EVENT_MEMORY_MS = 10 * 60_000L

        private const val ASK_COOLDOWN_MS = 5 * 60_000L
        private const val MAX_RETRY = 1
        private const val FORCED_MS = 3 * 60_000L
        private const val WINDOW_NAVI_COOLDOWN_MS = 10 * 60_000L
    }
}
