package com.sleepreaper.kkaeurang.integration

import android.content.Context
import android.util.Log
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.detector.AlarmModule
import com.sleepreaper.kkaeurang.detector.CabinActuator
import com.sleepreaper.kkaeurang.detector.CabinControl
import com.sleepreaper.kkaeurang.detector.DriverResponse
import com.sleepreaper.kkaeurang.detector.DriverResponseClassifier
import com.sleepreaper.kkaeurang.detector.DriverVoice
import com.sleepreaper.kkaeurang.detector.DrowsinessChecker
import com.sleepreaper.kkaeurang.detector.DrowsinessResult
import com.sleepreaper.kkaeurang.detector.DrowsinessRules
import com.sleepreaper.kkaeurang.detector.Level
import com.sleepreaper.kkaeurang.detector.LogEntry
import com.sleepreaper.kkaeurang.detector.NaviModule
import com.sleepreaper.kkaeurang.detector.NoOpNaviModule
import com.sleepreaper.kkaeurang.detector.NoOpWindowModule
import com.sleepreaper.kkaeurang.detector.Sample
import com.sleepreaper.kkaeurang.detector.SignalSource
import com.sleepreaper.kkaeurang.detector.VehicleSignalCollector
import com.sleepreaper.kkaeurang.detector.VoiceAnswer
import com.sleepreaper.kkaeurang.detector.WindowController
import com.sleepreaper.kkaeurang.detector.WindowModule
import com.sleepreaper.kkaeurang.ui.debug.DebugAnswer
import com.sleepreaper.kkaeurang.ui.debug.DriveScenario
import com.sleepreaper.kkaeurang.ui.debug.LiveDebugControls
import com.sleepreaper.kkaeurang.ui.debug.LiveDebugState
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.model.LifetimeStats
import com.sleepreaper.kkaeurang.ui.model.ScoreChange
import com.sleepreaper.kkaeurang.ui.model.TripReport
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions
import com.sleepreaper.kkaeurang.ui.state.KkaeuStateSource
import com.sleepreaper.kkaeurang.voice.VoiceModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 실제 모듈을 UI에 연결하는 상태 공급자. (FakeKkaeuStateSource의 실제 버전)
 *
 * - 판단 엔진 [DrowsinessChecker]의 점수·단계·근거·차량 샘플·사건 로그 → [DrowsinessUiState]
 * - 엔진이 부르는 음성/알람/창문/내비 인터페이스를 얇게 감싸서, 호출될 때 UI 상태도 같이 바꾼다
 *   (팀원 코드는 고치지 않고 이 파일에서만 연결한다)
 * - 포인트 적립, 연속·총 주행 시간, 주행 리포트, 안전 점수 기록(그래프), 누적 주행 기록은 엔진에 없으므로 여기서 계산한다
 *
 * 모든 호출과 상태 변경은 메인 스레드에서 일어난다. ([scope]는 메인 디스패처여야 한다)
 */
class KkaeuLiveSource(
    context: Context,
    private val voiceModule: VoiceModule,
    private val scope: CoroutineScope,
    signalSource: SignalSource = VehicleSignalCollector(context),
    /** 창문·찬바람 제어. null이면 창문을 열 수 없어 엔진이 음성으로 대체 안내한다 */
    private val cabin: CabinControl? = null,
    private val navi: NaviModule = NoOpNaviModule(),
) : KkaeuStateSource, KkaeuActions, LiveDebugControls {

    private val appContext = context.applicationContext

    // ── 졸음 대응 창문·찬바람 (cabin이 있을 때만) ──
    private val windowController = WindowController()
    private val cabinActuator = cabin?.let { c ->
        CabinActuator(c, clock = ::now, log = { Log.d(CABIN_TAG, it) }).also { it.onEvent = ::onCabinEvent }
    }
    private var lastCabinDecision = WindowController.Decision.CLOSED
    private var windowFailed = false      // 이번에 열려던 창문이 실패함 (닫힘 판단이 나오면 초기화)
    private var coolAirFailed = false

    /** 가짜 차량 신호면 디버그 패널에서 시나리오·기어를 조작할 수 있다 */
    private val simulated = signalSource as? SimulatedSignalSource

    /** 엔진과 이 클래스가 함께 쓰는 시계. 디버그 패널의 "시간 이동"은 [clockOffsetMs]만 바꾼다 */
    private var clockOffsetMs = 0L
    private fun now() = System.currentTimeMillis() + clockOffsetMs

    private val _liveDebugState = MutableStateFlow(LiveDebugState(simulated = simulated != null))
    override val liveDebugState: StateFlow<LiveDebugState> = _liveDebugState.asStateFlow()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 누적 주행 기록 (모든 주행 합계, 앱을 꺼도 남는다) ──
    private var lifeDriveMs = prefs.getLong(KEY_LIFE_DRIVE_MS, 0L)
    private var lifeDistanceKm = prefs.getFloat(KEY_LIFE_DISTANCE_KM, 0f)
    private var lifeScoreSum = prefs.getFloat(KEY_LIFE_SCORE_SUM, 0f)      // 안전 점수 × 초
    private var lifeScoreSec = prefs.getFloat(KEY_LIFE_SCORE_SEC, 0f)
    private var lifeSavedAtMs = 0L

    private val _state = MutableStateFlow(
        DrowsinessUiState.Initial.copy(
            totalPoints = prefs.getInt(KEY_TOTAL_POINTS, 0),
            warningSoundOn = prefs.getBoolean(KEY_WARNING_SOUND, true),
            isParked = true,
            lifetime = lifetimeStats(),
        )
    )
    override val state: StateFlow<DrowsinessUiState> = _state.asStateFlow()

    private val driverVoice = LiveDriverVoice()
    private val alarm = LiveAlarm()

    val checker = DrowsinessChecker(
        source = signalSource,
        voice = driverVoice,
        alarm = alarm,
        window = LiveWindow(),
        navi = LiveNavi(),
        clock = ::now,
    )

    // ── 주행 집계 (이번 주행 = P단에서 출발해 다시 P단이 될 때까지) ──
    private var lastTickMs: Long? = null
    private var driveStartMs: Long? = null      // 연속 주행 시작 (P단이면 초기화 — 엔진과 같은 기준)
    private var tripDriveMs = 0L
    private var awakeMs = 0L                    // NORMAL 단계로 적립된 시간
    private var pointsAtTripStart = _state.value.totalPoints
    private var maxLevel = DrowsyLevel.NORMAL
    private var eventCount = 0
    private var restCount = 0
    private val responseTimesMs = mutableListOf<Long>()

    private var resumedUntilMs = 0L
    private var lastHistoryMs: Long? = null     // 안전 점수 기록(그래프)에 마지막으로 점을 찍은 시각
    private var forcedUntilMs = 0L
    private var vehicleEventUntilMs = 0L
    private var lastLogSeen: LogEntry? = null

    private var collectJobs: List<Job> = emptyList()
    private var started = false

    init {
        voiceModule.setSilenced(!_state.value.warningSoundOn)
    }

    /** 차량 권한을 받은 뒤 호출한다. 실패하면 예외가 그대로 올라간다 */
    fun start() {
        if (started) return
        cabin?.start()
        checker.start()
        started = true
        collectJobs = listOf(
            scope.launch { checker.result.collect { onEngineTick(it, checker.sample.value) } },
            scope.launch { checker.logs.collect { onEngineLogs(it) } },
        )
    }

    fun stop() {
        collectJobs.forEach { it.cancel() }
        collectJobs = emptyList()
        if (started) {
            checker.stop()
            // 감시를 끝낼 때 우리가 연 창문·켠 찬바람은 원래대로 되돌린다
            cabinActuator?.apply(WindowController.Decision.CLOSED, isPark = false)
            cabin?.stop()
        }
        started = false
    }

    // ───────────────────────── 엔진 → UI ─────────────────────────

    private fun onEngineTick(result: DrowsinessResult, sample: Sample) {
        val now = result.timestamp
        val level = result.level.toUi()
        val parked = sample.isPark
        val dt = lastTickMs?.let { (now - it).coerceIn(0L, MAX_TICK_GAP_MS) } ?: 0L
        lastTickMs = now

        val prev = _state.value

        // P단 진입 → 주행 리포트, P단 해제 → 새 주행 시작
        if (parked && !prev.isParked) onParked()
        if (!parked && prev.isParked) onDriveStarted()
        onCabinTick(result, sample, now)

        driveStartMs = if (parked) null else (driveStartMs ?: now)
        if (!parked) tripDriveMs += dt
        if (level > maxLevel && !parked) maxLevel = level

        val accrual = nextAccrualState(prev.accrualState, prev.level, level, now)
        val accruing = !parked && level == DrowsyLevel.NORMAL &&
            (accrual == AccrualState.ACCRUING || accrual == AccrualState.RESUMED)
        if (accruing) awakeMs += dt

        val safety = DrowsyThresholds.safetyOf(result.score.toFloat())
        val history = if (!parked && (lastHistoryMs?.let { now - it >= HISTORY_EVERY_MS } != false)) {
            lastHistoryMs = now
            (prev.safetyHistory + safety).takeLast(MAX_HISTORY)
        } else {
            prev.safetyHistory
        }
        if (!parked) accumulateLifetime(dt, sample.speedKmh, safety, now)
        if (parked && !prev.isParked) saveLifetime(now)

        val tripPoints = (awakeMs / POINT_EVERY_MS).toInt()
        val totalPoints = pointsAtTripStart + tripPoints
        if (totalPoints != prev.totalPoints) prefs.edit().putInt(KEY_TOTAL_POINTS, totalPoints).apply()

        _state.update {
            it.copy(
                score = result.score.toFloat(),
                level = level,
                reasons = eventReasons(result.reasons),
                continuousDriveMin = driveStartMs?.let { start -> ((now - start) / 60_000).toInt() } ?: 0,
                isRiskTimeZone = DrowsinessRules.isRiskyHour(now),
                forcedWarningRemainSec = ((forcedUntilMs - now) / 1000).toInt().takeIf { sec -> sec > 0 },
                vehicleEvent = it.vehicleEvent?.takeIf { now < vehicleEventUntilMs },
                totalPoints = totalPoints,
                tripPoints = tripPoints,
                awakeMinutes = (awakeMs / 60_000).toInt(),
                tripDriveMinutes = (tripDriveMs / 60_000).toInt(),
                accrualState = accrual,
                isParked = parked,
                safetyHistory = history,
                lifetime = lifetimeStats(),
            )
        }

        _liveDebugState.update {
            it.copy(
                score = result.score,
                level = level,
                speedKmh = sample.speedKmh,
                steeringDeg = sample.steeringDeg,
                turnSignalOn = sample.turnSignalOn,
                brakeOn = sample.brakeOn,
                isPark = sample.isPark,
                clockText = CLOCK_FORMAT.format(Date(now)),
                clockOffsetMin = clockOffsetMs / 60_000,
                isAsking = driverVoice.isAsking,
                scenario = simulated?.runningScenario,
            )
        }
    }

    /** 주행 중인 [dt] 동안의 시간·거리·안전 점수를 누적 기록에 더한다 (10초마다 저장) */
    private fun accumulateLifetime(dt: Long, speedKmh: Float, safety: Int, now: Long) {
        if (dt <= 0L) return
        lifeDriveMs += dt
        lifeDistanceKm += speedKmh.coerceAtLeast(0f) * dt / 3_600_000f
        val sec = dt / 1000f
        lifeScoreSum += safety * sec
        lifeScoreSec += sec
        if (now - lifeSavedAtMs >= LIFETIME_SAVE_EVERY_MS) saveLifetime(now)
    }

    private fun saveLifetime(now: Long) {
        lifeSavedAtMs = now
        prefs.edit()
            .putLong(KEY_LIFE_DRIVE_MS, lifeDriveMs)
            .putFloat(KEY_LIFE_DISTANCE_KM, lifeDistanceKm)
            .putFloat(KEY_LIFE_SCORE_SUM, lifeScoreSum)
            .putFloat(KEY_LIFE_SCORE_SEC, lifeScoreSec)
            .apply()
    }

    private fun lifetimeStats() = LifetimeStats(
        totalDriveMinutes = (lifeDriveMs / 60_000).toInt(),
        totalDistanceKm = lifeDistanceKm,
        averageSafetyScore = if (lifeScoreSec >= 1f) (lifeScoreSum / lifeScoreSec).roundToInt() else null,
    )

    private fun nextAccrualState(cur: AccrualState, prevLevel: DrowsyLevel, level: DrowsyLevel, now: Long) = when {
        level >= DrowsyLevel.WARNING -> AccrualState.HIDDEN
        level == DrowsyLevel.CAUTION -> AccrualState.PAUSED
        prevLevel != DrowsyLevel.NORMAL || cur == AccrualState.PAUSED || cur == AccrualState.HIDDEN -> {
            resumedUntilMs = now + RESUMED_SHOW_MS
            AccrualState.RESUMED
        }
        cur == AccrualState.RESUMED && now < resumedUntilMs -> AccrualState.RESUMED
        else -> AccrualState.ACCRUING
    }

    /** 연속 주행·위험 시간대는 화면에 칩/배지로 따로 보이므로 근거 목록에서는 뺀다. 최신 근거가 위로 */
    private fun eventReasons(reasons: List<String>) =
        reasons.filterNot { it.startsWith(REASON_LONG_DRIVE) || it == REASON_RISKY_HOUR }.reversed()

    /** 엔진 사건 로그에서 "○○ (+N점)" 줄을 찾아 "마지막 점수 로그"와 리포트 이벤트 수에 반영 */
    private fun onEngineLogs(logs: List<LogEntry>) {
        val from = lastLogSeen?.let { seen -> logs.lastIndexOf(seen) + 1 } ?: 0
        lastLogSeen = logs.lastOrNull()
        for (entry in logs.drop(from)) {
            val m = SCORE_LOG.matchEntire(entry.message) ?: continue
            if (!entry.message.startsWith(DEBUG_LOG_PREFIX)) eventCount++
            _state.update { it.copy(lastScoreChange = ScoreChange(m.groupValues[1], m.groupValues[2].toInt())) }
        }
        _liveDebugState.update { s ->
            s.copy(logs = logs.takeLast(DEBUG_LOG_LINES).reversed().map { "${LOG_TIME_FORMAT.format(Date(it.time))}  ${it.message}" })
        }
    }

    private fun onParked() {
        // 잠깐 P단을 거친 것(1분 미만 주행)은 리포트를 띄우지 않는다
        if (tripDriveMs < 60_000) return
        val report = TripReport(
            driveMinutes = (tripDriveMs / 60_000).toInt(),
            awakeMinutes = (awakeMs / 60_000).toInt(),
            earnedPoints = (awakeMs / POINT_EVERY_MS).toInt(),
            avgResponseMs = responseTimesMs.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            maxLevel = maxLevel,
            eventCount = eventCount,
            restCount = restCount,
        )
        Log.d(TAG, "주행 종료 → 리포트 $report")
        _state.update { it.copy(tripReport = report, vehicleEvent = null, restStop = null) }
    }

    private fun onDriveStarted() {
        pointsAtTripStart = _state.value.totalPoints
        tripDriveMs = 0L
        awakeMs = 0L
        maxLevel = DrowsyLevel.NORMAL
        eventCount = 0
        restCount = 0
        responseTimesMs.clear()
        lastHistoryMs = null
        _state.update { it.copy(tripReport = null, lastScoreChange = null, safetyHistory = emptyList()) }
    }

    // ───────────────────────── 엔진이 부르는 모듈 (UI 상태도 함께 갱신) ─────────────────────────

    /** 엔진의 운전자 질문. VoiceModule.askAndListen을 직접 써서 "듣는 중" 시점까지 UI에 보여 준다 */
    private inner class LiveDriverVoice : DriverVoice {
        private var pending: ((VoiceAnswer) -> Unit)? = null
        private var listeningAt = 0L
        private var resetJob: Job? = null

        val isAsking get() = pending != null

        override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) {
            manualAsking = false   // 엔진 질문이 "음성으로 답하기"를 덮어쓴다 (그쪽 콜백은 오지 않음)
            pending = onResult
            resetJob?.cancel()
            listeningAt = 0L
            _state.update { it.copy(voiceState = VoiceState.ASKING, voiceQuestion = question, recognizedText = null) }

            voiceModule.askAndListen(
                question,
                onListening = {
                    listeningAt = now()
                    _state.update { it.copy(voiceState = VoiceState.LISTENING) }
                },
            ) { r ->
                if (pending !== onResult) return@askAndListen
                r.speechStartTimeMs?.takeIf { r.text != null }?.let { responseTimesMs += it }
                deliver(VoiceAnswer(r.text, r.isTimeout, r.speechStartTimeMs), shownText = r.text)
            }
        }

        override fun stopAll() {
            pending = null
            manualAsking = false
            voiceModule.stopAll()
            showThenIdle(null)
        }

        /** 엔진의 안내 문구(위험 단계, 정차 해제, 창문·내비 실패 대체). 듣지 않고 말만 한다 */
        override fun say(text: String) = voiceModule.speak(text)

        /**
         * 음성 대신 들어온 대답(화면 "괜찮아요" 버튼, 디버그 패널): 진행 중인 질문을 끊고 [answer]로 엔진에 넘긴다.
         * 질문 중이 아니면 false
         */
        fun answerInstead(answer: VoiceAnswer): Boolean {
            if (pending == null) return false
            voiceModule.stopAll()   // 진행 중 질문의 콜백은 버려진다
            if (listeningAt > 0 && answer.text != null) responseTimesMs += now() - listeningAt
            deliver(answer, shownText = answer.text)
            return true
        }

        /**
         * 음성 경고를 시작하기 직전에 부른다. 경고 문구(TTS)가 질문 음성을 덮어쓰면 질문 결과가 영영 오지 않아
         * 엔진이 '질문 중'에 갇히고 이후 다시 묻지 않게 되므로, 질문을 끊고 엔진에는 인식 실패(판단 보류)로 알린다.
         */
        fun cancelForAlarm() {
            val callback = pending ?: return
            pending = null
            resetJob?.cancel()
            Log.d(TAG, "음성 경고 시작 → 진행 중인 질문 취소 (엔진에는 인식 실패로 전달)")
            voiceModule.stopAll()   // 경고 시작 전이라 경고에는 영향 없음
            _state.update { it.copy(voiceState = VoiceState.IDLE, recognizedText = null) }
            callback(VoiceAnswer(text = null, isTimeout = false, speechStartTimeMs = null))
        }

        private fun deliver(answer: VoiceAnswer, shownText: String?) {
            val callback = pending ?: return
            pending = null
            val response = DriverResponseClassifier.classify(answer)
            if (response == DriverResponse.NO_ANSWER) {
                // 엔진은 무응답이면 WARNING 이상을 3분간 유지한다. 남은 시간 표시용으로 같은 기준을 둔다
                forcedUntilMs = now() + FORCED_MS
            }
            showThenIdle(
                when (response) {
                    DriverResponse.NO_ANSWER -> VoiceState.NO_RESPONSE
                    DriverResponse.ERROR -> VoiceState.FAILED
                    else -> if (shownText != null) VoiceState.RESULT else VoiceState.NO_RESPONSE
                },
                shownText,
            )
            // UI를 먼저 바꾼 뒤 엔진에 넘긴다 (엔진이 곧바로 재질문하면 ASKING으로 다시 바뀐다)
            callback(answer)
        }

        /** 결과를 잠깐 보여 준 뒤 IDLE로 되돌린다 (그 사이 새 질문이 시작되면 건드리지 않음) */
        fun showThenIdle(shown: VoiceState?, text: String? = null) {
            resetJob?.cancel()
            if (shown == null) {
                _state.update { if (it.voiceState == VoiceState.ALARM) it else it.copy(voiceState = VoiceState.IDLE, recognizedText = null) }
                return
            }
            _state.update { it.copy(voiceState = shown, recognizedText = text) }
            resetJob = scope.launch {
                delay(RESULT_SHOW_MS)
                _state.update { if (it.voiceState == shown) it.copy(voiceState = VoiceState.IDLE, recognizedText = null) else it }
            }
        }
    }

    /** 엔진의 알람 = VoiceModule 음성 경고. 경고가 실제로 울리는 동안만 UI에 ALARM으로 보인다 */
    private inner class LiveAlarm : AlarmModule {
        override fun start() {
            driverVoice.cancelForAlarm()
            voiceModule.startAlarm { reason ->
                Log.d(TAG, "음성 경고 종료: $reason")
                _state.update {
                    it.copy(
                        isAlarmRunning = false,
                        voiceState = if (it.voiceState == VoiceState.ALARM) VoiceState.IDLE else it.voiceState,
                    )
                }
            }
            if (voiceModule.isAlarmRunning) {
                _state.update { it.copy(isAlarmRunning = true, voiceState = VoiceState.ALARM, recognizedText = null) }
            }
        }

        override fun stop() = voiceModule.stopAlarm()
    }

    /**
     * 엔진이 DANGER 진입 때 부르는 창문 열기.
     * - [cabin]이 없으면 실제로 열 수 없으므로 실패로 알려, 엔진이 "창문을 직접 열어 주세요" 음성 안내를 하게 한다.
     * - [cabin]이 있으면 실제 열고 닫기는 매초 도는 [onCabinTick]이 점수를 보고 한다. 여기서는 받아들였다고만 알리고,
     *   진짜 실패는 [onCabinEvent]가 음성으로 대체 안내한다.
     */
    private inner class LiveWindow : WindowModule {
        override fun open() = open { }

        override fun open(onResult: (Boolean) -> Unit) {
            if (cabinActuator == null) {
                NoOpWindowModule().open(onResult)
                return
            }
            Log.d(CABIN_TAG, "엔진 창문 열기 요청 → 점수 기반 창문 조절이 처리")
            onResult(true)
        }
    }

    // ───────────────────────── 창문·찬바람 (매초) ─────────────────────────

    /** 엔진 결과가 나올 때마다 창문·찬바람을 판단하고 실행한 뒤, 화면 상태(창문 열림·외기·안내 문구)를 맞춘다 */
    private fun onCabinTick(result: DrowsinessResult, sample: Sample, now: Long) {
        val c = cabin ?: return
        val actuator = cabinActuator ?: return
        val decision = windowController.update(
            WindowController.Input(now, result.score, result.level, sample.speedKmh, sample.isPark, c.isRaining)
        )
        val prev = lastCabinDecision
        lastCabinDecision = decision
        // 목표가 바뀌면 새로 시도하므로 이전 실패 표시를 지운다 (실패 콜백은 apply 안에서 바로 올 수도 있다)
        if (decision.windowPercent != prev.windowPercent) windowFailed = false
        if (decision.coolAir != prev.coolAir) coolAirFailed = false
        actuator.apply(decision, sample.isPark)

        val message = when {
            decision.blockedByRain && !prev.blockedByRain && decision.coolAir ->
                VehicleControlEvent(
                    message = appContext.getString(R.string.live_window_try),
                    canCancel = true,
                    blockedReason = appContext.getString(R.string.live_window_blocked_rain),
                )
            decision.windowPercent > 0 && prev.windowPercent == 0 && !actuator.driverOverride && !windowFailed ->
                VehicleControlEvent(appContext.getString(R.string.live_window_opened), canCancel = true, blockedReason = null)
            decision.windowPercent == 0 && !decision.coolAir && (prev.windowPercent > 0 || prev.coolAir) && !sample.isPark ->
                VehicleControlEvent(appContext.getString(R.string.live_window_closed), canCancel = false, blockedReason = null)
            else -> null
        }
        if (message != null) vehicleEventUntilMs = now + VEHICLE_EVENT_SHOW_MS

        _state.update {
            it.copy(
                windowOpen = decision.windowPercent > 0 && !windowFailed && !actuator.driverOverride,
                outsideAirOn = decision.coolAir && !coolAirFailed,
                vehicleEvent = message ?: it.vehicleEvent,
            )
        }
    }

    private fun onCabinEvent(event: CabinActuator.Event) {
        when (event) {
            CabinActuator.Event.WINDOW_FAILED -> {
                windowFailed = true
                Log.w(CABIN_TAG, "창문 제어 실패 → 음성 대체 안내")
                driverVoice.say(appContext.getString(R.string.live_window_failed_voice))
                // 실제 차에서는 실패가 늦게 올 수 있으므로, 이미 띄운 "창문을 열었어요"를 거둔다
                vehicleEventUntilMs = 0L
                _state.update { it.copy(windowOpen = false, vehicleEvent = null) }
            }
            CabinActuator.Event.COOL_AIR_FAILED -> {
                coolAirFailed = true
                Log.w(CABIN_TAG, "찬바람 제어 실패")
                _state.update { it.copy(outsideAirOn = false) }
            }
            CabinActuator.Event.DRIVER_OVERRIDE -> {
                Log.d(CABIN_TAG, "운전자가 창문을 직접 조작 → 이번 주행 동안 창문 자동 조절 중지")
                _state.update { it.copy(windowOpen = false) }
            }
        }
    }

    /**
     * 엔진의 졸음쉼터 안내.
     * TODO(integration): 졸음쉼터 찾기(RestStopFinder) 코드가 저장소에 올라오면, 여기서 가장 가까운 쉼터를
     *  찾아 DrowsinessUiState.restStop(RestStopInfo)을 채우고 onAddRestStopWaypoint에서 NaviHelper 경유지 추가를 부른다.
     */
    private inner class LiveNavi : NaviModule {
        override fun guideToRestArea() = navi.guideToRestArea()

        override fun guideToRestArea(onResult: (Boolean) -> Unit) = navi.guideToRestArea(onResult)
    }

    // ───────────────────────── KkaeuActions (화면 → 모듈) ─────────────────────────

    override fun onAnswerOkay() {
        val text = appContext.getString(R.string.btn_okay)
        if (driverVoice.answerInstead(VoiceAnswer(text = text, isTimeout = false, speechStartTimeMs = null))) {
            Log.d(TAG, "괜찮아요 버튼 → 진행 중인 질문에 '괜찮음'으로 응답")
        } else if (voiceModule.isAlarmRunning) {
            // 질문 중이 아닐 때(경고 중) 누르면 운전자가 경고를 확인한 것으로 보고 경고음만 끈다.
            // 점수·단계는 엔진이 정하므로 건드리지 않는다
            Log.d(TAG, "괜찮아요 버튼 → 음성 경고 중지")
            voiceModule.stopAlarm()
        }
    }

    override fun onAddRestStopWaypoint() {
        // 지금은 restStop을 채우는 코드가 없어 카드가 뜨지 않으므로 여기까지 오지 않는다 (LiveNavi 주석 참고)
        val stop = _state.value.restStop ?: return
        if (!stop.isWaypointAdded) restCount++
        navi.guideToRestArea()
        _state.update { it.copy(restStop = stop.copy(isWaypointAdded = true)) }
    }

    override fun onDismissRestStop() {
        _state.update { it.copy(restStop = null) }
    }

    /**
     * 운전자가 "창문 되돌리기"를 누름: 창문·찬바람을 바로 원래대로 되돌리고 10분 동안 자동으로 다시 열지 않는다.
     * (P단이 되면 풀린다. 졸음 경고·질문 같은 엔진 동작은 그대로다)
     */
    override fun onCancelVehicleControl() {
        val actuator = cabinActuator
        if (actuator == null) {
            Log.w(TAG, "차량 제어 되돌리기 요청 (창문·공조 제어 없음)")
        } else {
            Log.d(CABIN_TAG, "운전자가 되돌리기를 누름 → 창문·찬바람 원래대로, 10분 동안 자동으로 열지 않음")
            windowController.suppress(now())
            lastCabinDecision = WindowController.Decision.CLOSED
            actuator.apply(WindowController.Decision.CLOSED, isPark = false)
        }
        vehicleEventUntilMs = 0L
        _state.update { it.copy(vehicleEvent = null, windowOpen = false, outsideAirOn = false) }
    }

    override fun onCloseTripReport() {
        _state.update { it.copy(tripReport = null) }
    }

    /** "음성으로 답하기"로 시작한 질문이 진행 중인지 (엔진 질문과는 별개) */
    private var manualAsking = false

    override fun onVoiceAnswer() {
        if (driverVoice.isAsking || manualAsking) return
        if (_state.value.level < DrowsyLevel.WARNING) return
        manualAsking = true
        val prompt = appContext.getString(R.string.warning_voice_prompt)
        var listeningAt = 0L
        Log.d(TAG, "음성으로 답하기 → 경고 멈추고 질문")
        // askAndListen이 진행 중인 경고를 먼저 멈춘다. 경고 종료 콜백이 ASKING을 덮지 않도록 상태를 먼저 바꾼다
        _state.update { it.copy(voiceState = VoiceState.ASKING, voiceQuestion = prompt, recognizedText = null) }
        voiceModule.askAndListen(
            prompt,
            onListening = {
                listeningAt = now()
                _state.update { it.copy(voiceState = VoiceState.LISTENING) }
            },
        ) { r ->
            if (!manualAsking) return@askAndListen
            manualAsking = false
            val response = DriverResponseClassifier.classify(VoiceAnswer(r.text, r.isTimeout, r.speechStartTimeMs))
            Log.d(TAG, "음성으로 답하기 결과: $response")
            if (r.text != null && listeningAt > 0) r.speechStartTimeMs?.let { responseTimesMs += it }
            driverVoice.showThenIdle(
                when (response) {
                    DriverResponse.NO_ANSWER -> VoiceState.NO_RESPONSE
                    DriverResponse.ERROR -> VoiceState.FAILED
                    else -> if (r.text != null) VoiceState.RESULT else VoiceState.NO_RESPONSE
                },
                r.text,
            )
            // 괜찮다고 하면 경고음을 끈 채로 둔다. 아니면(졸림·무응답·불명확) 아직 경고 단계일 때 다시 울린다
            // TODO(integration): 판단 엔진에 이 대답을 넘기는 공개 함수가 생기면 연결 (지금은 경고음만 다룬다)
            if (response != DriverResponse.OK && _state.value.level >= DrowsyLevel.WARNING) alarm.start()
        }
    }

    override fun onFindRestStop() {
        val stop = _state.value.restStop
        if (stop != null && !stop.isWaypointAdded) {
            onAddRestStopWaypoint()
        } else {
            Log.d(TAG, "근처 쉼터 찾기 → 내비에 쉼터 안내 요청")
            navi.guideToRestArea()
        }
    }

    override fun onToggleWarningSound() {
        val on = !_state.value.warningSoundOn
        voiceModule.setSilenced(!on)   // 끄면 진행 중인 경고도 즉시 멈춘다
        prefs.edit().putBoolean(KEY_WARNING_SOUND, on).apply()
        _state.update { it.copy(warningSoundOn = on) }
    }

    // ───────────────────────── LiveDebugControls (디버그 패널) ─────────────────────────

    override fun adjustScore(delta: Int) = checker.debugAdjustEventScore(delta.toFloat())

    override fun advanceTime(minutes: Int) {
        clockOffsetMs += minutes * 60_000L
        Log.d(TAG, "디버그: 시계 +${minutes}분 (누적 ${clockOffsetMs / 60_000}분)")
    }

    override fun jumpToRiskTime() {
        val now = now()
        if (DrowsinessRules.isRiskyHour(now)) return   // 이미 위험 시간대면 그대로
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // 오늘 02시 → 오늘 14시 → 내일 02시 중 지금 이후 첫 번째
        val target = listOf(0 to RISK_START_AM, 0 to RISK_START_PM, 1 to RISK_START_AM)
            .map { (day, hour) ->
                (today.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                }.timeInMillis
            }
            .first { it > now }
        clockOffsetMs += target - now
        Log.d(TAG, "디버그: 위험 시간대로 이동 → ${CLOCK_FORMAT.format(Date(now()))}")
    }

    override fun answer(answer: DebugAnswer) {
        val voiceAnswer = when (answer) {
            DebugAnswer.OKAY -> VoiceAnswer(DEBUG_TEXT_OKAY, isTimeout = false, speechStartTimeMs = null)
            DebugAnswer.SLEEPY -> VoiceAnswer(DEBUG_TEXT_SLEEPY, isTimeout = false, speechStartTimeMs = null)
            DebugAnswer.NO_ANSWER -> VoiceAnswer(null, isTimeout = true, speechStartTimeMs = null)
        }
        if (!driverVoice.answerInstead(voiceAnswer)) Log.d(TAG, "디버그: 질문 중이 아니라 대답 무시")
    }

    override fun runScenario(scenario: DriveScenario) {
        simulated?.run(scenario)
    }

    override fun setParked(parked: Boolean) {
        simulated?.setParked(parked)
    }

    override fun setTurnSignal(on: Boolean) {
        simulated?.turnSignalOn = on
    }

    override fun setBrake(on: Boolean) {
        simulated?.brakeOn = on
    }

    private fun Level.toUi() = when (this) {
        Level.NORMAL -> DrowsyLevel.NORMAL
        Level.CAUTION -> DrowsyLevel.CAUTION
        Level.WARNING -> DrowsyLevel.WARNING
        Level.DANGER -> DrowsyLevel.CRITICAL
    }

    private companion object {
        const val TAG = "Kkaeu.Live"
        const val CABIN_TAG = "Kkaeu.Cabin"
        const val PREFS = "kkaeurang"
        const val KEY_TOTAL_POINTS = "total_points"
        const val KEY_WARNING_SOUND = "warning_sound_on"
        const val KEY_LIFE_DRIVE_MS = "life_drive_ms"
        const val KEY_LIFE_DISTANCE_KM = "life_distance_km"
        const val KEY_LIFE_SCORE_SUM = "life_score_sum"
        const val KEY_LIFE_SCORE_SEC = "life_score_sec"

        const val POINT_EVERY_MS = 60_000L           // NORMAL로 1분 주행할 때마다 1P
        const val MAX_TICK_GAP_MS = 5_000L           // 앱이 멈췄다 돌아온 시간은 주행 시간에 넣지 않음
        const val RESUMED_SHOW_MS = 3_000L
        const val RESULT_SHOW_MS = 2_000L
        const val VEHICLE_EVENT_SHOW_MS = 5_000L
        const val FORCED_MS = 3 * 60_000L            // DrowsinessChecker.FORCED_MS와 같은 값
        const val HISTORY_EVERY_MS = 30_000L         // 안전 점수 그래프에 30초마다 점 하나
        const val MAX_HISTORY = 240                  // 최근 2시간
        const val LIFETIME_SAVE_EVERY_MS = 10_000L

        // DrowsinessChecker가 만드는 문장 형식 (엔진 문구가 바뀌면 여기도 맞춘다)
        const val REASON_LONG_DRIVE = "연속 주행"
        const val REASON_RISKY_HOUR = "졸음 위험 시간대"
        val SCORE_LOG = Regex("""^(.+) \(([+-]\d+)점\)$""")
        const val DEBUG_LOG_PREFIX = "디버그 "   // DrowsinessChecker.debugAdjustEventScore 로그

        // 디버그 패널
        const val DEBUG_LOG_LINES = 12
        const val DEBUG_TEXT_OKAY = "괜찮아"
        const val DEBUG_TEXT_SLEEPY = "졸려"
        const val RISK_START_AM = 2     // DrowsinessRules.isRiskyHour: 02~04시, 14~15시
        const val RISK_START_PM = 14
        val CLOCK_FORMAT = SimpleDateFormat("M/d HH:mm:ss", Locale.KOREA)
        val LOG_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
    }
}
