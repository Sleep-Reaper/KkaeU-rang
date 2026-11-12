package com.sleepreaper.kkaeurang.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.labelRes
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import kotlin.math.roundToInt

/**
 * ⑦ 디버그 패널 (시연·개발용). 로고를 길게 누르면 왼쪽에서 열리는 사이드 시트.
 * 바깥(어두운 부분)을 누르면 닫힌다. 주행 화면이 아니므로 스크롤을 허용한다.
 */
@Composable
fun DebugPanel(
    controls: KkaeuDebugControls,
    state: DrowsinessUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val debug by controls.debugState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KkaeuColors.Scrim)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
                .clip(RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
                .background(KkaeuColors.SurfaceHigh)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.debug_title),
                    style = KkaeuType.Title,
                    modifier = Modifier.weight(1f),
                )
                DebugButton(stringResource(R.string.btn_close), onClose, filled = false)
            }

            // 데모 자동 재생: 누르면 패널을 닫아 촬영 화면을 깨끗하게 한다
            if (debug.demoRunning) {
                DebugButton(stringResource(R.string.debug_demo_stop), controls::stopDemo, Modifier.fillMaxWidth())
                debug.demoStepRes?.let {
                    Text(text = stringResource(it), style = KkaeuType.Debug, color = KkaeuColors.LevelNormal)
                }
            } else {
                DebugButton(
                    text = stringResource(R.string.debug_demo_play),
                    onClick = {
                        controls.startDemo()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.debug_section_score, state.score.roundToInt(), stringResource(state.level.labelRes()))) {
                Slider(
                    value = state.score,
                    onValueChange = controls::setScore,
                    valueRange = 0f..100f,
                )
            }

            Section(stringResource(R.string.debug_section_reason)) {
                ReasonEvent.entries.chunked(2).forEach { row ->
                    ButtonRow {
                        row.forEach { event ->
                            DebugButton(
                                text = stringResource(event.buttonLabelRes),
                                onClick = { controls.addReasonEvent(event) },
                                modifier = Modifier.weight(1f),
                                filled = false,
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.debug_section_drive)) {
                SwitchRow(stringResource(R.string.debug_risk_time), state.isRiskTimeZone, controls::setRiskTimeZone)
                DebugButton(
                    text = stringResource(R.string.debug_add_drive),
                    onClick = { controls.addContinuousDrive(30) },
                    filled = false,
                )
                SwitchRow(stringResource(R.string.debug_gear_p), state.isParked, controls::setParked)
            }

            Section(stringResource(R.string.debug_section_voice)) {
                VoiceState.entries.chunked(VOICE_BUTTONS_PER_ROW).forEach { row ->
                    ButtonRow {
                        row.forEach { voice ->
                            DebugButton(
                                text = voice.name,
                                onClick = { controls.setVoiceState(voice) },
                                modifier = Modifier.weight(1f),
                                filled = state.voiceState == voice,
                            )
                        }
                        repeat(VOICE_BUTTONS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                SwitchRow(stringResource(R.string.debug_alarm), state.isAlarmRunning, controls::setAlarm)
            }

            Section(stringResource(R.string.debug_section_vehicle)) {
                ButtonRow {
                    DebugButton(
                        stringResource(R.string.debug_vehicle_window),
                        { controls.triggerVehicleControl(blockedByRain = false) },
                        Modifier.weight(1f),
                        filled = false,
                    )
                    DebugButton(
                        stringResource(R.string.debug_vehicle_rain),
                        { controls.triggerVehicleControl(blockedByRain = true) },
                        Modifier.weight(1f),
                        filled = false,
                    )
                }
            }

            Section(stringResource(R.string.debug_section_rest)) {
                ButtonRow {
                    DebugButton(stringResource(R.string.debug_rest_add), { controls.setRestStop(true) }, Modifier.weight(1f), filled = false)
                    DebugButton(stringResource(R.string.debug_rest_remove), { controls.setRestStop(false) }, Modifier.weight(1f), filled = false)
                }
            }

            HorizontalDivider(color = KkaeuColors.Outline)
            DebugButton(stringResource(R.string.debug_reset), controls::resetAll, Modifier.fillMaxWidth(), filled = false)
        }
    }
}

private const val VOICE_BUTTONS_PER_ROW = 3

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = KkaeuColors.Outline)
        Text(
            text = title,
            style = KkaeuType.Debug.copy(fontWeight = FontWeight.Bold),
            color = KkaeuColors.TextSecondary,
        )
        content()
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = KkaeuType.Debug, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DebugButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val label: @Composable () -> Unit = {
        Text(text = text, style = KkaeuType.Debug, textAlign = TextAlign.Center)
    }
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 64.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = KkaeuColors.LevelNormal,
                contentColor = KkaeuColors.OnLight,
            ),
        ) { label() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 64.dp),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KkaeuColors.TextPrimary),
        ) { label() }
    }
}
