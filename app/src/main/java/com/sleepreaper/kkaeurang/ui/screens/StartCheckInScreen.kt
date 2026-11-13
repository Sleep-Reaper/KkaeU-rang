package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.CardIconBrush
import com.sleepreaper.kkaeurang.ui.components.KGradientIcon
import com.sleepreaper.kkaeurang.ui.components.KIcon
import com.sleepreaper.kkaeurang.ui.components.PagePillButton
import com.sleepreaper.kkaeurang.ui.components.SoundArcs
import com.sleepreaper.kkaeurang.ui.components.SparkLines
import com.sleepreaper.kkaeurang.ui.components.sleepRangeLabel
import com.sleepreaper.kkaeurang.ui.components.circleGlow
import com.sleepreaper.kkaeurang.ui.components.lavenderCard
import com.sleepreaper.kkaeurang.ui.model.SleepRange
import com.sleepreaper.kkaeurang.ui.model.StartCheckIn
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/**
 * 앱을 시작하면 보이는 출발 전 컨디션 질문.
 * 깨우랑이 "마지막으로 몇 시간 주무셨어요?"라고 묻고 (마지막 수면 시간),
 * 운전자는 말로 대답하거나 구간 버튼(4시간 미만 / 4~6시간 / 6~8시간 / 8시간 이상)을 누른다.
 * 대답에 따라 오늘의 시작 점수가 정해지고, 대답이 없거나 건너뛰면 100점에서 시작한다 (공급자가 처리).
 */
@Composable
fun StartCheckInScreen(
    checkIn: StartCheckIn,
    onAnswer: (SleepRange) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.img_mascot),
            contentDescription = null,
            modifier = Modifier
                .offset(x = d.dp(-12), y = d.dp(250))
                .size(d.dp(410), d.dp(346))
                .alpha(0.34f),
            contentScale = ContentScale.Fit,
        )
        // 본문 가운데에 모은다 (아래 하단 탭 위까지 고르게 여백)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                CheckInBadge()
                SparkLines(
                    d.dp(54),
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = d.dp(60), y = d.dp(-20)),
                )
            }
            Spacer(Modifier.height(d.dp(26)))
            // 깨우랑이 묻는 문장 한 줄
            SpokenLine(
                text = checkIn.question,
                sizePx = 46,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.dp(60)),
            )
            Spacer(Modifier.height(d.dp(28)))
            VoiceStatus(checkIn)
            Spacer(Modifier.height(d.dp(40)))
            Row(horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                SleepRange.entries.forEach { range ->
                    RangeButton(
                        range = range,
                        heard = checkIn.heardRange == range,
                        onClick = { onAnswer(range) },
                    )
                }
            }
            Spacer(Modifier.height(d.dp(36)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(24))) {
                Text(
                    text = checkIn.autoStartSec?.let { stringResource(R.string.checkin_auto_start, it) }
                        ?: stringResource(R.string.checkin_hint),
                    style = d.text(21, W.SemiBold, if (checkIn.autoStartSec != null) KkaeuColors.Yellow else KkaeuColors.TextSecondary),
                )
                PagePillButton(text = stringResource(R.string.checkin_skip), onClick = onSkip)
            }
        }
    }
}

@Composable
private fun CheckInBadge() {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(d.dp(62))
            .clip(shape)
            .background(AccentBrush)
            .border(d.dp(2), Color(0x66FFFFFF), shape)
            .padding(start = d.dp(22), end = d.dp(32)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(12)),
    ) {
        KIcon(R.drawable.ic_ms_bedtime, d.dp(34), Color.White)
        Text(stringResource(R.string.checkin_badge), style = d.text(30, W.ExtraBold, Color.White))
    }
}

/** 마이크 + 지금 상태 (묻는 중 / 말씀해 주세요 / 알아들음 / 대답 없음) */
@Composable
private fun VoiceStatus(checkIn: StartCheckIn) {
    val d = LocalDesign.current
    val voice = checkIn.voiceState
    val text = when (voice) {
        VoiceState.ASKING -> stringResource(R.string.checkin_asking)
        VoiceState.LISTENING -> stringResource(R.string.checkin_listening)
        VoiceState.RESULT -> stringResource(
            R.string.checkin_result,
            checkIn.heardText.orEmpty(),
            checkIn.heardRange?.let { sleepRangeLabel(it) }.orEmpty(),
        )
        VoiceState.NO_RESPONSE -> stringResource(R.string.checkin_no_response)
        VoiceState.FAILED -> checkIn.heardText?.let { stringResource(R.string.checkin_failed_heard, it) }
            ?: stringResource(R.string.checkin_failed)
        VoiceState.IDLE, VoiceState.ALARM -> stringResource(R.string.checkin_idle)
    }
    val active = voice == VoiceState.ASKING || voice == VoiceState.LISTENING
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(d.dp(200), d.dp(96)), contentAlignment = Alignment.Center) {
            if (voice == VoiceState.LISTENING) {
                SoundArcs(d.dp(46), d.dp(80), left = true, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(-72)), glow = true)
                SoundArcs(d.dp(46), d.dp(80), left = false, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(72)), glow = true)
            }
            Box(
                Modifier
                    .size(d.dp(88))
                    .circleGlow(Color(0x998B5CF6))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF9E80FF), Color(0xFF6C47F2), Color(0xFF5835D8))))
                    .border(d.dp(3.5f), Color(0xD9FFFFFF), CircleShape)
                    .alpha(if (active) 1f else 0.6f),
                contentAlignment = Alignment.Center,
            ) {
                KIcon(R.drawable.ic_ms_mic, d.dp(42), Color.White)
            }
        }
        Text(
            text = text,
            style = d.text(27, W.SemiBold, KkaeuColors.TextPrimary.copy(alpha = 0.92f)),
            maxLines = 1,
        )
    }
}

/** 구간 버튼: "4~6시간" + "95점으로 시작". 말로 알아들은 구간은 보라 테두리로 강조 */
@Composable
private fun RangeButton(range: SleepRange, heard: Boolean, onClick: () -> Unit) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(d.dp(26))
    Column(
        modifier = Modifier
            .width(d.dp(280))
            .height(d.dp(168))
            .lavenderCard(d.dp(26))
            .then(if (heard) Modifier.border(d.dp(4), KkaeuColors.Accent, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = d.dp(18)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.dp(6), Alignment.CenterVertically),
    ) {
        KGradientIcon(R.drawable.ic_k_clock_outline, d.dp(40), CardIconBrush)
        Text(
            sleepRangeLabel(range),
            style = d.text(32, W.ExtraBold, KkaeuColors.CardText),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            stringResource(R.string.checkin_start_score, range.startSafety),
            style = d.text(20, W.Bold, KkaeuColors.AccentDeep),
            maxLines = 1,
        )
    }
}
