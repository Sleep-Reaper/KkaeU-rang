package com.sleepreaper.kkaeurang.ui.preview

import androidx.compose.ui.tooling.preview.Preview
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.RestStopInfo
import com.sleepreaper.kkaeurang.ui.model.RestStopType
import com.sleepreaper.kkaeurang.ui.model.ScoreChange
import com.sleepreaper.kkaeurang.ui.model.TripReport
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions

/**
 * Pleos Connect 에뮬레이터 (2560x1440, mdpi)에서 실제 앱 영역 크기.
 * 왼쪽 차량 패널·위 상태바·아래 독을 뺀 약 1680x1210dp (에뮬레이터 캡처로 확인)
 */
@Preview(widthDp = 1680, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0A0D12)
annotation class LandscapePreview

/** 화면 분할(세로 반쪽) 확인용 */
@Preview(widthDp = 840, heightDp = 1210, showBackground = true, backgroundColor = 0xFF0A0D12)
annotation class SplitPreview

/** 사이드 패널 블록 하나만 볼 때 (가로 전체 화면에서 사이드 패널 폭) */
@Preview(widthDp = 680, showBackground = true, backgroundColor = 0xFF0A0D12)
annotation class SidePreview

/** 계기판만 볼 때 */
@Preview(widthDp = 1400, heightDp = 400, showBackground = true, backgroundColor = 0xFF0A0D12)
annotation class DialPreview

/** 미리보기용 아무것도 안 하는 동작 */
object NoOpKkaeuActions : KkaeuActions {
    override fun onAnswerOkay() = Unit
    override fun onAddRestStopWaypoint() = Unit
    override fun onDismissRestStop() = Unit
    override fun onCancelVehicleControl() = Unit
    override fun onCloseTripReport() = Unit
    override fun onToggleWarningSound() = Unit
}

/** 미리보기 전용 샘플 데이터 (앱 화면 문구는 strings.xml, 여기 문장은 판단 엔진이 내려줄 값을 흉내 낸 것) */
object PreviewSamples {
    private val reasons = listOf(
        "핸들을 고정하다 갑자기 크게 꺾었어요",
        "깜빡이 없이 한쪽으로 쏠렸어요",
        "속도가 일정하지 않아요",
    )

    val restStop = RestStopInfo(
        name = "청주 졸음쉼터",
        type = RestStopType.DROWSY_SHELTER,
        distanceKm = 3.2f,
        hasToilet = true,
        parkingSpaces = 18,
        isWaypointAdded = false,
    )

    val windowEvent = VehicleControlEvent(
        message = "졸음 감지로 창문을 조금 열었어요",
        canCancel = true,
        blockedReason = null,
    )

    val rainBlockedEvent = VehicleControlEvent(
        message = "졸음 감지로 창문을 열려고 했어요",
        canCancel = true,
        blockedReason = "비가 와서 창문 대신 찬바람을 켰어요",
    )

    val normal = DrowsinessUiState.Initial.copy(
        score = 18f,
        continuousDriveMin = 102,
        isRiskTimeZone = true,
        totalPoints = 12_450,
        tripPoints = 102,
        awakeMinutes = 102,
        tripDriveMinutes = 102,
    )

    val cautionAsking = normal.copy(
        score = 46f,
        level = DrowsyLevel.CAUTION,
        reasons = reasons.take(1),
        lastScoreChange = ScoreChange(reasons[0], 25),
        continuousDriveMin = 128,
        voiceState = VoiceState.ASKING,
        voiceQuestion = "주행이 평소와 조금 달라요. 괜찮으세요?",
        accrualState = AccrualState.PAUSED,
        awakeMinutes = 102,
        tripDriveMinutes = 128,
    )

    val cautionListening = cautionAsking.copy(voiceState = VoiceState.LISTENING)
    val cautionResult = cautionAsking.copy(voiceState = VoiceState.RESULT, recognizedText = "괜찮아")
    val cautionFailed = cautionAsking.copy(voiceState = VoiceState.FAILED)
    val cautionWithRestStop = cautionAsking.copy(
        voiceState = VoiceState.IDLE,
        reasons = reasons,
        restStop = restStop.copy(isWaypointAdded = true),
    )

    val resumed = normal.copy(
        score = 23f,
        accrualState = AccrualState.RESUMED,
        tripDriveMinutes = 140,
        awakeMinutes = 130,
        lastScoreChange = ScoreChange("괜찮다고 대답했어요", -23),
    )

    val warning = cautionAsking.copy(
        score = 76f,
        level = DrowsyLevel.WARNING,
        reasons = reasons,
        lastScoreChange = ScoreChange(reasons[2], 15),
        voiceState = VoiceState.ALARM,
        isAlarmRunning = true,
        accrualState = AccrualState.HIDDEN,
        vehicleEvent = windowEvent,
    )

    val warningForced = warning.copy(score = 52f, forcedWarningRemainSec = 161, vehicleEvent = null)

    val critical = warning.copy(
        score = 86f,
        level = DrowsyLevel.CRITICAL,
        vehicleEvent = null,
        restStop = restStop,
    )

    val tripReport = TripReport(
        driveMinutes = 140,
        awakeMinutes = 130,
        earnedPoints = 130,
        avgResponseMs = 1_850,
        maxLevel = DrowsyLevel.CRITICAL,
        eventCount = 5,
        restCount = 1,
    )

    val parked = normal.copy(isParked = true, tripReport = tripReport, totalPoints = 12_580)
}
