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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.screens.EDGE_LEFT_PX
import com.sleepreaper.kkaeurang.ui.screens.EDGE_RIGHT_PX
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/** 헤더 높이 (시안 px) */
const val HEADER_HEIGHT_PX = 150

/** 헤더 양옆 알약의 위쪽 여백과 높이 (시안 px). 왼쪽 위험 시간대와 오른쪽 음성 상태가 같은 줄에 오도록 둘 다 이 높이 가운데에 둔다 */
private const val SIDE_TOP_PX = 33
private const val PILL_HEIGHT_PX = 56

/**
 * 모든 화면 위쪽 공통 헤더 (시안 높이 150px).
 * 왼쪽: 위험 시간대 (해당 시간에만) / 가운데: 로고 + "졸음보다 한발 먼저, 깨우랑" / 오른쪽: 음성 상태
 * 주행 시간은 홈 "현재 주행 로그" 카드에, 경고음 켜고 끄기는 설정 탭에 있다.
 * 왼쪽·오른쪽 끝은 아래 카드들의 좌우 끝([EDGE_LEFT_PX]·[EDGE_RIGHT_PX])에 맞춘다.
 *
 * @param onLogoLongPress 로고를 길게 누르면 호출 (디버그 패널). null이면 아무 일 없음
 */
@Composable
fun KkaeuHeader(
    state: DrowsinessUiState,
    onLogoLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(d.dp(HEADER_HEIGHT_PX)),
    ) {
        if (state.isRiskTimeZone) {
            RiskTimeChip(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = d.dp(EDGE_LEFT_PX), top = d.dp(SIDE_TOP_PX))
                    .height(d.dp(PILL_HEIGHT_PX)),
            )
        }
        HeaderLogo(
            onLongPress = onLogoLongPress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = d.dp(18)),
        )
        VoicePill(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = d.dp(EDGE_RIGHT_PX), top = d.dp(SIDE_TOP_PX)),
        )
    }
}

/** "🌙 위험 시간대" (졸음 위험 시간대일 때만) */
@Composable
private fun RiskTimeChip(modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(14)),
    ) {
        KIcon(R.drawable.ic_ms_bedtime, d.dp(28), KkaeuColors.TextSecondary)
        Text(stringResource(R.string.main_risk_time), style = d.text(22, W.SemiBold, KkaeuColors.TextSecondary))
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
        modifier = modifier
            .height(d.dp(PILL_HEIGHT_PX))
            .clip(shape)
            .background(fill)
            .border(d.dp(1.5f), KkaeuColors.PillBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** 🎤 대기 / 말하는 중… / 듣는 중… / "괜찮아" / 응답 없음 / 인식 불가 / 알람 중 */
@Composable
private fun VoicePill(state: DrowsinessUiState, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    // 출발 전 컨디션 질문을 묻고 듣는 동안에는 그 상태를 보여 준다
    val voice = state.startCheckIn?.voiceState?.takeIf { it == VoiceState.ASKING || it == VoiceState.LISTENING }
        ?: state.voiceState
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
    HeaderPill(
        modifier = modifier,
        fill = if (active) AccentBrush else Brush.verticalGradient(listOf(KkaeuColors.PillFill, KkaeuColors.PillFill)),
    ) {
        Spacer(Modifier.width(d.dp(26)))
        KIcon(icon, d.dp(28), KkaeuColors.TextPrimary)
        Spacer(Modifier.width(d.dp(14)))
        Text(label, style = d.text(21, W.SemiBold, KkaeuColors.TextPrimary), maxLines = 1)
        Spacer(Modifier.width(d.dp(30)))
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
