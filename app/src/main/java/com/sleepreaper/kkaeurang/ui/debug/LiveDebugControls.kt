package com.sleepreaper.kkaeurang.ui.debug

import androidx.annotation.StringRes
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import kotlinx.coroutines.flow.StateFlow

/** 가짜 차량 신호로 재생하는 주행 시나리오. 판단 엔진의 규칙 ①~④가 실제로 걸리도록 만든 신호 */
enum class DriveScenario(@StringRes val labelRes: Int) {
    STEERING_FROZEN(R.string.live_debug_scn_frozen),
    FROZEN_THEN_JERK(R.string.live_debug_scn_jerk),
    DRIFT(R.string.live_debug_scn_drift),
    UNSTABLE_SPEED(R.string.live_debug_scn_speed),
}

/** 엔진의 운전자 질문에 디버그 패널에서 대신 대답하기 */
enum class DebugAnswer(@StringRes val labelRes: Int) {
    OKAY(R.string.live_debug_answer_okay),        // "괜찮아" → 5분간 다시 묻지 않음
    SLEEPY(R.string.live_debug_answer_sleepy),    // "졸려" → +30
    NO_ANSWER(R.string.live_debug_answer_none),   // 무응답 → 경고 이상 3분 유지
}

/** 실제 모드 디버그 패널이 보는 값 (엔진 내부 값 그대로) */
data class LiveDebugState(
    val simulated: Boolean = false,          // 가짜 차량 신호를 쓰는지 (false면 시나리오·기어 버튼 숨김)
    val score: Int = 0,
    val level: DrowsyLevel = DrowsyLevel.NORMAL,
    val speedKmh: Float = 0f,
    val steeringDeg: Float = 0f,
    val turnSignalOn: Boolean = false,
    val brakeOn: Boolean = false,
    val isPark: Boolean = true,
    val clockText: String = "",              // 엔진이 보는 현재 시각 (시간 이동 반영)
    val clockOffsetMin: Long = 0,
    val isAsking: Boolean = false,           // 엔진이 질문하고 대답을 기다리는 중
    val scenario: DriveScenario? = null,
    val logs: List<String> = emptyList(),    // 최근 사건 로그, 최신이 위
)

/**
 * 실제 모드(판단 엔진 연결)용 디버그 조작. 엔진 값을 직접 바꾸거나 가짜 차량 신호를 넣어
 * 점수·단계에 따라 화면·음성·알람이 제대로 바뀌는지 확인한다.
 */
interface LiveDebugControls {
    val liveDebugState: StateFlow<LiveDebugState>

    /** 엔진 이벤트 점수를 직접 더하거나 뺀다 (단계 전환은 1초 안에 평소처럼 일어남) */
    fun adjustScore(delta: Int)

    /** 엔진이 보는 시계를 앞으로 돌린다 (연속 주행 시간·위험 시간대·재질문 대기 등이 같이 흐름) */
    fun advanceTime(minutes: Int)

    /** 다음 졸음 위험 시간대(02시 또는 14시) 시작으로 시계를 옮긴다 */
    fun jumpToRiskTime()

    /** 진행 중인 엔진 질문에 대신 대답한다 (질문 중이 아니면 무시) */
    fun answer(answer: DebugAnswer)

    // ── 아래는 가짜 차량 신호일 때만 동작 ──
    fun runScenario(scenario: DriveScenario)
    fun setParked(parked: Boolean)
    fun setTurnSignal(on: Boolean)
    fun setBrake(on: Boolean)
}
