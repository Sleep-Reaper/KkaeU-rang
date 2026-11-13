package com.sleepreaper.kkaeurang.detector

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 졸음 판단 엔진. 1초마다 샘플을 찍고 → 규칙 ①~⑥ → 안전 점수 → 4단계를 정해 [result]로 내보낸다.
 * 점수는 **100에서 시작해 위험한 사건이 있으면 감점**한다 (화면의 안전 점수와 같은 값, 기준은 [ScoreThresholds]).
 * 단계가 오르면 알람·창문·내비·질문을 직접 호출한다.
 *
 * 모든 동작은 **메인 스레드**에서 일어난다. (음성 콜백도 메인이라 별도 동기화가 필요 없다.)
 * 차량 신호 리스너는 다른 스레드여도 되지만 [SignalSource]가 최신값만 들고 있어야 한다.
 *
 * ## UI에서 쓰는 법 (세 값 모두 1초마다 갱신되고, 메인 스레드에서 바뀜다)
 * ```
 * val result by checker.result.collectAsState()   // score(0~100), level, reasons
 * val sample by checker.sample.collectAsState()   // 속도·핸들·깜빡이·브레이크·기어 실시간 값
 * val logs by checker.logs.collectAsState()       // 최근 사건 로그 (최대 100개, 오래된 것이 앞)
 * ```
 * - [logs]는 단계 변경·점수 감점·질문·응답·알람/창문/내비 호출만 담는다. 매초 점수는 [result]를 쓴다.
 * - 운전자 대답 원문은 로그에 넣지 않고 분류 결과만 남긴다.
 * - [start]가 성공해야 값이 갱신된다. (권한 요청 후 시작)
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

    private var eventPenalty = 0f                            // ①~④ + 운전자 응답 감점, 시간이 지나면 회복
    private var driveStartMs: Long? = null                   // ⑤ 연속 주행 시작 시각
    private var parkSinceMs: Long? = null                    // P단이 이어지기 시작한 시각 (P단이 아니면 null)
    private val recentEvents = ArrayDeque<Pair<Long, String>>()  // 이유 문장용

    private var lastLevel = Level.NORMAL
    private var lastScoreLevel = Level.NORMAL                // 점수만으로 정한 직전 단계 (무응답 강제 단계 제외, 내려갈 때 여유 계산용)
    private var forcedLevel = Level.NORMAL                   // 무응답으로 올려 둔 단계
    private var forcedUntil = 0L
    private var lastWindowNaviMs: Long? = null
    private var lastAskMs: Long? = null
    private var asking = false
    private var retryCount = 0
    private var conditionRisk = 0f                           // 출발 전 컨디션으로 정한 기본 위험도 (0이면 없음)
    private var conditionReason: String? = null

    private val _result = MutableStateFlow(DrowsinessResult())
    val result: StateFlow<DrowsinessResult> = _result

    private val _sample = MutableStateFlow(Sample(0L, 0f, 0f, false, false, true))
    /** 마지막으로 찍은 차량 상태. 시작 전에는 정지(P단, 0km/h) 기본값 */
    val sample: StateFlow<Sample> = _sample

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    /** 최근 사건 로그 (최대 [MAX_LOGS]개) */
    val logs: StateFlow<List<LogEntry>> = _logs

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
        _sample.value = s
        samples.addLast(s)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        val list = samples.toList()

        // 2) 연속 주행 시간 (P단이면 휴식으로 보고 초기화, 신호 대기(D단)는 유지)
        driveStartMs = if (s.isPark) null else (driveStartMs ?: now)

        // 2-1) 경고 이상에서 P단으로 정차하면 휴식으로 보고 상태를 해제한다.
        //      기어 신호가 잠시 P로 읽히는 경우를 막기 위해 P단이 [PARK_CONFIRM_MS] 이어질 때만 해제한다.
        parkSinceMs = if (s.isPark) (parkSinceMs ?: now) else null
        val parkedFor = parkSinceMs?.let { now - it } ?: 0L
        if (s.isPark && parkedFor >= PARK_CONFIRM_MS && lastLevel >= Level.WARNING) releaseOnPark(now)

        // 3) 이벤트 규칙 ①~④ — 시작된 순간에만 감점
        if (DrowsinessRules.isSuddenCorrection(list)) addEvent(now, 25f, "핸들 고정 후 급조작")
        else if (justStarted(list, DrowsinessRules::isSteeringFrozen)) addEvent(now, 15f, "핸들 조작 없음 10초 이상")
        if (justStarted(list, DrowsinessRules::isDriftWithoutSignal)) addEvent(now, 15f, "방향지시등 없이 한쪽으로 쏠림")
        if (justStarted(list, DrowsinessRules::isSpeedUnstable)) addEvent(now, 10f, "속도 유지 불안정")

        // 4) 사건 감점 회복: 1초에 0.05점 → 10분이면 30점
        eventPenalty = (eventPenalty - RECOVER_PER_TICK).coerceIn(0f, MAX_EVENT_PENALTY)
        while (recentEvents.isNotEmpty() && now - recentEvents.first().first > EVENT_MEMORY_MS)
            recentEvents.removeFirst()

        // 5) 기본 감점 ⑤⑥ (조건이 맞는 동안 계속)
        val reasons = mutableListOf<String>()
        var basePenalty = 0f
        if (DrowsinessRules.isLongDrive(driveStartMs, now)) {
            basePenalty += 10f
            val min = (now - driveStartMs!!) / 60_000
            reasons += "연속 주행 ${min / 60}시간 ${min % 60}분"
        }
        if (DrowsinessRules.isRiskyHour(now)) {
            basePenalty += 15f
            reasons += "졸음 위험 시간대"
        }
        if (conditionRisk > 0f) {
            basePenalty += conditionRisk
            conditionReason?.let { reasons += it }
        }

        // 6) 100에서 감점 → 안전 점수 → 4단계. 무응답으로 올려 둔 단계가 더 높으면 그걸 쓴다.
        val penalty = (basePenalty + eventPenalty).roundToInt().coerceIn(0, ScoreThresholds.FULL)
        val score = ScoreThresholds.FULL - penalty
        val scoreLevel = ScoreThresholds.levelFor(score, lastScoreLevel)
        lastScoreLevel = scoreLevel
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
        note(now, "단계 변경: ${label(prev)} → ${label(next)} (점수 ${_result.value.score})")

        if (next == Level.CAUTION && prev < Level.CAUTION) {
            retryCount = 0
            if (canAsk(now)) askDriver(now)
        }
        if (next >= Level.WARNING && prev < Level.WARNING) {
            note(now, "알람 시작")
            alarm.start()
        }
        if (next == Level.DANGER && prev < Level.DANGER) {
            val last = lastWindowNaviMs
            if (last == null || now - last > WINDOW_NAVI_COOLDOWN_MS) {
                note(now, "창문 열기, 졸음쉼터 안내 요청")
                // 위험 단계 음성은 졸음쉼터 안내 결과 한 문장만: 성공하면 "안내할게요", 실패하면 "실패했어요".
                // 창문 결과는 말하지 않는다 (화면 창문 패널이 열림·닫힘을 보여 준다)
                window.open { ok ->
                    if (!ok) note(clock(), "창문 열기 실패 (음성 안내 없음)")
                }
                navi.guideToRestArea { ok ->
                    if (ok) {
                        note(clock(), "졸음쉼터 안내 시작")
                        voice.say(NAVI_GUIDE)
                    } else {
                        note(clock(), "졸음쉼터 안내 실패")
                        voice.say(NAVI_FAIL_GUIDE)
                    }
                }
                lastWindowNaviMs = now
            } else {
                note(now, "창문·내비는 10분 안에 이미 실행했어 생략")
            }
        }
        if (next < Level.WARNING && prev >= Level.WARNING) {
            note(now, "알람 중지")
            alarm.stop()
        }
    }

    /**
     * 경고 이상 상태에서 P단이 되면 한 번 호출된다: 알람 중지 → 안내 → 이벤트 점수·강제 단계 초기화.
     * (초기화 뒤 이번 틱의 점수는 기본 위험도만 남아 NORMAL이 되므로 다음 틱에 다시 호출되지 않는다)
     */
    private fun releaseOnPark(now: Long) {
        note(now, "정차(P단) → 경고 해제")
        if (asking) {
            voice.stopAll()      // 취소되면 onResult가 오지 않으므로 상태를 직접 초기화한다
            asking = false
        }
        alarm.stop()
        voice.say(PARK_GUIDE)
        eventPenalty = 0f
        forcedLevel = Level.NORMAL
        forcedUntil = 0L
        recentEvents.clear()
    }

    // ── 운전자 질문 ──

    private fun canAsk(now: Long): Boolean {
        val last = lastAskMs
        return !asking && (last == null || now - last >= ASK_COOLDOWN_MS)
    }

    private fun askDriver(now: Long) {
        asking = true
        lastAskMs = now
        note(now, "운전자에게 질문")
        voice.ask(QUESTION) { answer -> onAnswer(answer, clock()) }
    }

    private fun onAnswer(answer: VoiceAnswer, now: Long) {
        asking = false
        val response = DriverResponseClassifier.classify(answer)
        log("운전자 응답: $response ($answer)")   // Logcat에만: 인식된 문장 원문 포함
        note(now, "운전자 응답: ${label(response)}")      // UI 로그: 분류 결과만

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
                note(now, "무응답 → ${label(forcedLevel)} 단계를 3분간 유지")
            }
            DriverResponse.ERROR -> Unit                             // 기기 문제일 수 있어 판단 보류
        }
    }

    /**
     * 디버그 전용: 사건 감점을 직접 늘리거나 줄인다. (실차가 아닌 환경에서 단계 전환을 확인하는 용도)
     * [delta]가 **양수면 감점이 늘어난다** (안전 점수가 내려감). 로그는 안전 점수 변화량으로 남긴다 (예: -30점).
     * 단계 변경과 그에 따른 질문·알람은 다음 틱(1초 안)에 평소처럼 일어난다.
     */
    fun debugAdjustPenalty(delta: Float) {
        val now = clock()
        val before = eventPenalty
        eventPenalty = (eventPenalty + delta).coerceIn(0f, MAX_EVENT_PENALTY)
        val applied = (eventPenalty - before).roundToInt()
        note(now, "디버그 점수 조절 (${signed(-applied)}점)")
    }

    /**
     * 출발 전 컨디션(마지막으로 몇 시간 잤는지)으로 정한 기본 위험도.
     * 연속 주행·위험 시간대(⑤⑥)처럼 시간이 지나도 줄지 않고 매초 점수에 더해진다 → 오늘의 시작 점수가 된다.
     * [points]가 0이면 없앤다 (대답하지 않았거나 건너뜀 → 안전 점수 100에서 시작).
     */
    fun setConditionRisk(points: Int, reason: String) {
        conditionRisk = points.coerceIn(0, MAX_CONDITION_RISK).toFloat()
        conditionReason = "출발 전 컨디션 ($reason)".takeIf { conditionRisk > 0f }
        note(clock(), "출발 전 컨디션: $reason, 시작 감점 ${conditionRisk.roundToInt()}점")
    }

    private fun addEvent(now: Long, penalty: Float, msg: String) {
        eventPenalty = (eventPenalty + penalty).coerceAtMost(MAX_EVENT_PENALTY)
        recentEvents.addLast(now to msg)
        note(now, "$msg (-${penalty.toInt()}점)")
    }

    /** 부호를 항상 붙여 표기: 30 → "+30", -30 → "-30", 0 → "+0" */
    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

    /** Logcat과 UI 로그([logs])에 함께 남긴다. 매초 찍는 점수 로그에는 쓰지 않는다. */
    private fun note(now: Long, message: String) {
        log(message)
        _logs.value = (_logs.value + LogEntry(now, message)).takeLast(MAX_LOGS)
    }

    private fun label(level: Level) = when (level) {
        Level.NORMAL -> "정상"
        Level.CAUTION -> "주의"
        Level.WARNING -> "경고"
        Level.DANGER -> "위험"
    }

    private fun label(response: DriverResponse) = when (response) {
        DriverResponse.ADMITTED -> "졸림 인정"
        DriverResponse.OK -> "괜찮음"
        DriverResponse.UNCLEAR -> "불명확"
        DriverResponse.SLOW -> "느린 응답"
        DriverResponse.NO_ANSWER -> "무응답"
        DriverResponse.ERROR -> "인식 실패(판단 보류)"
    }

    /** 같은 상태가 계속되는 동안 매초 점수가 쌓이지 않도록, 직전 틱에는 거짓이었다가 이번에 참이 된 순간만 true */
    private fun justStarted(list: List<Sample>, rule: (List<Sample>) -> Boolean): Boolean =
        rule(list) && !(list.size > 1 && rule(list.dropLast(1)))

    companion object {
        private const val TAG = "Kkaeu.Checker"
    private const val PARK_CONFIRM_MS = 3_000L                // P단이 이 시간 이어져야 경고를 해제
    private const val NAVI_GUIDE = "가까운 졸음쉼터로 안내할게요."
    private const val NAVI_FAIL_GUIDE = "졸음쉼터 안내에 실패했어요."
    private const val PARK_GUIDE = "잘 쉬고 출발하세요."
    private const val QUESTION = "주행이 평소와 조금 달라요. 괜찮으세요?"

        private const val TICK_MS = 1000L
        private const val MAX_SAMPLES = 600
        private const val MAX_LOGS = 100
        private const val RECOVER_PER_TICK = 0.05f           // 사건 감점이 1초에 회복되는 점수 (10분에 30점)
        private const val MAX_EVENT_PENALTY = 100f
        private const val MAX_CONDITION_RISK = 30            // 컨디션만으로 주의 단계(40점)까지 가지는 않게
        private const val EVENT_MEMORY_MS = 10 * 60_000L

        private const val ASK_COOLDOWN_MS = 5 * 60_000L
        private const val MAX_RETRY = 1
        private const val FORCED_MS = 3 * 60_000L
        private const val WINDOW_NAVI_COOLDOWN_MS = 10 * 60_000L
    }
}
