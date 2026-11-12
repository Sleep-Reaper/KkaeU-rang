package com.sleepreaper.kkaeurang.integration

import android.content.Context
import android.util.Log
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.detector.AlarmModule
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
import com.sleepreaper.kkaeurang.detector.WindowModule
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
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

/**
 * 실제 모듈을 UI에 연결하는 상태 공급자. (FakeKkaeuStateSource의 실제 버전)
 *
 * - 판단 엔진 [DrowsinessChecker]의 점수·단계·근거·차량 샘플·사건 로그 → [DrowsinessUiState]
 * - 엔진이 부르는 음성/알람/창문/내비 인터페이스를 얇게 감싸서, 호출될 때 UI 상태도 같이 바꾼다
 *   (팀원 코드는 고치지 않고 이 파일에서만 연결한다)
 * - 포인트 적립, 연속·총 주행 시간, 주행 리포트는 엔진에 없으므로 여기서 계산한다
 *
 * 모든 호출과 상태 변경은 메인 스레드에서 일어난다. ([scope]는 메인 디스패처여야 한다)
 */
class KkaeuLiveSource(
    context: Context,
    private val voiceModule: VoiceModule,
    private val scope: CoroutineScope,
    signalSource: SignalSource = VehicleSignalCollector(context),
    private val window: WindowModule = NoOpWindowModule(),
    private val navi: NaviModule = NoOpNaviModule(),
) : KkaeuStateSource, KkaeuActions {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        DrowsinessUiState.Initial.copy(
            totalPoints = prefs.getInt(KEY_TOTAL_POINTS, 0),
            warningSoundOn = prefs.getBoolean(KEY_WARNING_SOUND, true),
            isParked = true,
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
        if (started) checker.stop()
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

        driveStartMs = if (parked) null else (driveStartMs ?: now)
        if (!parked) tripDriveMs += dt
        if (level > maxLevel && !parked) maxLevel = level

        val accrual = nextAccrualState(prev.accrualState, prev.level, level, now)
        val accruing = !parked && level == DrowsyLevel.NORMAL &&
            (accrual == AccrualState.ACCRUING || accrual == AccrualState.RESUMED)
        if (accruing) awakeMs += dt

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
            )
        }
    }

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
            eventCount++
            _state.update { it.copy(lastScoreChange = ScoreChange(m.groupValues[1], m.groupValues[2].toInt())) }
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
        _state.update { it.copy(tripReport = null, lastScoreChange = null) }
    }

    // ───────────────────────── 엔진이 부르는 모듈 (UI 상태도 함께 갱신) ─────────────────────────

    /** 엔진의 운전자 질문. VoiceModule.askAndListen을 직접 써서 "듣는 중" 시점까지 UI에 보여 준다 */
    private inner class LiveDriverVoice : DriverVoice {
        private var pending: ((VoiceAnswer) -> Unit)? = null
        private var listeningAt = 0L
        private var resetJob: Job? = null

        override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) {
            pending = onResult
            resetJob?.cancel()
            listeningAt = 0L
            _state.update { it.copy(voiceState = VoiceState.ASKING, voiceQuestion = question, recognizedText = null) }

            voiceModule.askAndListen(
                question,
                onListening = {
                    listeningAt = System.currentTimeMillis()
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
            voiceModule.stopAll()
            showThenIdle(null)
        }

        /** 화면 "괜찮아요" 버튼: 진행 중인 질문을 끊고 '괜찮다'는 대답으로 엔진에 넘긴다 */
        fun answerOkayFromButton(text: String): Boolean {
            if (pending == null) return false
            voiceModule.stopAll()   // 진행 중 질문의 콜백은 버려진다
            if (listeningAt > 0) responseTimesMs += System.currentTimeMillis() - listeningAt
            deliver(VoiceAnswer(text = text, isTimeout = false, speechStartTimeMs = null), shownText = text)
            return true
        }

        private fun deliver(answer: VoiceAnswer, shownText: String?) {
            val callback = pending ?: return
            pending = null
            val response = DriverResponseClassifier.classify(answer)
            if (response == DriverResponse.NO_ANSWER) {
                // 엔진은 무응답이면 WARNING 이상을 3분간 유지한다. 남은 시간 표시용으로 같은 기준을 둔다
                forcedUntilMs = System.currentTimeMillis() + FORCED_MS
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
        private fun showThenIdle(shown: VoiceState?, text: String? = null) {
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
     * 엔진의 창문 열기. 실제 창문 모듈이 붙어 있을 때만 화면에 "창문을 열었어요"를 띄운다
     * (지금 NoOpWindowModule이면 실제로 열리지 않으므로 화면에도 표시하지 않는다)
     */
    private inner class LiveWindow : WindowModule {
        override fun open() {
            window.open()
            if (window is NoOpWindowModule) return
            vehicleEventUntilMs = System.currentTimeMillis() + VEHICLE_EVENT_SHOW_MS
            _state.update {
                it.copy(
                    windowOpen = true,
                    // TODO(integration): 창문 모듈에 되돌리기 기능이 생기면 canCancel = true + onCancelVehicleControl 연결
                    vehicleEvent = VehicleControlEvent(
                        message = appContext.getString(R.string.live_window_opened),
                        canCancel = false,
                        blockedReason = null,
                    ),
                )
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
    }

    // ───────────────────────── KkaeuActions (화면 → 모듈) ─────────────────────────

    override fun onAnswerOkay() {
        val text = appContext.getString(R.string.btn_okay)
        if (driverVoice.answerOkayFromButton(text)) {
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

    override fun onCancelVehicleControl() {
        Log.w(TAG, "차량 제어 되돌리기 요청 (미연결: 창문 모듈 없음)")
        vehicleEventUntilMs = 0L
        _state.update { it.copy(vehicleEvent = null) }
    }

    override fun onCloseTripReport() {
        _state.update { it.copy(tripReport = null) }
    }

    override fun onToggleWarningSound() {
        val on = !_state.value.warningSoundOn
        voiceModule.setSilenced(!on)   // 끄면 진행 중인 경고도 즉시 멈춘다
        prefs.edit().putBoolean(KEY_WARNING_SOUND, on).apply()
        _state.update { it.copy(warningSoundOn = on) }
    }

    private fun Level.toUi() = when (this) {
        Level.NORMAL -> DrowsyLevel.NORMAL
        Level.CAUTION -> DrowsyLevel.CAUTION
        Level.WARNING -> DrowsyLevel.WARNING
        Level.DANGER -> DrowsyLevel.CRITICAL
    }

    private companion object {
        const val TAG = "Kkaeu.Live"
        const val PREFS = "kkaeurang"
        const val KEY_TOTAL_POINTS = "total_points"
        const val KEY_WARNING_SOUND = "warning_sound_on"

        const val POINT_EVERY_MS = 60_000L           // NORMAL로 1분 주행할 때마다 1P
        const val MAX_TICK_GAP_MS = 5_000L           // 앱이 멈췄다 돌아온 시간은 주행 시간에 넣지 않음
        const val RESUMED_SHOW_MS = 3_000L
        const val RESULT_SHOW_MS = 2_000L
        const val VEHICLE_EVENT_SHOW_MS = 5_000L
        const val FORCED_MS = 3 * 60_000L            // DrowsinessChecker.FORCED_MS와 같은 값

        // DrowsinessChecker가 만드는 문장 형식 (엔진 문구가 바뀌면 여기도 맞춘다)
        const val REASON_LONG_DRIVE = "연속 주행"
        const val REASON_RISKY_HOUR = "졸음 위험 시간대"
        val SCORE_LOG = Regex("""^(.+) \(\+(\d+)점\)$""")
    }
}
