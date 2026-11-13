package com.sleepreaper.kkaeurang.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.KIcon
import com.sleepreaper.kkaeurang.ui.components.LevelIcon
import com.sleepreaper.kkaeurang.ui.components.SoundArcs
import com.sleepreaper.kkaeurang.ui.components.SparkLines
import com.sleepreaper.kkaeurang.ui.components.circleGlow
import com.sleepreaper.kkaeurang.ui.components.clockText
import com.sleepreaper.kkaeurang.ui.components.labelRes
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import com.sleepreaper.kkaeurang.ui.theme.levelBadgeBrush
import com.sleepreaper.kkaeurang.ui.theme.levelBadgeText

/**
 * 경보 화면 (주의·경고·위험). 디자인 시안: 단계별 경보 화면 3종.
 * 홈 본문 자리를 통째로 바꾼다 (헤더·하단 탭은 그대로, 탭은 홈·설정만).
 * 단계가 바뀌면 즉시 바뀌고, 깜빡임·움직이는 효과는 쓰지 않는다 (주행 중).
 *
 * 깨우랑이 말하는(묻는) 문장은 단계마다 그림 없이 글자 한 줄로 보여 준다.
 * 운전자가 졸리지 않다고 알리면(괜찮다는 대답, "괜찮아요"·"터치로 응답하기" 버튼) 공급자가
 * alertDismissed를 켜고, 화면은 홈 계기판으로 돌아간다 ([isAlert]).
 *
 * - 주의: 질문 한 줄 + 마이크 (듣는 중이면 소리 물결) + 괜찮아요 버튼
 * - 경고: "지금 바로 반응해 주세요!" (음성으로 답하기 중이면 묻는 문장) + 음성으로 답하기 / 터치로 응답하기
 * - 위험: 깨우랑이 말한 안내 한 줄 + 창문 상태 · 차량 · 쉼터 경로 + 음성으로 답하기 / 괜찮아요
 */
@Composable
fun AlertScreen(
    state: DrowsinessUiState,
    onAnswerOkay: () -> Unit,
    onVoiceAnswer: () -> Unit,
    modifier: Modifier = Modifier,
    /** 위험 단계에서 열어 둔 창문·찬바람을 원래대로 되돌린다 */
    onCancelVehicleControl: () -> Unit = {},
) {
    val d = LocalDesign.current
    Box(modifier.fillMaxSize()) {
        // 왼쪽에 흐릿한 마스코트
        val critical = state.level == DrowsyLevel.CRITICAL
        Image(
            painter = painterResource(R.drawable.img_mascot),
            contentDescription = null,
            modifier = Modifier
                .offset(x = d.dp(-12), y = d.dp(if (critical) 96 else 250))
                .size(d.dp(if (critical) 330 else 410), d.dp(if (critical) 280 else 346))
                .alpha(0.34f),
            contentScale = ContentScale.Fit,
        )
        when (state.level) {
            DrowsyLevel.NORMAL, DrowsyLevel.CAUTION -> CautionContent(state, onAnswerOkay)
            DrowsyLevel.WARNING -> WarningContent(state, onAnswerOkay, onVoiceAnswer)
            DrowsyLevel.CRITICAL -> CriticalContent(state, onAnswerOkay, onVoiceAnswer, onCancelVehicleControl)
        }
    }
}

// ───────────────────────── 공통: 단계 배지 ─────────────────────────

@Composable
private fun StageBadge(level: DrowsyLevel, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    val border = when (level) {
        DrowsyLevel.CAUTION -> Color(0x80FFD54A)
        DrowsyLevel.WARNING -> Color(0x80FF9A55)
        DrowsyLevel.CRITICAL -> Color(0xB3FF6B78)
        DrowsyLevel.NORMAL -> Color(0x40FFFFFF)
    }
    Row(
        modifier = modifier
            .then(
                if (level == DrowsyLevel.CRITICAL) Modifier.drawBehind {
                    // 빨간 빛 번짐
                    drawRoundRect(
                        Brush.radialGradient(listOf(Color(0x66FF3B4D), Color.Transparent), center, size.width * 0.75f),
                        topLeft = Offset(-size.width * 0.25f, -size.height * 0.9f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.5f, size.height * 2.8f),
                    )
                } else Modifier
            )
            .height(d.dp(62))
            .clip(shape)
            .background(levelBadgeBrush(level))
            .border(d.dp(2), border, shape)
            .padding(start = d.dp(20), end = d.dp(32)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(14)),
    ) {
        if (level == DrowsyLevel.CRITICAL) {
            KIcon(R.drawable.ic_k_siren, d.dp(38), Color(0xFFFF8E98))
        } else {
            LevelIcon(level = level, modifier = Modifier.size(d.dp(36)), mark = KkaeuColors.OnLight)
        }
        Text(
            text = stringResource(R.string.alert_badge, stringResource(level.labelRes())),
            style = d.text(31, W.ExtraBold, levelBadgeText(level)),
        )
    }
}

/**
 * 깨우랑이 말하는(묻는) 문장을 글자 한 줄로. 화면보다 길면 한 줄에 들어갈 때까지 글자를 줄인다 ([minPx]까지).
 */
@Composable
internal fun SpokenLine(text: String, sizePx: Int, modifier: Modifier = Modifier, minPx: Int = 30) {
    val d = LocalDesign.current
    BasicText(
        text = text,
        modifier = modifier,
        style = d.text(sizePx, W.ExtraBold, KkaeuColors.TextPrimary, lineHeight = 1.2f).copy(textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = d.sp(minPx), maxFontSize = d.sp(sizePx), stepSize = d.sp(2)),
    )
}

private val QUESTION_STATES = setOf(VoiceState.ASKING, VoiceState.LISTENING)

// ───────────────────────── 주의 ─────────────────────────

@Composable
private fun CautionContent(state: DrowsinessUiState, onAnswerOkay: () -> Unit) {
    val d = LocalDesign.current
    val voice = state.voiceState
    // 주의 단계에서 깨우랑이 묻는 문장 (엔진 질문). 대답을 기다리는 동안에도 그대로 둔다
    val question = state.voiceQuestion ?: stringResource(R.string.voice_default_question)
    @StringRes val label = when (voice) {
        VoiceState.ASKING -> R.string.caution_asking
        VoiceState.LISTENING -> R.string.caution_listening
        VoiceState.RESULT -> R.string.caution_result
        VoiceState.NO_RESPONSE -> R.string.caution_no_response
        VoiceState.FAILED -> R.string.caution_failed
        VoiceState.IDLE, VoiceState.ALARM -> R.string.caution_idle_sub
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(d.dp(70)))
        BadgeWithSparks(DrowsyLevel.CAUTION)
        Spacer(Modifier.height(d.dp(30)))
        SpokenLine(
            text = question,
            sizePx = 52,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.dp(60)),
        )
        Spacer(Modifier.height(d.dp(40)))
        MicWithWaves(
            listening = voice == VoiceState.LISTENING,
            dimmed = voice == VoiceState.IDLE || voice == VoiceState.ALARM || voice == VoiceState.RESULT,
        )
        Text(
            text = stringResource(label),
            style = d.text(27, W.SemiBold, KkaeuColors.TextPrimary.copy(alpha = 0.9f)),
        )
        Spacer(Modifier.height(d.dp(18)))
        // 말 대신 눌러서 "괜찮다"고 알릴 수 있다 → 홈 계기판으로
        OkayPill(onAnswerOkay)
    }
}

@Composable
private fun BadgeWithSparks(level: DrowsyLevel) {
    val d = LocalDesign.current
    Box {
        StageBadge(level)
        SparkLines(
            d.dp(54),
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = d.dp(60), y = d.dp(-20)),
        )
    }
}

/** 보라 마이크 버튼 + 옅은 동심원 + (듣는 중이면) 양옆 소리 물결 */
@Composable
private fun MicWithWaves(listening: Boolean, dimmed: Boolean) {
    val d = LocalDesign.current
    Box(Modifier.size(d.dp(560), d.dp(260)), contentAlignment = Alignment.Center) {
        // 동심원
        Canvas(Modifier.size(d.dp(260))) {
            drawCircle(Color(0x14FFFFFF), radius = size.minDimension / 2f)
            drawCircle(Color(0x1FC9CCFF), radius = size.minDimension / 2f, style = Stroke(1.5f))
            drawCircle(Color(0x10FFFFFF), radius = size.minDimension * 0.39f)
        }
        if (listening) {
            SoundArcs(d.dp(90), d.dp(150), left = true, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(-190)), glow = true)
            SoundArcs(d.dp(90), d.dp(150), left = false, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(190)), glow = true)
        }
        MicCircle(diameter = d.dp(160), modifier = Modifier.alpha(if (dimmed) 0.55f else 1f))
    }
}

@Composable
private fun MicCircle(diameter: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val d = LocalDesign.current
    Box(
        modifier = modifier
            .size(diameter)
            .circleGlow(Color(0x998B5CF6))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF9E80FF), Color(0xFF6C47F2), Color(0xFF5835D8))))
            .border(d.dp(5), Color(0xD9FFFFFF), CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(R.drawable.ic_ms_mic, diameter * 0.46f, Color.White)
    }
}

@Composable
private fun OkayPill(onClick: () -> Unit) {
    val d = LocalDesign.current
    Text(
        text = stringResource(R.string.btn_okay),
        style = d.text(28, W.Bold, KkaeuColors.TextPrimary),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(KkaeuColors.AccentLight, KkaeuColors.Accent)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = d.dp(44), vertical = d.dp(14)),
    )
}

// ───────────────────────── 경고 ─────────────────────────

@Composable
private fun WarningContent(state: DrowsinessUiState, onAnswerOkay: () -> Unit, onVoiceAnswer: () -> Unit) {
    val d = LocalDesign.current
    val voice = state.voiceState
    val voiceLabel = when (voice) {
        VoiceState.ASKING -> stringResource(R.string.caution_asking)
        VoiceState.LISTENING -> stringResource(R.string.caution_listening)
        VoiceState.RESULT -> stringResource(R.string.main_voice_quote, state.recognizedText.orEmpty())
        else -> stringResource(R.string.warning_voice)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(d.dp(78)))
        StageBadge(DrowsyLevel.WARNING)
        Spacer(Modifier.height(d.dp(34)))
        // "음성으로 답하기" 중에는 깨우랑이 묻는 문장을, 아니면 경고 문구를 한 줄로
        val title = state.voiceQuestion?.takeIf { voice in QUESTION_STATES } ?: stringResource(R.string.warning_title)
        Box(Modifier.padding(horizontal = d.dp(90))) {
            SpokenLine(text = title, sizePx = 58)
            SparkLines(
                d.dp(58),
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = d.dp(66), y = d.dp(-34)),
            )
        }
        Box(Modifier.height(d.dp(40)), contentAlignment = Alignment.Center) {
            state.forcedWarningRemainSec?.let { sec ->
                Text(
                    stringResource(R.string.warning_forced, clockText(sec)),
                    style = d.text(22, W.SemiBold, KkaeuColors.TextSecondary),
                )
            }
        }
        Spacer(Modifier.height(d.dp(64)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResponseButton(label = voiceLabel, onClick = onVoiceAnswer) {
                Box(contentAlignment = Alignment.Center) {
                    if (voice == VoiceState.LISTENING) {
                        SoundArcs(d.dp(70), d.dp(130), left = true, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(-140)), glow = true)
                        SoundArcs(d.dp(70), d.dp(130), left = false, color = Color(0xFFC3BCFF), modifier = Modifier.offset(x = d.dp(140)), glow = true)
                    }
                    MicCircle(diameter = d.dp(178), onClick = onVoiceAnswer)
                }
            }
            Box(
                Modifier
                    .padding(horizontal = d.dp(92))
                    .width(d.dp(2))
                    .height(d.dp(250))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80C9CCFF), Color.Transparent)))
            )
            ResponseButton(label = stringResource(R.string.warning_touch), onClick = onAnswerOkay) {
                TouchCircle(diameter = d.dp(178), onClick = onAnswerOkay)
            }
        }
    }
}

@Composable
private fun ResponseButton(label: String, onClick: () -> Unit, circle: @Composable () -> Unit) {
    val d = LocalDesign.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.dp(22)),
        modifier = Modifier.width(d.dp(300)),
    ) {
        circle()
        Text(
            text = label,
            style = d.text(30, W.Bold, KkaeuColors.TextPrimary),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = d.dp(10), vertical = d.dp(4)),
        )
    }
}

/** 테두리만 있는 동그라미 + 누르는 손 + 주황 반짝 선 */
@Composable
private fun TouchCircle(diameter: Dp, onClick: () -> Unit) {
    val d = LocalDesign.current
    Box(
        modifier = Modifier
            .size(diameter)
            .circleGlow(Color(0x406E5BFF))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0x332A2F80), Color(0x661E2463))))
            .border(d.dp(4.5f), Brush.linearGradient(listOf(Color(0xFFD7D0FF), Color(0xFF9B8CFF))), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(R.drawable.ic_k_touch, diameter * 0.46f, Color.White, Modifier.offset(x = diameter * 0.03f, y = diameter * 0.04f))
        SparkLines(
            diameter * 0.30f,
            Modifier
                .align(Alignment.TopStart)
                .offset(x = diameter * 0.18f, y = diameter * 0.12f),
            color = Color(0xFFFF9A5C),
            angleDeg = -90f,
        )
    }
}

// ───────────────────────── 위험 ─────────────────────────

@Composable
private fun CriticalContent(
    state: DrowsinessUiState,
    onAnswerOkay: () -> Unit,
    onVoiceAnswer: () -> Unit,
    onCancelVehicleControl: () -> Unit,
) {
    val d = LocalDesign.current
    val red = Color(0xFFFF4D5A)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(d.dp(6)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoundArcs(d.dp(78), d.dp(92), left = true, color = red, count = 3, glow = true)
            Spacer(Modifier.width(d.dp(22)))
            StageBadge(DrowsyLevel.CRITICAL)
            Spacer(Modifier.width(d.dp(22)))
            SoundArcs(d.dp(78), d.dp(92), left = false, color = red, count = 3, glow = true)
        }
        Spacer(Modifier.height(d.dp(14)))
        // 깨우랑이 말한 졸음쉼터 안내 결과 ("…안내할게요" / "…실패했어요"). 말하기 전에는 찾는 중, "음성으로 답하기" 중이면 묻는 문장
        val line = state.voiceQuestion?.takeIf { state.voiceState in QUESTION_STATES }
            ?: state.voiceGuide
            ?: stringResource(R.string.danger_guide_default)
        SpokenLine(
            text = line,
            sizePx = 46,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.dp(50)),
        )
        Spacer(Modifier.height(d.dp(14)))
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.dp(390)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowPanel(state, onCancelVehicleControl, Modifier.weight(831f).fillMaxHeight())
            Box(
                Modifier
                    .width(d.dp(2))
                    .height(d.dp(240))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80C9CCFF), Color.Transparent)))
            )
            RoutePanel(state, Modifier.weight(647f).fillMaxHeight())
        }
        Spacer(Modifier.height(d.dp(6)))
        CriticalResponseRow(state, onAnswerOkay, onVoiceAnswer)
    }
}

/** 위험 단계에서도 졸리지 않으면 알릴 수 있게: [🎤 음성으로 답하기] [괜찮아요] */
@Composable
private fun CriticalResponseRow(state: DrowsinessUiState, onAnswerOkay: () -> Unit, onVoiceAnswer: () -> Unit) {
    val d = LocalDesign.current
    val voice = state.voiceState
    val voiceLabel = when (voice) {
        VoiceState.ASKING -> stringResource(R.string.caution_asking)
        VoiceState.LISTENING -> stringResource(R.string.caution_listening)
        else -> stringResource(R.string.warning_voice)
    }
    val active = voice in QUESTION_STATES
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(20))) {
        Text(
            stringResource(R.string.danger_okay_hint),
            style = d.text(22, W.SemiBold, KkaeuColors.TextSecondary),
        )
        Row(
            modifier = Modifier
                .height(d.dp(64))
                .clip(RoundedCornerShape(50))
                .background(
                    if (active) Brush.verticalGradient(listOf(KkaeuColors.AccentLight, KkaeuColors.Accent))
                    else Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
                )
                .border(d.dp(1.5f), Color(0x40FFFFFF), RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onVoiceAnswer)
                .padding(start = d.dp(26), end = d.dp(32)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(12)),
        ) {
            KIcon(R.drawable.ic_ms_mic, d.dp(30), Color.White)
            Text(voiceLabel, style = d.text(26, W.Bold, KkaeuColors.TextPrimary))
        }
        OkayPill(onAnswerOkay)
    }
}

/** 왼쪽: 창문 상태 목록 + 차량 그림 */
@Composable
private fun WindowPanel(state: DrowsinessUiState, onCancelVehicleControl: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = d.dp(70), top = d.dp(40)),
            verticalArrangement = Arrangement.spacedBy(d.dp(36)),
        ) {
            WindowRow(open = state.windowOpen, seats = R.string.danger_front_seats)
            WindowRow(open = false, seats = R.string.danger_rear_seats)
            state.vehicleEvent?.blockedReason?.let {
                Text(it, style = d.text(20, W.Medium, KkaeuColors.TextSecondary), modifier = Modifier.width(d.dp(300)))
            }
            // 깨우랑이 창문을 열었거나 찬바람을 켠 동안에만 보인다
            if (state.windowOpen || state.outsideAirOn) UndoVehicleControlPill(onCancelVehicleControl)
        }
        Image(
            painter = painterResource(if (state.windowOpen) R.drawable.img_car_open else R.drawable.img_car_closed),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = d.dp(557 - 831 / 2), y = d.dp(-6))
                .size(d.dp(340), d.dp(330)),
            contentScale = ContentScale.Fit,
        )
    }
}

/** "원래대로 되돌리기": 창문·찬바람을 되돌리고 10분 동안 자동으로 열지 않는다. 운전 중이라 크지 않게, 눈에 덜 띄는 색으로 */
@Composable
private fun UndoVehicleControlPill(onClick: () -> Unit) {
    val d = LocalDesign.current
    Text(
        text = stringResource(R.string.danger_window_undo),
        style = d.text(22, W.SemiBold, KkaeuColors.TextPrimary),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x24FFFFFF))
            .border(d.dp(1f), Color(0x40FFFFFF), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = d.dp(28), vertical = d.dp(12)),
    )
}

@Composable
private fun WindowRow(open: Boolean, @StringRes seats: Int) {
    val d = LocalDesign.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(20))) {
        Box(
            Modifier
                .size(d.dp(76), d.dp(54))
                .then(if (open) Modifier.circleGlow(Color(0x553B82F6), 0.9f) else Modifier)
                .clip(RoundedCornerShape(50))
                .background(
                    if (open) Brush.verticalGradient(listOf(Color(0xFF4F9BFF), Color(0xFF2F62D8)))
                    else Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
                )
                .then(if (open) Modifier.border(d.dp(1.5f), Color(0xB3A8D4FF), RoundedCornerShape(50)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            KIcon(R.drawable.ic_k_window, d.dp(36), if (open) Color(0xFFE6F2FF) else Color(0xFF9AA0C8))
        }
        Column {
            Text(
                stringResource(if (open) R.string.danger_window_open else R.string.danger_window_closed),
                style = d.text(25, W.Bold, KkaeuColors.TextPrimary),
            )
            Text(stringResource(seats), style = d.text(21, W.Medium, KkaeuColors.TextSecondary))
        }
    }
}

/** 오른쪽: 내 위치 → 빛나는 길 → 쉼터 + "경로 변경 완료" + 쉼터 이름 */
@Composable
private fun RoutePanel(state: DrowsinessUiState, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val stop = state.restStop
    Box(modifier) {
        // 경로 그림 (시안 px 기준 상자 520×300, 패널 왼쪽에서 50px)
        Box(
            Modifier
                .offset(x = d.dp(40), y = d.dp(20))
                .size(d.dp(520), d.dp(300)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 520f
                val path = Path().apply {
                    moveTo(110f * k, 232f * k)
                    cubicTo(150f * k, 196f * k, 210f * k, 205f * k, 196f * k, 160f * k)
                    cubicTo(184f * k, 124f * k, 232f * k, 108f * k, 262f * k, 100f * k)
                }
                drawPath(path, Color(0x338B9CFF), style = Stroke(30f * k, cap = StrokeCap.Round))
                drawPath(path, Color(0x668B9CFF), style = Stroke(16f * k, cap = StrokeCap.Round))
                drawPath(path, Brush.linearGradient(listOf(Color(0xFFB9C4FF), Color(0xFF7FD3FF)), Offset(110f * k, 232f * k), Offset(262f * k, 100f * k)), style = Stroke(7f * k, cap = StrokeCap.Round))
                // 핀 아래 그림자
                drawOval(Color(0x55000000), topLeft = Offset(246f * k, 96f * k), size = androidx.compose.ui.geometry.Size(32f * k, 10f * k))
            }
            Image(
                painter = painterResource(R.drawable.img_rest_stop_night),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = d.dp(268), y = d.dp(-18))
                    .size(d.dp(240), d.dp(158)),
                contentScale = ContentScale.Fit,
            )
            // 빨간 위치 핀
            KIcon(
                R.drawable.ic_k_location,
                d.dp(56),
                Color(0xFFFF5A6A),
                Modifier.offset(x = d.dp(234), y = d.dp(46)),
            )
            // 내 위치 (보라 원 + 화살표)
            Box(
                Modifier
                    .offset(x = d.dp(110 - 36), y = d.dp(232 - 36))
                    .size(d.dp(72))
                    .circleGlow(Color(0x778B5CF6))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF9E80FF), Color(0xFF6644E6))))
                    .border(d.dp(3.5f), Color(0xE6FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                KIcon(R.drawable.ic_k_navigation, d.dp(34), Color.White)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = d.dp(260), y = d.dp(160)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.dp(18)),
        ) {
            Text(
                text = stringResource(
                    when {
                        stop?.isWaypointAdded == true -> R.string.danger_route_done
                        state.restGuideOk == false -> R.string.danger_route_failed
                        else -> R.string.danger_route_requested
                    }
                ),
                style = d.text(29, W.Bold, KkaeuColors.TextPrimary, lineHeight = 1.25f),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .height(d.dp(56))
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x1FFFFFFF))
                    .border(d.dp(1.5f), Color(0x26FFFFFF), RoundedCornerShape(50))
                    .padding(start = d.dp(20), end = d.dp(26)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.dp(10)),
            ) {
                KIcon(R.drawable.ic_k_location, d.dp(28), Color(0xFFFF6B9A))
                Text(
                    text = if (stop != null) stringResource(R.string.danger_rest_pill, stop.name, stop.distanceKm)
                    else stringResource(R.string.danger_rest_unknown),
                    style = d.text(24, W.Bold, KkaeuColors.TextPrimary),
                    maxLines = 1,
                )
            }
        }
    }
}
