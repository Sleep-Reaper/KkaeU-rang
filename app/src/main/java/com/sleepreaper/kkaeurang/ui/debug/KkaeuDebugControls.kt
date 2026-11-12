package com.sleepreaper.kkaeurang.ui.debug

import androidx.annotation.StringRes
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import kotlinx.coroutines.flow.StateFlow

/** 디버그 패널에서 넣는 "판단 근거" 이벤트 (시연·개발용) */
enum class ReasonEvent(
    val scoreDelta: Float,
    @StringRes val buttonLabelRes: Int,
    @StringRes val reasonTextRes: Int,
    /** 계기판 아래 "마지막 점수 로그"에 쓰는 짧은 이름 (한 줄에 들어가게) */
    @StringRes val logLabelRes: Int,
) {
    NO_STEERING(15f, R.string.debug_event_no_steering, R.string.fake_reason_no_steering, R.string.fake_log_no_steering),
    STEER_FIX_THEN_JERK(25f, R.string.debug_event_steer_jerk, R.string.fake_reason_steer_jerk, R.string.fake_log_steer_jerk),
    DRIFT_WITHOUT_SIGNAL(15f, R.string.debug_event_drift, R.string.fake_reason_drift, R.string.fake_log_drift),
    UNSTABLE_SPEED(10f, R.string.debug_event_speed, R.string.fake_reason_speed, R.string.fake_log_speed),
}

data class DebugUiState(
    val demoRunning: Boolean = false,
    @StringRes val demoStepRes: Int? = null,
)

/**
 * 디버그 패널이 쓰는 조작 모음. 실제 모듈로 교체한 뒤에는 KkaeuApp에 null을 넘기면 패널이 꺼진다.
 */
interface KkaeuDebugControls {
    val debugState: StateFlow<DebugUiState>

    fun setScore(score: Float)
    fun addReasonEvent(event: ReasonEvent)
    fun setRiskTimeZone(on: Boolean)
    fun addContinuousDrive(minutes: Int)
    fun setVoiceState(state: VoiceState)
    fun setAlarm(on: Boolean)
    fun triggerVehicleControl(blockedByRain: Boolean)
    fun setRestStop(present: Boolean)
    fun setParked(parked: Boolean)
    fun startDemo()
    fun stopDemo()
    fun resetAll()
}
