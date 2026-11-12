package com.sleepreaper.kkaeurang.ui.model

/** 졸음 단계. 점수 → 단계 경계값은 [DrowsyThresholds]에 모아 둔다 */
enum class DrowsyLevel { NORMAL, CAUTION, WARNING, CRITICAL }

/** 음성 질문 진행 상태 (VoicePanel이 이 값만 보고 그린다) */
enum class VoiceState { IDLE, ASKING, LISTENING, RESULT, NO_RESPONSE, FAILED, ALARM }

/** 포인트 적립 상태 (RewardStrip 표시용) */
enum class AccrualState { ACCRUING, PAUSED, RESUMED, HIDDEN }

/** 현재 날씨 (메인 화면 왼쪽 아래 아이콘) */
enum class Weather { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW }

enum class RestStopType {
    DROWSY_SHELTER, // 졸음쉼터
    SERVICE_AREA,   // 휴게소
}

data class RestStopInfo(
    val name: String,
    val type: RestStopType,
    val distanceKm: Float,
    val hasToilet: Boolean,
    val parkingSpaces: Int?,
    val isWaypointAdded: Boolean,
)

data class VehicleControlEvent(
    val message: String,           // "졸음 감지로 창문을 열었어요"
    val canCancel: Boolean,
    val blockedReason: String?,    // "비가 와서 창문 대신 찬바람을 켰어요" 등
)

data class TripReport(
    val driveMinutes: Int,
    val awakeMinutes: Int,
    val earnedPoints: Int,
    val avgResponseMs: Long?,
    val maxLevel: DrowsyLevel,
    val eventCount: Int,
    val restCount: Int,
)

/** 마지막으로 점수가 바뀐 이유와 변화량 (계기판 아래 "마지막 점수 로그") */
data class ScoreChange(
    val reason: String,            // "핸들을 고정하다 갑자기 크게 꺾었어요"
    val delta: Int,                // +25, -21 …
)

/** UI가 보는 유일한 상태. 화면 코드는 이 값만 보고 그린다 */
data class DrowsinessUiState(
    val score: Float,                    // 0~100
    val level: DrowsyLevel,
    val reasons: List<String>,           // 판단 근거
    val continuousDriveMin: Int,         // 연속 주행 시간
    val isRiskTimeZone: Boolean,         // 02~05시, 14~16시
    val forcedWarningRemainSec: Int?,    // 무응답으로 WARNING 강제 유지 중일 때 남은 시간
    val voiceState: VoiceState,
    val voiceQuestion: String?,          // 질문 문장
    val recognizedText: String?,         // 인식 결과
    val isAlarmRunning: Boolean,
    val vehicleEvent: VehicleControlEvent?,
    val restStop: RestStopInfo?,
    val totalPoints: Int,                // 누적 포인트
    val tripPoints: Int,                 // 이번 주행 적립
    val awakeMinutes: Int,               // 이번 주행에서 깨어 있던(적립된) 시간
    val tripDriveMinutes: Int,           // 이번 주행 전체 시간
    val accrualState: AccrualState,
    val isParked: Boolean,               // 기어 P
    val tripReport: TripReport?,         // 주행 종료 시에만 값 있음
    // TODO(integration): 판단 엔진이 점수를 바꿀 때마다 이유와 변화량을 채워 준다
    val lastScoreChange: ScoreChange? = null,
    // TODO(integration): 경고음 켬/끔은 VoiceModule 알람 음소거 설정과 맞춘다
    val warningSoundOn: Boolean = true,
    // TODO(integration): 차량 제어 모듈(창문·공조)과 날씨 정보에서 채운다
    val windowOpen: Boolean = false,
    val outsideAirOn: Boolean = false,
    val weather: Weather = Weather.PARTLY_CLOUDY,
) {
    companion object {
        /** 아무 일도 없는 주행 시작 상태 */
        val Initial = DrowsinessUiState(
            score = 0f,
            level = DrowsyLevel.NORMAL,
            reasons = emptyList(),
            continuousDriveMin = 0,
            isRiskTimeZone = false,
            forcedWarningRemainSec = null,
            voiceState = VoiceState.IDLE,
            voiceQuestion = null,
            recognizedText = null,
            isAlarmRunning = false,
            vehicleEvent = null,
            restStop = null,
            totalPoints = 0,
            tripPoints = 0,
            awakeMinutes = 0,
            tripDriveMinutes = 0,
            accrualState = AccrualState.ACCRUING,
            isParked = false,
            tripReport = null,
        )
    }
}
