package com.sleepreaper.kkaeurang.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.labelRes
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import kotlin.math.roundToInt

/**
 * 실제 모드(판단 엔진 연결)용 디버그 패널. 계기판을 길게 누르면 왼쪽에 열린다.
 *
 * 화면을 가리지 않도록 어두운 배경을 깔지 않는다 → 패널을 연 채로 계기판·음성·알람이 바뀌는 것을 보고,
 * 화면의 "괜찮아요" 버튼도 그대로 누를 수 있다. 닫기 버튼으로만 닫힌다.
 */
@Composable
fun LiveDebugPanel(
    controls: LiveDebugControls,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by controls.liveDebugState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(PANEL_WIDTH_FRACTION)
                .clip(RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
                .background(KkaeuColors.SurfaceHigh.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.live_debug_title),
                    style = KkaeuType.Title,
                    modifier = Modifier.weight(1f),
                )
                DebugButton(stringResource(R.string.btn_close), onClose, filled = false)
            }

            // ── 엔진 상태 ──
            Text(
                text = stringResource(R.string.live_debug_score, s.score, stringResource(s.level.labelRes())),
                style = KkaeuType.Body.copy(fontWeight = FontWeight.Bold),
                color = s.level.color(),
            )
            DebugLine(
                stringResource(
                    if (s.isPark) R.string.live_debug_vehicle_parked else R.string.live_debug_vehicle,
                    s.speedKmh.roundToInt(),
                    s.steeringDeg,
                    stringResource(if (s.turnSignalOn) R.string.live_debug_on else R.string.live_debug_off),
                    stringResource(if (s.brakeOn) R.string.live_debug_on else R.string.live_debug_off),
                )
            )
            DebugLine(stringResource(R.string.live_debug_clock, s.clockText, s.clockOffsetMin))
            if (!s.simulated) DebugLine(stringResource(R.string.live_debug_real_signals), KkaeuColors.LevelCaution)

            // ── 점수 직접 조절 ──
            DebugSection(stringResource(R.string.live_debug_section_score)) {
                DebugButtonRow {
                    SCORE_STEPS.forEach { delta ->
                        DebugButton(
                            text = if (delta > 0) "+$delta" else "$delta",
                            onClick = { controls.adjustScore(delta) },
                            modifier = Modifier.weight(1f),
                            filled = false,
                        )
                    }
                }
                DebugButton(
                    stringResource(R.string.live_debug_score_reset),
                    { controls.adjustScore(-100) },
                    Modifier.fillMaxWidth(),
                    filled = false,
                )
            }

            // ── 가짜 차량 신호로 규칙 ①~④ 재생 ──
            if (s.simulated) {
                DebugSection(stringResource(R.string.live_debug_section_scenario)) {
                    DriveScenario.entries.chunked(2).forEach { row ->
                        DebugButtonRow {
                            row.forEach { scn ->
                                DebugButton(
                                    text = stringResource(scn.labelRes),
                                    onClick = { controls.runScenario(scn) },
                                    modifier = Modifier.weight(1f),
                                    filled = s.scenario == scn,
                                )
                            }
                        }
                    }
                    s.scenario?.let {
                        DebugLine(stringResource(R.string.live_debug_scenario_running, stringResource(it.labelRes)), KkaeuColors.LevelNormal)
                    }
                    DebugSwitchRow(stringResource(R.string.debug_gear_p), s.isPark, controls::setParked)
                    DebugSwitchRow(stringResource(R.string.live_debug_turn_signal), s.turnSignalOn, controls::setTurnSignal)
                    DebugSwitchRow(stringResource(R.string.live_debug_brake), s.brakeOn, controls::setBrake)
                }
            }

            // ── 시간 이동 ──
            DebugSection(stringResource(R.string.live_debug_section_time)) {
                DebugButtonRow {
                    DebugButton(stringResource(R.string.live_debug_time_10m), { controls.advanceTime(10) }, Modifier.weight(1f), filled = false)
                    DebugButton(stringResource(R.string.live_debug_time_1h), { controls.advanceTime(60) }, Modifier.weight(1f), filled = false)
                }
                DebugButton(
                    stringResource(R.string.live_debug_time_risk),
                    controls::jumpToRiskTime,
                    Modifier.fillMaxWidth(),
                    filled = false,
                )
            }

            // ── 운전자 대답 ──
            DebugSection(
                stringResource(if (s.isAsking) R.string.live_debug_section_answer_asking else R.string.live_debug_section_answer_idle)
            ) {
                DebugButtonRow {
                    DebugAnswer.entries.forEach { a ->
                        DebugButton(
                            text = stringResource(a.labelRes),
                            onClick = { controls.answer(a) },
                            modifier = Modifier.weight(1f),
                            filled = s.isAsking,
                        )
                    }
                }
            }

            // ── 엔진 사건 로그 ──
            DebugSection(stringResource(R.string.live_debug_section_log)) {
                if (s.logs.isEmpty()) {
                    DebugLine(stringResource(R.string.live_debug_log_empty))
                } else {
                    s.logs.forEach { DebugLine(it) }
                }
            }
        }
    }
}

@Composable
private fun DebugLine(text: String, color: Color = KkaeuColors.TextSecondary) {
    Text(text = text, style = KkaeuType.Debug, color = color)
}

private fun DrowsyLevel.color() = when (this) {
    DrowsyLevel.NORMAL -> KkaeuColors.LevelNormal
    DrowsyLevel.CAUTION -> KkaeuColors.LevelCaution
    DrowsyLevel.WARNING -> KkaeuColors.LevelWarning
    DrowsyLevel.CRITICAL -> KkaeuColors.LevelCritical
}

private const val PANEL_WIDTH_FRACTION = 0.4f
private val SCORE_STEPS = listOf(-30, -10, 10, 30)
