package com.sleepreaper.kkaeurang.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.model.RestStopInfo
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.model.Weather
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import com.sleepreaper.kkaeurang.ui.theme.levelColor
import com.sleepreaper.kkaeurang.ui.theme.onWarningScreen
import com.sleepreaper.kkaeurang.ui.theme.warningScreenBg

// 메인 화면 네 모서리. 계기판을 침범하지 않도록 작게, 아이콘 + 짧은 글자로만.
// 아이콘은 Material Symbols Rounded (채움), res/drawable/ic_ms_*.xml

private val PillHeight = 72.dp
private val PillShape = RoundedCornerShape(36.dp)
private val CornerCardShape = RoundedCornerShape(28.dp)
private val IconSize = 36.dp

/** 오른쪽 위 카드 폭. 계기판 호와 닿지 않는 폭이다 (1707×1210dp 앱 영역 기준) */
private val TopRightCardWidth = 400.dp

/** 왼쪽 아래 차량 제어 알림 폭. 가운데 로그 구분선과 닿지 않는 폭이다 */
private val VehicleNoticeWidth = 440.dp

@Composable
private fun Sym(
    @DrawableRes id: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = IconSize,
    contentDescription: String? = null,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}

// ───────────────────────── 왼쪽 위: 연속 주행 시간 · 위험 시간대 ─────────────────────────

/** "1시간 12분 주행 중" + (위험 시간대일 때) 작은 배지. 2시간을 넘기면 노란색으로 강조 */
@Composable
fun DriveTimeCorner(continuousDriveMin: Int, isRiskTimeZone: Boolean, modifier: Modifier = Modifier) {
    val long = continuousDriveMin >= DrowsyThresholds.LONG_DRIVE_MIN
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Sym(R.drawable.ic_ms_schedule, if (long) KkaeuColors.LevelCaution else KkaeuColors.TextSecondary)
            Text(
                text = stringResource(R.string.main_drive_time, durationText(continuousDriveMin)),
                style = KkaeuType.Corner,
                color = if (long) KkaeuColors.LevelCaution else KkaeuColors.TextPrimary,
            )
        }
        if (isRiskTimeZone) {
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(lerp(KkaeuColors.Background, KkaeuColors.RiskBadge, 0.16f))
                    .height(48.dp)
                    .padding(start = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Sym(R.drawable.ic_ms_bedtime, KkaeuColors.RiskBadge, size = 28.dp)
                Text(
                    text = stringResource(R.string.main_risk_time),
                    style = KkaeuType.CornerSmall,
                    color = KkaeuColors.RiskBadge,
                )
            }
        }
    }
}

// ───────────────────────── 오른쪽 위: 음성 상태 · 경고음 ─────────────────────────

/**
 * 음성 상태 알약과 경고음 스위치. 앱이 질문하면 그 아래에 질문과 "괜찮아요"(음성의 백업)가,
 * 경고 이상이면 고대비 경고 카드가 나온다.
 */
@Composable
fun VoiceCorner(
    state: DrowsinessUiState,
    onToggleWarningSound: () -> Unit,
    onAnswerOkay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VoiceStatusPill(state)
            WarningSoundPill(state.warningSoundOn, onToggleWarningSound)
        }
        when {
            state.level >= DrowsyLevel.WARNING ->
                WarningCard(state, onAnswerOkay, Modifier.width(TopRightCardWidth))

            state.voiceState.showsVoicePanel() && state.voiceState != VoiceState.RESULT ->
                QuestionCard(state, onAnswerOkay, Modifier.width(TopRightCardWidth))
        }
    }
}

@Composable
private fun VoiceStatusPill(state: DrowsinessUiState) {
    val voice = state.voiceState
    val (icon, label) = when (voice) {
        VoiceState.IDLE -> R.drawable.ic_ms_mic to stringResource(R.string.main_voice_idle)
        VoiceState.ASKING -> R.drawable.ic_ms_record_voice_over to stringResource(R.string.main_voice_asking)
        VoiceState.LISTENING -> R.drawable.ic_ms_mic to stringResource(R.string.main_voice_listening)
        VoiceState.RESULT -> R.drawable.ic_ms_mic to stringResource(R.string.main_voice_quote, state.recognizedText.orEmpty())
        VoiceState.NO_RESPONSE -> R.drawable.ic_ms_mic to stringResource(R.string.main_voice_no_response)
        VoiceState.FAILED -> R.drawable.ic_ms_mic to stringResource(R.string.main_voice_failed)
        VoiceState.ALARM -> R.drawable.ic_ms_notifications_active to stringResource(R.string.main_voice_alarm)
    }
    // 말하는 중·듣는 중·인식 결과·알람은 지금 단계색으로 채워서 눈에 띄게
    val active = voice == VoiceState.ASKING || voice == VoiceState.LISTENING ||
        voice == VoiceState.RESULT || voice == VoiceState.ALARM
    val fill = if (active) levelColor(state.level) else KkaeuColors.SurfaceHigh
    val onFill = when {
        !active -> KkaeuColors.TextPrimary
        state.level == DrowsyLevel.CRITICAL -> KkaeuColors.OnCriticalScreen
        else -> KkaeuColors.OnLight
    }
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(fill)
            .height(PillHeight)
            .padding(start = 22.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sym(icon, if (active) onFill else KkaeuColors.TextSecondary, size = 34.dp)
        Text(text = label, style = KkaeuType.Corner, color = onFill, maxLines = 1)
    }
}

/** "경고음" + 스위치. 알약 전체를 눌러도 켜고 끈다 */
@Composable
private fun WarningSoundPill(on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(KkaeuColors.SurfaceHigh)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .height(PillHeight)
            .padding(start = 22.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sym(if (on) R.drawable.ic_ms_volume_up else R.drawable.ic_ms_volume_off, KkaeuColors.TextSecondary, size = 34.dp)
        Text(text = stringResource(R.string.main_warning_sound), style = KkaeuType.Corner)
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KkaeuColors.Background,
                checkedTrackColor = KkaeuColors.TextPrimary,
                checkedBorderColor = KkaeuColors.TextPrimary,
                uncheckedThumbColor = KkaeuColors.TextSecondary,
                uncheckedTrackColor = KkaeuColors.Background,
                uncheckedBorderColor = KkaeuColors.Outline,
            ),
        )
    }
}

/** 질문 문장 + "괜찮아요" (음성이 안 될 때의 백업) */
@Composable
private fun QuestionCard(state: DrowsinessUiState, onAnswerOkay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CornerCardShape)
            .background(KkaeuColors.SurfaceHigh)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.main_voice_quote, state.voiceQuestion ?: stringResource(R.string.voice_default_question)),
            style = KkaeuType.Corner,
        )
        when (state.voiceState) {
            VoiceState.NO_RESPONSE -> Text(stringResource(R.string.voice_no_response), style = KkaeuType.CornerSmall, color = KkaeuColors.TextSecondary)
            VoiceState.FAILED -> Text(stringResource(R.string.voice_failed), style = KkaeuType.CornerSmall, color = KkaeuColors.TextSecondary)
            else -> Unit
        }
        MediumButton(
            text = stringResource(R.string.btn_okay),
            onClick = onAnswerOkay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 경고·위험: 고대비 카드 (깜빡임 없음). 행동이 필요한 정보와 "괜찮아요"만 */
@Composable
private fun WarningCard(state: DrowsinessUiState, onAnswerOkay: () -> Unit, modifier: Modifier = Modifier) {
    val bg = warningScreenBg(state.level)
    val fg = onWarningScreen(state.level)
    Column(
        modifier = modifier
            .clip(CornerCardShape)
            .background(bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.warning_title),
            style = KkaeuType.CornerStrong.copy(fontSize = 34.sp, lineHeight = 42.sp),
            color = fg,
        )
        state.forcedWarningRemainSec?.let { sec ->
            Text(stringResource(R.string.warning_forced, clockText(sec)), style = KkaeuType.CornerSmall, color = fg)
        }
        when (state.voiceState) {
            VoiceState.ASKING -> Text(
                text = stringResource(R.string.main_voice_quote, state.voiceQuestion ?: stringResource(R.string.voice_default_question)),
                style = KkaeuType.Corner,
                color = fg,
            )

            VoiceState.LISTENING -> Text(
                text = stringResource(R.string.voice_listening),
                style = KkaeuType.Corner.copy(fontWeight = FontWeight.Bold),
                color = fg,
            )

            else -> Unit
        }
        MediumButton(
            text = stringResource(R.string.btn_okay),
            onClick = onAnswerOkay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            containerColor = fg,
            contentColor = bg,
        )
    }
}

// ───────────────────────── 왼쪽 아래: 창문 · 공조 · 날씨 ─────────────────────────

@Composable
fun ClimateCorner(
    windowOpen: Boolean,
    outsideAirOn: Boolean,
    weather: Weather,
    vehicleEvent: VehicleControlEvent?,
    onCancelVehicleControl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 차량 제어가 막 일어났으면 5초간 그 위에 알림
        VehicleControlBanner(vehicleEvent, onCancelVehicleControl, Modifier.width(VehicleNoticeWidth))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            val (weatherIcon, weatherName) = weather.visual()
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(KkaeuColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Sym(weatherIcon, KkaeuColors.TextPrimary, size = 52.dp, contentDescription = weatherName)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CornerLine(
                    R.drawable.ic_ms_sensor_window,
                    stringResource(if (windowOpen) R.string.main_window_open else R.string.main_window_closed),
                )
                CornerLine(
                    R.drawable.ic_ms_air,
                    stringResource(if (outsideAirOn) R.string.main_outside_air_on else R.string.main_outside_air_off),
                )
            }
        }
    }
}

@Composable
private fun Weather.visual(): Pair<Int, String> = when (this) {
    Weather.CLEAR -> R.drawable.ic_ms_sunny to stringResource(R.string.main_weather_clear)
    Weather.PARTLY_CLOUDY -> R.drawable.ic_ms_partly_cloudy_day to stringResource(R.string.main_weather_partly_cloudy)
    Weather.CLOUDY -> R.drawable.ic_ms_cloud to stringResource(R.string.main_weather_cloudy)
    Weather.RAIN -> R.drawable.ic_ms_rainy to stringResource(R.string.main_weather_rain)
    Weather.SNOW -> R.drawable.ic_ms_weather_snowy to stringResource(R.string.main_weather_snow)
}

@Composable
private fun CornerLine(@DrawableRes icon: Int, text: String, color: Color = KkaeuColors.TextPrimary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Sym(icon, KkaeuColors.TextSecondary)
        Text(text = text, style = KkaeuType.Corner, color = color, maxLines = 1)
    }
}

// ───────────────────────── 오른쪽 아래: 가까운 쉼터 · 경유지 추가 ─────────────────────────

/** 가장 가까운 졸음쉼터·휴게소. 경유지로 추가되면 버튼 자리에 "경유지 추가됨" */
@Composable
fun RestStopCorner(info: RestStopInfo?, onAddWaypoint: () -> Unit, modifier: Modifier = Modifier) {
    if (info == null) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Sym(R.drawable.ic_ms_local_cafe, KkaeuColors.TextSecondary)
            Text(text = info.name, style = KkaeuType.Corner, maxLines = 1)
            Text(
                text = stringResource(R.string.main_rest_distance, info.distanceKm),
                style = KkaeuType.CornerStrong,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (info.isWaypointAdded) {
            Row(
                modifier = Modifier.height(PillHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Sym(R.drawable.ic_ms_check_circle, KkaeuColors.LevelNormal, size = 34.dp)
                Text(stringResource(R.string.main_waypoint_added), style = KkaeuType.Corner, color = KkaeuColors.LevelNormal)
            }
        } else {
            OutlinedButton(
                onClick = onAddWaypoint,
                modifier = Modifier.heightIn(min = PillHeight),
                shape = PillShape,
                border = BorderStroke(2.dp, KkaeuColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = KkaeuColors.SurfaceHigh,
                    contentColor = KkaeuColors.TextPrimary,
                ),
                contentPadding = PaddingValues(start = 22.dp, end = 28.dp),
            ) {
                Sym(R.drawable.ic_ms_add_location_alt, KkaeuColors.TextPrimary, size = 34.dp)
                Text(
                    text = stringResource(R.string.rest_add_waypoint),
                    style = KkaeuType.Corner,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
