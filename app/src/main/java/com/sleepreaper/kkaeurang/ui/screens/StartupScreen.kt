package com.sleepreaper.kkaeurang.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.NightSkyBackground
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.ProvideDesignScale
import com.sleepreaper.kkaeurang.ui.theme.W
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** 앱 시작 화면에 보여 줄 상태 (졸음 감시 엔진이 아직 없을 때) */
enum class StartupStatus {
    /** 권한을 묻거나 졸음 감시 서비스를 띄우는 중 */
    PREPARING,

    /** 마이크 권한 거부. 다시 물어볼 수 있다 */
    MIC_DENIED,

    /** 마이크 권한을 "다시 묻지 않음"으로 거부. 설정에서 켜야 한다 */
    MIC_BLOCKED,

    /** 돌던 졸음 감시가 꺼졌다 (알림의 종료 버튼 등) */
    STOPPED,
}

/** 시작 장면: 마스코트가 커지는 배율, 위로 올라가는 거리, 걸리는 시간 */
private const val INTRO_SCALE = 1.3f
private val INTRO_RISE = 120.dp
private const val INTRO_MS = 700

/** 마스코트가 위아래로 떠다니는 폭과 한 번 오르내리는 시간 */
private val FLOAT_AMPLITUDE = 8.dp
private const val FLOAT_MS = 1600

/**
 * 앱 시작 화면. 시스템 시작 화면(스플래시: 남색 + 가운데 마스코트, themes.xml의 Theme.KkaeUrang.Starting)을 이어 받는다.
 *
 * 첫 장면은 스플래시와 똑같이 남색 단색 + 같은 크기·같은 자리의 마스코트라서 화면이 바뀌는 순간이 보이지 않는다.
 * 그다음 밤하늘이 서서히 나타나고, 마스코트가 조금 커지며 위로 올라가고, 그 아래에 로고 글자와 상태 문구가 떠오른다.
 * 마스코트는 그 뒤로도 위아래로 살짝 떠다닌다.
 *
 * 상태별 아래 문구: 준비 중(점 세 개) / 마이크 권한 거부(다시 요청) / 다시 묻지 않음으로 거부(설정 열기) / 감시 꺼짐(다시 시작)
 *
 * @param playIntro false면 시작 장면 없이 마지막 모습으로 바로 그린다 (돌던 감시가 꺼져서 이 화면으로 돌아온 경우)
 */
@Composable
fun StartupScreen(
    status: StartupStatus,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    playIntro: Boolean = true,
) {
    // 미리보기에서는 애니메이션이 돌지 않으므로 마지막 모습으로 그린다
    val skipIntro = !playIntro || LocalInspectionMode.current
    val intro = remember { Animatable(if (skipIntro) 1f else 0f) }
    val sky = remember { Animatable(if (skipIntro) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { sky.animateTo(1f, tween(INTRO_MS, easing = LinearOutSlowInEasing)) }
        intro.animateTo(1f, tween(INTRO_MS, delayMillis = 150, easing = FastOutSlowInEasing))
    }
    val floating by rememberInfiniteTransition(label = "mascotFloat").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLOAT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mascotFloatOffset",
    )
    // 스플래시 아이콘과 같은 크기 (dimens.xml)
    val mascotWidth = dimensionResource(R.dimen.splash_mascot_width)
    val mascotHeight = dimensionResource(R.dimen.splash_mascot_height)

    // 스플래시 배경색(colors.xml kkaeu_background)과 같은 남색에서 시작
    Box(modifier.fillMaxSize().background(KkaeuColors.SkyTop)) {
        NightSkyBackground(alert = false, modifier = Modifier.graphicsLayer { alpha = sky.value })

        ProvideDesignScale {
            val d = LocalDesign.current
            Image(
                painter = painterResource(R.drawable.img_logo_mascot),
                contentDescription = null, // 아래 "깨우랑" 글자가 이름을 읽어 준다
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(mascotWidth, mascotHeight)
                    .graphicsLayer {
                        val p = intro.value
                        val scale = 1f + (INTRO_SCALE - 1f) * p
                        scaleX = scale
                        scaleY = scale
                        translationY = -(INTRO_RISE.toPx() * p + FLOAT_AMPLITUDE.toPx() * floating)
                    },
            )

            // 올라간 마스코트 바로 아래에서 로고 글자·상태 문구가 시작한다
            val lockupTop = maxHeight / 2 - INTRO_RISE + mascotHeight * INTRO_SCALE / 2 + d.dp(14)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = lockupTop, start = d.dp(40), end = d.dp(40))
                    .graphicsLayer {
                        // 마스코트가 반쯤 올라간 뒤부터 아래에서 떠오른다
                        val t = ((intro.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 24.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.logo_text),
                    style = d.text(76, W.ExtraBold, KkaeuColors.TextPrimary, lineHeight = 1.1f, italic = true),
                )
                Spacer(Modifier.height(d.dp(4)))
                Tagline()
                Spacer(Modifier.height(d.dp(64)))
                StatusBlock(status = status, onRetry = onRetry, onOpenSettings = onOpenSettings)
            }
        }
    }
}

/** "졸음보다 한발 먼저, 깨우랑" (깨우랑만 노랑) */
@Composable
private fun Tagline() {
    val d = LocalDesign.current
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.header_tagline))
            append(' ')
            withStyle(SpanStyle(color = KkaeuColors.Yellow, fontStyle = FontStyle.Italic)) {
                append(stringResource(R.string.logo_text))
            }
        },
        style = d.text(26, W.Bold, KkaeuColors.TextSecondary),
    )
}

/** 상태 문구. 바뀌면 화면 읽기 프로그램이 읽어 준다 */
@Composable
private fun StatusBlock(status: StartupStatus, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val d = LocalDesign.current
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.dp(14)),
    ) {
        when (status) {
            StartupStatus.PREPARING -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.dp(16)),
            ) {
                Text(
                    text = stringResource(R.string.startup_preparing),
                    style = d.text(26, W.SemiBold, KkaeuColors.TextSecondary),
                )
                LoadingDots()
            }
            StartupStatus.MIC_DENIED -> Problem(
                title = R.string.startup_mic_title,
                body = R.string.startup_mic_body,
                action = R.string.startup_mic_retry,
                onAction = onRetry,
            )
            StartupStatus.MIC_BLOCKED -> Problem(
                title = R.string.startup_mic_title,
                body = R.string.startup_mic_blocked_body,
                action = R.string.startup_open_settings,
                onAction = onOpenSettings,
            )
            StartupStatus.STOPPED -> Problem(
                title = R.string.startup_stopped_title,
                body = R.string.startup_stopped_body,
                action = R.string.startup_restart,
                onAction = onRetry,
            )
        }
    }
}

/** 제목 + 설명 + 버튼 하나 */
@Composable
private fun Problem(@StringRes title: Int, @StringRes body: Int, @StringRes action: Int, onAction: () -> Unit) {
    val d = LocalDesign.current
    Text(
        text = stringResource(title),
        style = d.text(36, W.Bold, KkaeuColors.TextPrimary),
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(body),
        style = d.text(24, W.Medium, KkaeuColors.TextSecondary),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(d.dp(10)))
    PillButton(label = action, onClick = onAction)
}

/** 보라 알약 버튼 (운전석에서 누르기 쉽게 높이 약 86dp) */
@Composable
private fun PillButton(@StringRes label: Int, onClick: () -> Unit) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(d.dp(76))
            .clip(shape)
            .background(AccentBrush)
            .border(d.dp(2), Color(0x66FFFFFF), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = d.dp(52)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(label), style = d.text(28, W.Bold, KkaeuColors.TextPrimary))
    }
}

/** 차례로 밝아지는 점 세 개 */
@Composable
private fun LoadingDots() {
    val d = LocalDesign.current
    val phase by rememberInfiniteTransition(label = "loadingDots").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "loadingDotsPhase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(d.dp(10))) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(d.dp(12))
                    .graphicsLayer {
                        // 자기 차례(1/3 구간)에만 밝아졌다가 다시 흐려진다
                        val t = ((phase - i) % 3f + 3f) % 3f
                        alpha = if (t < 1f) 0.3f + 0.7f * sin(PI.toFloat() * t) else 0.3f
                    }
                    .clip(CircleShape)
                    .background(KkaeuColors.AccentLight)
            )
        }
    }
}
