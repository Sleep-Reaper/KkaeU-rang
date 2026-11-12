package com.sleepreaper.kkaeurang.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.ui.components.NightSkyBackground
import com.sleepreaper.kkaeurang.ui.components.SafetyGauge
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.screens.KkaeuApp
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.ui.theme.ProvideDesignScale

// ───────── 화면 단위 (단계별) ─────────

@Composable
private fun AppPreview(state: DrowsinessUiState) {
    KkaeUrangTheme {
        KkaeuApp(state = state, actions = NoOpKkaeuActions, debugControls = null)
    }
}

@LandscapePreview
@Composable
fun Preview1Home() = AppPreview(PreviewSamples.normal)

@LandscapePreview
@Composable
fun Preview2CautionAsking() = AppPreview(PreviewSamples.cautionAsking)

@LandscapePreview
@Composable
fun Preview3CautionListening() = AppPreview(PreviewSamples.cautionListening)

@LandscapePreview
@Composable
fun Preview4CautionResult() = AppPreview(PreviewSamples.cautionResult)

@LandscapePreview
@Composable
fun Preview5CautionFailed() = AppPreview(PreviewSamples.cautionFailed)

@LandscapePreview
@Composable
fun Preview6Warning() = AppPreview(PreviewSamples.warning)

@LandscapePreview
@Composable
fun Preview7WarningListening() = AppPreview(PreviewSamples.warningListening)

@LandscapePreview
@Composable
fun Preview8WarningForced() = AppPreview(PreviewSamples.warningForced)

@LandscapePreview
@Composable
fun Preview9Critical() = AppPreview(PreviewSamples.critical)

@LandscapePreview
@Composable
fun Preview10CriticalNoWindow() = AppPreview(PreviewSamples.criticalNoWindow)

@LandscapePreview
@Composable
fun Preview11TripReport() = AppPreview(PreviewSamples.parked)

@SplitPreview
@Composable
fun PreviewSplitHome() = AppPreview(PreviewSamples.normal)

// ───────── 블록 단위 ─────────

@DialPreview
@Composable
fun PreviewGaugeAllLevels() = KkaeUrangTheme {
    Box {
        NightSkyBackground(alert = false)
        ProvideDesignScale {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(
                    DrowsyLevel.NORMAL to 92,
                    DrowsyLevel.CAUTION to 48,
                    DrowsyLevel.WARNING to 26,
                    DrowsyLevel.CRITICAL to 9,
                ).forEach { (level, safety) ->
                    SafetyGauge(safety = safety, level = level, onLongPress = null)
                }
            }
        }
    }
}
