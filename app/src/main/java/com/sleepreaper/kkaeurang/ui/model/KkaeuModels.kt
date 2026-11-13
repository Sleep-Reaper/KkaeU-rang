package com.sleepreaper.kkaeurang.ui.model


/** 졸음 단계. 점수 → 단계 경계값은 [DrowsyThresholds]에 모아 둔다 */
enum class DrowsyLevel { NORMAL, CAUTION, WARNING, CRITICAL }

/** 음성 질문 진행 상태 (VoicePanel이 이 값만 보고 그린다) */
enum class VoiceState { IDLE, ASKING, LISTENING, RESULT, NO_RESPONSE, FAILED, ALARM }

/** 포인트 적립 상태 (주행 리포트·적립 계산용) */
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

/**
 * 주행 종료 리포트 (P단으로 주행을 마쳤을 때). 화면은 "주행 로그" 탭과 같은 구조로 그린다.
 * 아래 기본값이 있는 값들은 공급자가 모르면 비워 둬도 된다 (화면이 "기록 없음"으로 보여 준다)
 */
data class TripReport(
    val driveMinutes: Int,
    val awakeMinutes: Int,
    val earnedPoints: Int,
    val avgResponseMs: Long?,
    val maxLevel: DrowsyLevel,
    val eventCount: Int,
    val restCount: Int,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val distanceKm: Float = 0f,
    val averageSafety: Int? = null,          // 이번 주행 평균 안전 점수
    val startSafety: Int = 100,              // 출발 전 컨디션으로 정한 시작 점수
    val safetyHistory: List<Int> = emptyList(),
    val alertCounts: AlertCounts = AlertCounts(),
    val logs: List<TripLogEntry> = emptyList(),   // 상세 로그 (오래된 것부터)
)

/** 졸음 경보 단계별 횟수 (안전 → 주의 이상으로 올라간 횟수) */
data class AlertCounts(val caution: Int = 0, val warning: Int = 0, val critical: Int = 0) {
    val total: Int get() = caution + warning + critical

    /** [level]로 올라간 경보 한 번을 더한다 (안전은 무시) */
    fun plus(level: DrowsyLevel) = when (level) {
        DrowsyLevel.NORMAL -> this
        DrowsyLevel.CAUTION -> copy(caution = caution + 1)
        DrowsyLevel.WARNING -> copy(warning = warning + 1)
        DrowsyLevel.CRITICAL -> copy(critical = critical + 1)
    }
}

/** 상세 로그 한 줄의 종류 (아이콘·색) */
enum class TripLogKind { START, CONDITION, LEVEL_UP, LEVEL_DOWN, EVENT, QUESTION, ANSWER, ALARM, VEHICLE, REST, END, OTHER }

/** 주행 상세 로그 한 줄 */
data class TripLogEntry(
    val timeMs: Long,
    val kind: TripLogKind,
    val message: String,
    val safetyDelta: Int? = null,    // 안전 점수 변화 (-25 등). 없으면 표시 안 함
    val level: DrowsyLevel? = null,  // 단계 변경 줄이면 바뀐 단계
)

/**
 * 출발 전 컨디션 질문 "마지막으로 몇 시간 주무셨어요?"의 대답 구간(마지막 수면 시간)과 그에 따른 오늘의 시작 안전 점수.
 * 적게 잘수록 낮게 시작한다. 대답하지 않거나 건너뛰면 100점에서 시작한다.
 * TODO(design): 구간별 시작 점수는 임시 값이다. 확정되면 [startSafety]만 바꾼다
 */
enum class SleepRange(val startSafety: Int) {
    UNDER_4H(80),
    H4_TO_6(90),
    H6_TO_8(95),
    OVER_8H(100),
}

/** 앱을 시작할 때 보여 주는 출발 전 컨디션 질문 화면 상태 */
data class StartCheckIn(
    val voiceState: VoiceState,          // ASKING(묻는 중) · LISTENING · RESULT(알아들음) · NO_RESPONSE · FAILED(못 알아들음) · IDLE
    val question: String,                // 깨우랑이 말하는 질문 (화면에 한 줄로)
    val heardText: String? = null,       // 인식된 대답
    val heardRange: SleepRange? = null,  // 대답에서 알아낸 구간 (잠깐 강조한 뒤 적용)
    val autoStartSec: Int? = null,       // 대답이 없을 때 100점으로 시작하기까지 남은 초
)

/** 수면 기록 (홈 "현재 컨디션" 카드) */
data class SleepCondition(
    val sleepStartMs: Long,        // 잠든 시각
    val sleepMinutes: Int,         // 잔 시간
    val awakeMinutes: Int,         // 일어난 뒤 지난 시간
)

/** 지금까지 모든 주행을 합친 기록 (홈 "나의 주행 로그" 카드) */
data class LifetimeStats(
    val totalDriveMinutes: Int,
    val totalDistanceKm: Float,
    val averageSafetyScore: Int?,  // 안전 점수 평균 (기록이 없으면 null)
)

/** 마지막으로 점수가 바뀐 이유와 변화량 (엔진 사건 로그에서 뽑은 값) */
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
    // 깨우랑이 연 창문·켠 찬바람. KkaeuLiveSource가 WindowController 판단으로 매초 채운다
    val windowOpen: Boolean = false,
    val outsideAirOn: Boolean = false,
    // TODO(integration): 날씨 정보에서 채운다
    val weather: Weather = Weather.PARTLY_CLOUDY,
    /** 이번 주행의 안전 점수 기록 (오래된 것부터, 일정 간격으로 쌓음). 홈 "이번 주행 점수 로그" 그래프 */
    val safetyHistory: List<Int> = emptyList(),
    val lifetime: LifetimeStats = LifetimeStats(0, 0f, null),
    // TODO(integration): 수면 기록(웨어러블·직접 입력 등)이 생기면 채운다. null이면 카드에 "기록 없음"
    val sleep: SleepCondition? = null,
    /** 이번 주행 거리 (홈 "현재 주행 로그") */
    val tripDistanceKm: Float = 0f,
    /**
     * 주의 이상인데 운전자가 "괜찮다"고 답하거나 화면을 눌러 경보 화면을 닫은 상태.
     * true면 경보 화면 대신 홈 계기판을 보여 준다. 단계가 더 오르거나 점수가 크게 나빠지면 공급자가 다시 false로 만든다
     */
    val alertDismissed: Boolean = false,
    /** 깨우랑이 마지막으로 말한 안내 문장 (위험 단계 졸음쉼터 안내 결과). 위험 화면에 한 줄로 보여 준다 */
    val voiceGuide: String? = null,
    /** 위험 단계 졸음쉼터 안내 결과. null = 아직 결과 없음, false = 내비 연동 실패 (위험 화면 경로 그림 문구) */
    val restGuideOk: Boolean? = null,
    /** 출발 전 컨디션 질문 화면. null이면 보이지 않는다 */
    val startCheckIn: StartCheckIn? = null,
    /** 출발 전 컨디션 대답 (null = 대답 안 함·건너뜀 → 100점에서 시작) */
    val sleepRange: SleepRange? = null,
    /** 주행 로그 탭 (지금은 임시 값) */
    val driveLog: DriveLogSummary = DriveLogSummary.Empty,
    /** 혜택 탭 (지금은 임시 값). 보유 포인트는 [totalPoints]를 쓴다 */
    val benefits: BenefitsSummary = BenefitsSummary.Empty,
) {
    /**
     * 화면에 보여 주는 안전 점수 (높을수록 안전). 판단 엔진의 졸음 점수(높을수록 위험)를 뒤집은 값이다.
     * 단계 경계: 졸음 40·70·80 ↔ 안전 60·30·20
     */
    val safetyScore: Int get() = DrowsyThresholds.safetyOf(score)

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
