package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/** 헤더 높이 (시안 px) */
const val HEADER_HEIGHT_PX = 150

/**
 * 모든 화면 위쪽 공통 헤더 (시안 높이 150px).
 * 왼쪽: 연속 주행 시간 · 위험 시간대 / 가운데: 로고 + "졸음보다 한발 먼저, 깨우랑" / 오른쪽: 음성 상태 · 경고음 스위치
 *
 * @param onLogoLongPress 로고를 길게 누르면 호출 (디버그 패널). null이면 아무 일 없음
 */
@Composable
fun KkaeuHeader(
    state: DrowsinessUiState,
    onToggleWarningSound: () -> Unit,
    onLogoLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(d.dp(HEADER_HEIGHT_PX)),
    ) {
        DriveInfo(
            state = state,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = d.dp(50), top = d.dp(31)),
        )
        HeaderLogo(
            onLongPress = onLogoLongPress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = d.dp(18)),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = d.dp(38), top = d.dp(33)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(20)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoicePill(state)
            WarningSoundPill(on = state.warningSoundOn, onToggle = onToggleWarningSound)
        }
    }
}

/** "🕐 1시간 12분 주행 중" + "🌙 위험 시간대" */
@Composable
private fun DriveInfo(state: DrowsinessUiState, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val long = state.continuousDriveMin >= DrowsyThresholds.LONG_DRIVE_MIN
    val color = if (long) KkaeuColors.Yellow else KkaeuColors.TextPrimary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(d.dp(14))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(14))) {
            KIcon(R.drawable.ic_ms_schedule, d.dp(28), color)
            Text(
                text = if (state.isParked) stringResource(R.string.header_parked)
                else stringResource(R.string.main_drive_time, durationText(state.continuousDriveMin)),
                style = d.text(22, W.Bold, color),
            )
        }
        if (state.isRiskTimeZone) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(14))) {
                KIcon(R.drawable.ic_ms_bedtime, d.dp(28), KkaeuColors.TextSecondary)
                Text(stringResource(R.string.main_risk_time), style = d.text(21, W.SemiBold, KkaeuColors.TextSecondary))
            }
        }
    }
}

/** 마스코트 로고 + "깨우랑" + 태그라인. 길게 누르면 디버그 패널 */
@Composable
private fun HeaderLogo(onLongPress: (() -> Unit)?, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Column(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { currentOnLongPress?.invoke() })
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.img_logo_mascot),
                contentDescription = stringResource(R.string.header_logo_description),
                modifier = Modifier.size(d.dp(121), d.dp(82)),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = stringResource(R.string.logo_text),
                style = d.text(50, W.ExtraBold, KkaeuColors.TextPrimary, lineHeight = 1.1f, italic = true),
                modifier = Modifier.offset(x = d.dp(-4)),
            )
        }
        Row(
            modifier = Modifier.offset(y = d.dp(-4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(30)),
        ) {
            TaglineRule()
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.header_tagline))
                    append(' ')
                    withStyle(SpanStyle(color = KkaeuColors.Yellow, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(stringResource(R.string.logo_text))
                    }
                },
                style = d.text(23, W.Bold, KkaeuColors.TextPrimary),
            )
            TaglineRule()
        }
    }
}

@Composable
private fun TaglineRule() {
    val d = LocalDesign.current
    Box(
        Modifier
            .width(d.dp(50))
            .height(d.dp(2.5f))
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0xCCFFFFFF), Color(0x33FFFFFF))))
    )
}

/** 반투명 알약 (헤더 오른쪽) */
@Composable
private fun HeaderPill(
    modifier: Modifier = Modifier,
    fill: Brush = Brush.verticalGradient(listOf(KkaeuColors.PillFill, KkaeuColors.PillFill)),
    content: @Composable () -> Unit,
) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(d.dp(56))
            .clip(shape)
            .background(fill)
            .border(d.dp(1.5f), KkaeuColors.PillBorder, shape)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** 🎤 대기 / 말하는 중… / 듣는 중… / "괜찮아" / 응답 없음 / 인식 불가 / 알람 중 */
@Composable
private fun VoicePill(state: DrowsinessUiState) {
    val d = LocalDesign.current
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
    // 말하는 중·듣는 중·알람은 보라색으로 채워서 눈에 띄게
    val active = voice == VoiceState.ASKING || voice == VoiceState.LISTENING || voice == VoiceState.ALARM
    HeaderPill(fill = if (active) AccentBrush else Brush.verticalGradient(listOf(KkaeuColors.PillFill, KkaeuColors.PillFill))) {
        Spacer(Modifier.width(d.dp(26)))
        KIcon(icon, d.dp(28), KkaeuColors.TextPrimary)
        Spacer(Modifier.width(d.dp(14)))
        Text(label, style = d.text(21, W.SemiBold, KkaeuColors.TextPrimary), maxLines = 1)
        Spacer(Modifier.width(d.dp(30)))
    }
}

/** 🔊 경고음 [스위치]. 알약 전체를 눌러도 켜고 끈다 */
@Composable
private fun WarningSoundPill(on: Boolean, onToggle: () -> Unit) {
    val d = LocalDesign.current
    HeaderPill(modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })) {
        Spacer(Modifier.width(d.dp(24)))
        KIcon(if (on) R.drawable.ic_ms_volume_up else R.drawable.ic_ms_volume_off, d.dp(28), KkaeuColors.TextPrimary)
        Spacer(Modifier.width(d.dp(12)))
        Text(stringResource(R.string.main_warning_sound), style = d.text(21, W.SemiBold, KkaeuColors.TextPrimary))
        Spacer(Modifier.width(d.dp(22)))
        KkaeuSwitch(on)
        Spacer(Modifier.width(d.dp(8)))
    }
}

/** 시안의 보라 스위치 (모양만. 누르는 처리는 감싸는 쪽에서) */
@Composable
fun KkaeuSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(
        modifier = modifier
            .size(d.dp(66), d.dp(40))
            .clip(RoundedCornerShape(50))
            .background(if (on) Brush.horizontalGradient(listOf(KkaeuColors.Accent, KkaeuColors.AccentLight)) else Brush.verticalGradient(listOf(KkaeuColors.ToggleOff, KkaeuColors.ToggleOff)))
            .padding(d.dp(5)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(d.dp(30))
                .clip(CircleShape)
                .background(if (on) Color.White else KkaeuColors.TextSecondary)
        )
    }
}
