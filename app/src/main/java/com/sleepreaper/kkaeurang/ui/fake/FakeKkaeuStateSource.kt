package com.sleepreaper.kkaeurang.ui.fake

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.debug.DebugUiState
import com.sleepreaper.kkaeurang.ui.debug.KkaeuDebugControls
import com.sleepreaper.kkaeurang.ui.debug.ReasonEvent
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.model.AlertCounts
import com.sleepreaper.kkaeurang.ui.model.SleepRange
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.model.LifetimeStats
import com.sleepreaper.kkaeurang.ui.model.RestStopInfo
import com.sleepreaper.kkaeurang.ui.model.RestStopType
import com.sleepreaper.kkaeurang.ui.model.ScoreChange
import com.sleepreaper.kkaeurang.ui.model.SleepCondition
import com.sleepreaper.kkaeurang.ui.model.StartCheckIn
import com.sleepreaper.kkaeurang.ui.model.TripLogEntry
import com.sleepreaper.kkaeurang.ui.model.TripLogKind
import com.sleepreaper.kkaeurang.ui.model.TripReport
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.model.Weather
import com.sleepreaper.kkaeurang.ui.state.AlertDismissal
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions
import com.sleepreaper.kkaeurang.ui.state.KkaeuStateSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 실제 모듈 없이 UI를 돌려 보기 위한 가짜 상태 공급자 (ViewModel + StateFlow).
 *
 * - 1초마다 상태 갱신. 시연용으로 실제 1초 = 주행 1분, NORMAL이면 1분마다 +1P
 * - 포인트는 절대 줄지 않는다 (더하기만 함)
 * - CAUTION 진입 → 적립 PAUSED + 음성 질문, 응답 4초 미만 → RESUMED, WARNING 이상 → HIDDEN
 * - 두 번 물어도 대답이 없으면 WARNING을 3분간 강제로 유지한다
 *
 * 모든 상태 변경은 메인 스레드(viewModelScope)에서만 일어난다.
 *
 * TODO(integration): 실제 판단 엔진·음성·차량 제어·네비 모듈이 준비되면, 같은 인터페이스
 *  (KkaeuStateSource, KkaeuActions)를 구현한 실제 클래스로 MainActivity에서 바꿔 끼운다.
 *  이 클래스의 단계 전환 규칙은 시연용 흉내일 뿐, 실제 판단 로직이 아니다.
 */
class FakeKkaeuStateSource(application: Application) :
    AndroidViewModel(application), KkaeuStateSource, KkaeuActions, KkaeuDebugControls {

    private val _state = MutableStateFlow(drivingStartState(totalPoints = START_TOTAL_POINTS))
    override val state: StateFlow<DrowsinessUiState> = _state.asStateFlow()

    private val _debugState = MutableStateFlow(DebugUiState())
    override val debugState: StateFlow<DebugUiState> = _debugState.asStateFlow()

    // 주행 리포트용 집계
    private var maxLevel = DrowsyLevel.NORMAL
    private var eventCount = 0
    private var restCount = 0
    private val responseTimesMs = mutableListOf<Long>()
    private var alertCounts = AlertCounts()
    private var tripStartMs = System.currentTimeMillis()
    private val tripLogs = ArrayList<TripLogEntry>()
    private val alertDismissal = AlertDismissal()
    private var checkInJob: Job? = null

    // 틱(1초) 단위 타이머
    private var resumedTicksLeft = 0
    private var slowResumeTicksLeft = 0
    private var vehicleEventTicksLeft = 0

    /** 응답 시간을 잴 기준 시각 (질문 시작·듣기 시작·경고 시작 때 갱신) */
    private var responseStartAt = 0L

    private var voiceJob: Job? = null
    private var demoJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
        beginStartCheckIn()
    }

    // ───────────────────────── 상태 갱신 공통 ─────────────────────────

    /** 상태를 바꾸고, 점수로 단계를 다시 계산하고, 단계가 바뀌었으면 그에 따른 처리를 한다 */
    private fun update(transform: (DrowsinessUiState) -> DrowsinessUiState) {
        val prev = _state.value
        var next = transform(prev)
        val score = next.score.coerceIn(0f, 100f)
        next = next.copy(score = score, level = levelFor(score, next.forcedWarningRemainSec))
        if (next.level != prev.level) next = onLevelChanged(prev.level, next)
        if (next.level > maxLevel) maxLevel = next.level
        if (next.level > prev.level) alertCounts = alertCounts.plus(next.level)
        if (next.level != prev.level) {
            val kind = if (next.level > prev.level) TripLogKind.LEVEL_UP else TripLogKind.LEVEL_DOWN
            log(kind, str(R.string.fake_log_level, levelName(prev.level), levelName(next.level), next.safetyScore), level = next.level)
        }
        next = next.copy(alertDismissed = alertDismissal.update(next.level, next.score))
        _state.value = next
    }

    private fun levelFor(score: Float, forcedWarningSec: Int?): DrowsyLevel {
        val byScore = DrowsyThresholds.levelOf(score)
        return if (forcedWarningSec != null && byScore < DrowsyLevel.WARNING) DrowsyLevel.WARNING else byScore
    }

    private fun onLevelChanged(old: DrowsyLevel, s: DrowsinessUiState): DrowsinessUiState {
        val new = s.level
        return when {
            new >= DrowsyLevel.WARNING -> {
                var r = s.copy(accrualState = AccrualState.HIDDEN)
                if (old < DrowsyLevel.WARNING) {
                    voiceJob?.cancel()
                    responseStartAt = now()
                    vehicleEventTicksLeft = VEHICLE_EVENT_TICKS
                    r = r.copy(
                        isAlarmRunning = true,
                        voiceState = VoiceState.ALARM,
                        recognizedText = null,
                        vehicleEvent = windowOpenedEvent(),
                        windowOpen = true,
                    )
                }
                if (new == DrowsyLevel.CRITICAL && r.restStop == null) r = r.copy(restStop = sampleRestStop())
                // 시연에서는 졸음쉼터 안내가 늘 성공한 것으로 본다 (실제는 엔진이 내비 결과로 말한다)
                if (new == DrowsyLevel.CRITICAL && old < DrowsyLevel.CRITICAL) r = r.copy(voiceGuide = str(R.string.danger_guide_navi_ok), restGuideOk = true)
                r
            }

            new == DrowsyLevel.CAUTION -> {
                var r = s.copy(accrualState = AccrualState.PAUSED)
                if (old >= DrowsyLevel.WARNING) {
                    r = r.copy(isAlarmRunning = false, voiceState = r.voiceState.alarmToIdle())
                }
                if (old == DrowsyLevel.NORMAL) {
                    r = r.copy(voiceState = VoiceState.ASKING, voiceQuestion = str(R.string.voice_default_question), recognizedText = null)
                    startAskFlow()
                }
                r
            }

            else -> { // NORMAL
                resumedTicksLeft = RESUMED_SHOW_TICKS
                var r = s.copy(accrualState = AccrualState.RESUMED, reasons = emptyList())
                if (old >= DrowsyLevel.WARNING) {
                    r = r.copy(isAlarmRunning = false, voiceState = r.voiceState.alarmToIdle())
                }
                if (r.voiceState in ASKING_STATES) {
                    voiceJob?.cancel()
                    r = r.copy(voiceState = VoiceState.IDLE)
                }
                r
            }
        }
    }

    private fun VoiceState.alarmToIdle() = if (this == VoiceState.ALARM) VoiceState.IDLE else this

    /** 1초마다: 주행 1분 진행, 포인트 적립, 각종 타이머 */
    private fun tick() = update { s ->
        if (s.isParked) return@update s
        var r = s.copy(
            continuousDriveMin = s.continuousDriveMin + 1,
            tripDriveMinutes = s.tripDriveMinutes + 1,
            tripDistanceKm = s.tripDistanceKm + FAKE_KM_PER_MIN,
        )

        val accruing = r.level == DrowsyLevel.NORMAL &&
            (r.accrualState == AccrualState.ACCRUING || r.accrualState == AccrualState.RESUMED)
        if (accruing) {
            r = r.copy(totalPoints = r.totalPoints + 1, tripPoints = r.tripPoints + 1, awakeMinutes = r.awakeMinutes + 1)
        }

        when (r.accrualState) {
            AccrualState.RESUMED -> if (--resumedTicksLeft <= 0) r = r.copy(accrualState = AccrualState.ACCRUING)
            AccrualState.PAUSED -> if (r.level == DrowsyLevel.NORMAL && --slowResumeTicksLeft <= 0) {
                resumedTicksLeft = RESUMED_SHOW_TICKS
                r = r.copy(accrualState = AccrualState.RESUMED)
            }
            else -> Unit
        }

        if (r.tripDriveMinutes % HISTORY_EVERY_MIN == 0) {
            r = r.copy(safetyHistory = (r.safetyHistory + r.safetyScore).takeLast(MAX_HISTORY))
        }
        r = r.copy(
            lifetime = r.lifetime.copy(
                totalDriveMinutes = r.lifetime.totalDriveMinutes + 1,
                totalDistanceKm = r.lifetime.totalDistanceKm + FAKE_KM_PER_MIN,
            ),
            sleep = r.sleep?.let { it.copy(awakeMinutes = it.awakeMinutes + 1) },
        )

        r.forcedWarningRemainSec?.let { sec -> r = r.copy(forcedWarningRemainSec = (sec - 1).takeIf { it > 0 }) }
        if (r.vehicleEvent != null && --vehicleEventTicksLeft <= 0) r = r.copy(vehicleEvent = null)
        r
    }

    // ───────────────────────── 음성 질문 흉내 ─────────────────────────

    /** 호출 전에 상태가 이미 ASKING이어야 한다. 첫 동작이 delay라서 update 안에서 불러도 안전하다 */
    private fun startAskFlow() {
        voiceJob?.cancel()
        responseStartAt = now()
        voiceJob = viewModelScope.launch {
            for (attempt in 1..ASK_ATTEMPTS) {
                if (attempt > 1) {
                    responseStartAt = now()
                    update { it.copy(voiceState = VoiceState.ASKING) }
                }
                delay(ASK_MS)
                responseStartAt = now()
                update { it.copy(voiceState = VoiceState.LISTENING) }
                delay(LISTEN_MS)
                update { it.copy(voiceState = VoiceState.NO_RESPONSE) }
                delay(NO_RESPONSE_MS)
            }
            // 두 번 다 대답이 없으면 WARNING 강제 유지
            update { it.copy(voiceState = VoiceState.IDLE, forcedWarningRemainSec = FORCED_WARNING_SEC) }
        }
    }

    /** 음성 또는 버튼으로 "괜찮다"는 응답이 들어온 경우 */
    private fun handleAnswer(recognized: String) {
        val before = _state.value
        if (before.isParked) return
        val responseMs = (now() - responseStartAt).coerceAtLeast(0L)
        voiceJob?.cancel()
        // 리포트의 "평균 반응 속도"는 음성 질문에 대한 응답만 센다 (경고 중 버튼 누르기까지의 시간은 제외)
        if (before.voiceState in ASKING_STATES) responseTimesMs += responseMs
        // 졸리지 않다는 대답 → 경보 화면을 닫고 홈 계기판으로
        alertDismissal.dismiss(before.level, before.score)
        log(TripLogKind.ANSWER, str(R.string.fake_log_answer))

        update {
            val halved = floor(it.score / 2f) // 응답하면 점수 절반
            it.copy(
                score = halved,
                lastScoreChange = ScoreChange(str(R.string.fake_log_answer), (halved - it.score).roundToInt()),
                forcedWarningRemainSec = null,
                isAlarmRunning = false,
                voiceState = VoiceState.RESULT,
                recognizedText = recognized,
            )
        }

        val after = _state.value
        val wasNotAccruing = before.level != DrowsyLevel.NORMAL || before.accrualState == AccrualState.PAUSED
        if (after.level == DrowsyLevel.NORMAL && wasNotAccruing) {
            if (responseMs < FAST_RESPONSE_MS) {
                resumedTicksLeft = RESUMED_SHOW_TICKS
                update { it.copy(accrualState = AccrualState.RESUMED) }
            } else {
                slowResumeTicksLeft = SLOW_RESUME_TICKS
                update { it.copy(accrualState = AccrualState.PAUSED) }
            }
        }

        voiceJob = viewModelScope.launch {
            delay(RESULT_SHOW_MS)
            update { s ->
                if (s.voiceState != VoiceState.RESULT) s
                else s.copy(voiceState = VoiceState.IDLE, recognizedText = null)
            }
        }
    }

    // ───────────────────────── KkaeuActions ─────────────────────────

    override fun onAnswerOkay() {
        Log.d(TAG, "onAnswerOkay")
        handleAnswer(str(R.string.btn_okay))
    }

    override fun onAddRestStopWaypoint() {
        Log.d(TAG, "onAddRestStopWaypoint")
        update { it.copy(restStop = it.restStop?.copy(isWaypointAdded = true)) }
    }

    override fun onDismissRestStop() {
        Log.d(TAG, "onDismissRestStop")
        update { it.copy(restStop = null) }
    }

    override fun onCancelVehicleControl() {
        Log.d(TAG, "onCancelVehicleControl")
        vehicleEventTicksLeft = 0
        update { it.copy(vehicleEvent = null, windowOpen = false, outsideAirOn = false) }
    }

    override fun onCloseTripReport() {
        Log.d(TAG, "onCloseTripReport")
        startNewTrip(parked = _state.value.isParked)
    }

    override fun onToggleWarningSound() {
        Log.d(TAG, "onToggleWarningSound")
        update { it.copy(warningSoundOn = !it.warningSoundOn) }
    }

    /** 경고 단계 "음성으로 답하기": 알람을 멈추고 듣는 척하다가 2초 뒤 "괜찮아"로 응답 */
    override fun onVoiceAnswer() {
        Log.d(TAG, "onVoiceAnswer")
        val s = _state.value
        if (s.level < DrowsyLevel.WARNING || s.voiceState in ASKING_STATES) return
        voiceJob?.cancel()
        responseStartAt = now()
        update { it.copy(isAlarmRunning = false, voiceState = VoiceState.ASKING, voiceQuestion = str(R.string.warning_voice_prompt)) }
        voiceJob = viewModelScope.launch {
            delay(ASK_MS)
            responseStartAt = now()
            update { it.copy(voiceState = VoiceState.LISTENING) }
            delay(VOICE_ANSWER_MS)
            handleAnswer(str(R.string.fake_voice_answer))
        }
    }

    /** "근처 쉼터 찾기": 가까운 쉼터를 경유지로 추가 */
    override fun onFindRestStop() {
        Log.d(TAG, "onFindRestStop")
        update { it.copy(restStop = (it.restStop ?: sampleRestStop()).copy(isWaypointAdded = true)) }
    }

    override fun onStartCheckInAnswer(range: SleepRange) {
        Log.d(TAG, "onStartCheckInAnswer $range")
        finishCheckIn(range)
    }

    override fun onStartCheckInSkip() {
        Log.d(TAG, "onStartCheckInSkip")
        finishCheckIn(null)
    }

    // ───────────────────────── 출발 전 컨디션 질문 흉내 ─────────────────────────

    /** 묻는 척(3초) → 듣는 척(6초) → 대답 없음 → 10초 기다렸다가 100점으로 시작. 버튼을 누르면 바로 반영 */
    private fun beginStartCheckIn() {
        _state.value = _state.value.copy(startCheckIn = StartCheckIn(VoiceState.ASKING, str(R.string.checkin_question)))
        checkInJob?.cancel()
        checkInJob = viewModelScope.launch {
            delay(ASK_MS)
            updateCheckIn { it.copy(voiceState = VoiceState.LISTENING) }
            delay(LISTEN_MS)
            updateCheckIn { it.copy(voiceState = VoiceState.NO_RESPONSE) }
            for (sec in CHECKIN_AUTO_START_SEC downTo 1) {
                updateCheckIn { it.copy(autoStartSec = sec) }
                delay(TICK_MS)
            }
            finishCheckIn(null)
        }
    }

    private fun updateCheckIn(transform: (StartCheckIn) -> StartCheckIn) {
        val s = _state.value
        s.startCheckIn?.let { _state.value = s.copy(startCheckIn = transform(it)) }
    }

    private fun finishCheckIn(range: SleepRange?) {
        if (_state.value.startCheckIn == null) return
        checkInJob?.cancel()
        val penalty = 100f - (range?.startSafety ?: 100)
        update { it.copy(startCheckIn = null, sleepRange = range, score = maxOf(it.score, penalty)) }
    }

    // ───────────────────────── KkaeuDebugControls ─────────────────────────

    override fun setScore(score: Float) = update { it.copy(score = score) }

    override fun addReasonEvent(event: ReasonEvent) {
        eventCount++
        val text = str(event.reasonTextRes)
        val logLabel = str(event.logLabelRes)
        log(TripLogKind.EVENT, logLabel, safetyDelta = -event.scoreDelta.roundToInt())
        update {
            val newScore = (it.score + event.scoreDelta).coerceIn(0f, 100f)
            it.copy(
                score = newScore,
                lastScoreChange = ScoreChange(logLabel, (newScore - it.score).roundToInt()),
                // 같은 근거가 또 들어오면 맨 위로 올리기만 한다 (중복 표시 없음)
                reasons = (listOf(text) + it.reasons.filter { r -> r != text }).take(MAX_REASONS),
            )
        }
    }

    override fun setRiskTimeZone(on: Boolean) = update { it.copy(isRiskTimeZone = on) }

    override fun addContinuousDrive(minutes: Int) =
        update { it.copy(continuousDriveMin = it.continuousDriveMin + minutes) }

    override fun setVoiceState(state: VoiceState) {
        voiceJob?.cancel()
        if (state == VoiceState.ASKING || state == VoiceState.LISTENING) responseStartAt = now()
        update {
            it.copy(
                voiceState = state,
                voiceQuestion = it.voiceQuestion ?: str(R.string.voice_default_question),
                recognizedText = if (state == VoiceState.RESULT) str(R.string.fake_voice_answer) else null,
            )
        }
    }

    override fun setAlarm(on: Boolean) = update { it.copy(isAlarmRunning = on) }

    override fun triggerVehicleControl(blockedByRain: Boolean) {
        vehicleEventTicksLeft = VEHICLE_EVENT_TICKS
        val event = if (blockedByRain) {
            VehicleControlEvent(
                message = str(R.string.fake_window_try),
                canCancel = true,
                blockedReason = str(R.string.fake_window_blocked_rain),
            )
        } else {
            windowOpenedEvent()
        }
        update {
            if (blockedByRain) it.copy(vehicleEvent = event, weather = Weather.RAIN, outsideAirOn = true)
            else it.copy(vehicleEvent = event, windowOpen = true)
        }
    }

    override fun setRestStop(present: Boolean) =
        update { it.copy(restStop = if (present) sampleRestStop() else null) }

    override fun setParked(parked: Boolean) {
        val s = _state.value
        if (parked == s.isParked) return
        if (parked) {
            voiceJob?.cancel()
            if (s.restStop?.isWaypointAdded == true) restCount++
            log(TripLogKind.END, str(R.string.trip_log_end))
            val report = TripReport(
                driveMinutes = s.tripDriveMinutes,
                awakeMinutes = s.awakeMinutes,
                earnedPoints = s.tripPoints,
                avgResponseMs = responseTimesMs.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                maxLevel = maxLevel,
                eventCount = eventCount,
                restCount = restCount,
                startedAtMs = tripStartMs,
                endedAtMs = System.currentTimeMillis(),
                distanceKm = s.tripDistanceKm,
                averageSafety = s.safetyHistory.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                startSafety = s.sleepRange?.startSafety ?: 100,
                safetyHistory = s.safetyHistory,
                alertCounts = alertCounts,
                logs = tripLogs.toList(),
            )
            vehicleEventTicksLeft = 0
            update {
                it.copy(
                    isParked = true,
                    tripReport = report,
                    voiceState = VoiceState.IDLE,
                    recognizedText = null,
                    isAlarmRunning = false,
                    vehicleEvent = null,
                    forcedWarningRemainSec = null,
                    restStop = null,
                    continuousDriveMin = 0, // 쉬었으니 연속 주행 초기화
                )
            }
        } else {
            if (s.tripReport != null) startNewTrip(parked = true)
            update { it.copy(isParked = false) }
        }
    }

    override fun startDemo() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            try {
                runDemo()
            } finally {
                _debugState.value = DebugUiState()
            }
        }
    }

    override fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    override fun resetAll() {
        stopDemo()
        startNewTrip(parked = false)
    }

    // ───────────────────────── 데모 시나리오 (약 2분) ─────────────────────────

    private suspend fun runDemo() {
        resetCounters()
        _state.value = drivingStartState(totalPoints = _state.value.totalPoints).copy(
            score = 18f,
            continuousDriveMin = 102,
            tripDriveMinutes = 102,
            awakeMinutes = 102,
            tripPoints = 102,
            isRiskTimeZone = true,
        )

        // 1. NORMAL 주행, 포인트 적립 중
        demoStep(R.string.demo_step_1)
        delay(20_000)

        // 2. ② 이벤트 → CAUTION, 적립 멈춤, 질문(ASKING → LISTENING)
        demoStep(R.string.demo_step_2)
        addReasonEvent(ReasonEvent.STEER_FIX_THEN_JERK)
        state.first { it.voiceState == VoiceState.LISTENING }
        delay(2_000)

        // 3. 2초 만에 "괜찮아" → 점수 절반, NORMAL 복귀, 적립 재개
        demoStep(R.string.demo_step_3)
        handleAnswer(str(R.string.fake_voice_answer))
        delay(20_000)

        // 4. 이벤트 연속 → WARNING, 알람, 창문 배너
        demoStep(R.string.demo_step_4)
        addReasonEvent(ReasonEvent.NO_STEERING)
        delay(3_000)
        addReasonEvent(ReasonEvent.DRIFT_WITHOUT_SIGNAL)
        delay(3_000)
        addReasonEvent(ReasonEvent.UNSTABLE_SPEED)
        delay(2_000)
        addReasonEvent(ReasonEvent.NO_STEERING)
        delay(15_000)

        // 5. 80점 이상 → CRITICAL, 쉼터 카드, 경유지 추가
        demoStep(R.string.demo_step_5)
        addReasonEvent(ReasonEvent.UNSTABLE_SPEED)
        delay(8_000)
        onAddRestStopWaypoint()
        delay(6_000)
        onAnswerOkay()
        delay(16_000)

        // 6. 쉼터 도착, P단 → 주행 종료 리포트
        demoStep(R.string.demo_step_6)
        setParked(true)
        delay(8_000)
    }

    private fun demoStep(@StringRes res: Int) {
        Log.d(TAG, "demo: ${str(res)}")
        _debugState.value = DebugUiState(demoRunning = true, demoStepRes = res)
    }

    // ───────────────────────── 보조 ─────────────────────────

    private fun startNewTrip(parked: Boolean) {
        resetCounters()
        val s = _state.value
        _state.value = drivingStartState(totalPoints = s.totalPoints).copy(
            isParked = parked,
            isRiskTimeZone = s.isRiskTimeZone,
            warningSoundOn = s.warningSoundOn,
            sleepRange = s.sleepRange,
        )
    }

    private fun resetCounters() {
        voiceJob?.cancel()
        maxLevel = DrowsyLevel.NORMAL
        eventCount = 0
        restCount = 0
        responseTimesMs.clear()
        alertCounts = AlertCounts()
        alertDismissal.clear()
        tripStartMs = System.currentTimeMillis()
        tripLogs.clear()
        log(TripLogKind.START, str(R.string.trip_log_start))
        resumedTicksLeft = 0
        slowResumeTicksLeft = 0
        vehicleEventTicksLeft = 0
    }

    /** 시연용 시작 상태: 디자인 시안과 비슷한 값 (가까운 쉼터·점수 그래프·누적 기록·수면 기록) */
    private fun drivingStartState(totalPoints: Int) =
        DrowsinessUiState.Initial.copy(
            score = 8f,
            totalPoints = totalPoints,
            restStop = sampleRestStop(),
            continuousDriveMin = 72,
            tripDriveMinutes = 72,
            awakeMinutes = 72,
            safetyHistory = SAMPLE_HISTORY,
            lifetime = LifetimeStats(totalDriveMinutes = 278 * 60, totalDistanceKm = 12_430f, averageSafetyScore = 93),
            tripDistanceKm = 72 * FAKE_KM_PER_MIN,
            driveLog = SampleRecords.driveLog(),
            benefits = SampleRecords.benefits(),
            sleep = SleepCondition(
                sleepStartMs = System.currentTimeMillis() - (3 * 60 + 440) * 60_000L,
                sleepMinutes = 440,
                awakeMinutes = 180,
            ),
        )

    private fun windowOpenedEvent() = VehicleControlEvent(
        message = str(R.string.fake_window_opened),
        canCancel = true,
        blockedReason = null,
    )

    private fun sampleRestStop() = RestStopInfo(
        name = str(R.string.fake_rest_stop_name),
        type = RestStopType.DROWSY_SHELTER,
        distanceKm = 8.2f,
        hasToilet = true,
        parkingSpaces = 18,
        isWaypointAdded = false,
    )

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)

    private fun str(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun levelName(level: DrowsyLevel) = str(
        when (level) {
            DrowsyLevel.NORMAL -> R.string.level_normal
            DrowsyLevel.CAUTION -> R.string.level_caution
            DrowsyLevel.WARNING -> R.string.level_warning
            DrowsyLevel.CRITICAL -> R.string.level_critical
        }
    )

    /** 이번 주행 상세 로그에 한 줄 남긴다 */
    private fun log(kind: TripLogKind, message: String, safetyDelta: Int? = null, level: DrowsyLevel? = null) {
        tripLogs += TripLogEntry(System.currentTimeMillis(), kind, message, safetyDelta, level)
        if (tripLogs.size > MAX_TRIP_LOGS) tripLogs.removeAt(0)
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TAG = "KkaeuFake"

        const val START_TOTAL_POINTS = 12_850
        const val MAX_REASONS = 3

        const val TICK_MS = 1_000L
        const val ASK_MS = 3_000L
        const val LISTEN_MS = 6_000L
        const val NO_RESPONSE_MS = 2_000L
        const val ASK_ATTEMPTS = 2
        const val RESULT_SHOW_MS = 2_000L
        const val FAST_RESPONSE_MS = 4_000L

        const val RESUMED_SHOW_TICKS = 3
        const val SLOW_RESUME_TICKS = 5
        const val VEHICLE_EVENT_TICKS = 5
        const val FORCED_WARNING_SEC = 180

        val ASKING_STATES = setOf(VoiceState.ASKING, VoiceState.LISTENING, VoiceState.NO_RESPONSE)

        const val VOICE_ANSWER_MS = 2_000L
        const val CHECKIN_AUTO_START_SEC = 10
        const val MAX_TRIP_LOGS = 300
        const val HISTORY_EVERY_MIN = 10        // 그래프 점 간격 (시연 시간으로 10분 = 실제 10초)
        const val MAX_HISTORY = 24
        const val FAKE_KM_PER_MIN = 1.5f        // 시속 90km
        val SAMPLE_HISTORY = listOf(88, 91, 93, 90, 96, 92, 89, 86)
    }
}
