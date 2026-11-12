package com.sleepreaper.kkaeurang.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.ui.components.DrowsinessDial
import com.sleepreaper.kkaeurang.ui.components.ReasonBlock
import com.sleepreaper.kkaeurang.ui.components.RestStopCard
import com.sleepreaper.kkaeurang.ui.components.RewardStrip
import com.sleepreaper.kkaeurang.ui.components.VehicleControlBanner
import com.sleepreaper.kkaeurang.ui.components.VoicePanel
import com.sleepreaper.kkaeurang.ui.components.WarningPanel
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.screens.KkaeuApp
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors

// ───────── 화면 단위 (단계별) ─────────

@Composable
private fun AppPreview(state: DrowsinessUiState) {
    KkaeUrangTheme {
        KkaeuApp(state = state, actions = NoOpKkaeuActions, debugControls = null)
    }
}

@LandscapePreview
@Composable
fun Preview1Normal() = AppPreview(PreviewSamples.normal)

@LandscapePreview
@Composable
fun Preview2CautionAsking() = AppPreview(PreviewSamples.cautionAsking)

@LandscapePreview
@Composable
fun Preview3CautionListening() = AppPreview(PreviewSamples.cautionListening)

@LandscapePreview
@Composable
fun Preview4CautionRestStop() = AppPreview(PreviewSamples.cautionWithRestStop)

@LandscapePreview
@Composable
fun Preview5Resumed() = AppPreview(PreviewSamples.resumed)

@LandscapePreview
@Composable
fun Preview6Warning() = AppPreview(PreviewSamples.warning)

@LandscapePreview
@Composable
fun Preview7WarningForced() = AppPreview(PreviewSamples.warningForced)

@LandscapePreview
@Composable
fun Preview8Critical() = AppPreview(PreviewSamples.critical)

@LandscapePreview
@Composable
fun Preview9TripReport() = AppPreview(PreviewSamples.parked)

@SplitPreview
@Composable
fun PreviewSplitCaution() = AppPreview(PreviewSamples.cautionListening)

@SplitPreview
@Composable
fun PreviewSplitCritical() = AppPreview(PreviewSamples.critical)

// ───────── 블록 단위 ─────────

@Composable
private fun BlockFrame(content: @Composable () -> Unit) = KkaeUrangTheme {
    Surface(color = KkaeuColors.Background, contentColor = KkaeuColors.TextPrimary, content = content)
}

@DialPreview
@Composable
fun PreviewDialAllLevels() = BlockFrame {
    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(
            DrowsyLevel.NORMAL to 18f,
            DrowsyLevel.CAUTION to 52f,
            DrowsyLevel.WARNING to 74f,
            DrowsyLevel.CRITICAL to 91f,
        ).forEach { (level, score) ->
            DrowsinessDial(score = score, level = level, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@SidePreview
@Composable
fun PreviewVoicePanelAllStates() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(
            VoiceState.ASKING, VoiceState.LISTENING, VoiceState.RESULT,
            VoiceState.NO_RESPONSE, VoiceState.FAILED,
        ).forEach {
            VoicePanel(
                voiceState = it,
                question = "주행이 평소와 조금 달라요. 괜찮으세요?",
                recognizedText = "괜찮아",
                onAnswerOkay = {},
            )
        }
    }
}

@SidePreview
@Composable
fun PreviewWarningPanel() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 실제 화면에서는 남은 높이를 채우므로, 미리보기에서는 높이를 정해 준다
        WarningPanel(PreviewSamples.warning, onAnswerOkay = {}, modifier = Modifier.height(560.dp))
        WarningPanel(
            PreviewSamples.warningForced.copy(level = DrowsyLevel.CRITICAL),
            onAnswerOkay = {},
            modifier = Modifier.height(560.dp),
        )
    }
}

@SidePreview
@Composable
fun PreviewReasonBlock() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ReasonBlock(DrowsyLevel.NORMAL, emptyList(), continuousDriveMin = 102, isRiskTimeZone = false)
        val s = PreviewSamples.cautionWithRestStop
        ReasonBlock(s.level, s.reasons, s.continuousDriveMin, isRiskTimeZone = true)
    }
}

@SidePreview
@Composable
fun PreviewRewardStripAllStates() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RewardStrip(AccrualState.ACCRUING, awakeMinutes = 102, tripDriveMinutes = 102, tripPoints = 85)
        RewardStrip(AccrualState.PAUSED, awakeMinutes = 102, tripDriveMinutes = 110, tripPoints = 85)
        RewardStrip(AccrualState.RESUMED, awakeMinutes = 103, tripDriveMinutes = 112, tripPoints = 86)
    }
}

@SidePreview
@Composable
fun PreviewRestStopCard() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        RestStopCard(PreviewSamples.restStop, onAddWaypoint = {}, onDismiss = {})
        RestStopCard(PreviewSamples.restStop.copy(isWaypointAdded = true), onAddWaypoint = {}, onDismiss = {})
    }
}

@SidePreview
@Composable
fun PreviewVehicleControlBanner() = BlockFrame {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        VehicleControlBanner(PreviewSamples.windowEvent, onCancel = {})
        VehicleControlBanner(PreviewSamples.rainBlockedEvent, onCancel = {})
    }
}
