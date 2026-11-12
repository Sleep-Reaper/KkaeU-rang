package com.sleepreaper.kkaeurang.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.theme.CardBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import kotlin.math.cos
import kotlin.math.sin

// 시안의 작은 장식들: 그라데이션 아이콘, 연보라 카드, 노란 반짝 선, 소리 물결, 빛 번짐

/** 아이콘(흰색 벡터)에 그라데이션을 입힌다 */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

/** 벡터 아이콘 (단색) */
@Composable
fun KIcon(
    @DrawableRes id: Int,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}

/** 벡터 아이콘 (그라데이션) */
@Composable
fun KGradientIcon(
    @DrawableRes id: Int,
    size: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painterResource(id),
        contentDescription,
        modifier.size(size).gradientTint(brush),
        tint = Color.White,
    )
}

/** 연보라 카드 (시안 홈 화면 카드) */
fun Modifier.lavenderCard(radius: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(CardBrush)
        .border(1.5.dp, KkaeuColors.CardBorder, shape)
}

/** 카드 오른쪽 흰 동그라미 + ">" */
@Composable
fun ChevronCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xF2FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(R.drawable.ic_k_chevron_right, size * 0.56f, KkaeuColors.CardText)
    }
}

/** 원 뒤로 번지는 빛 (보라 글로우) */
fun Modifier.circleGlow(color: Color, radiusFactor: Float = 1.45f): Modifier = drawBehind {
    val r = size.minDimension / 2f * radiusFactor
    drawCircle(
        Brush.radialGradient(
            0f to color,
            0.55f to color.copy(alpha = color.alpha * 0.45f),
            1f to Color.Transparent,
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}

/**
 * 노란 "반짝" 선 3개 (시안의 마스코트 머리 위·경보 제목 옆).
 * [angleDeg]: 가운데 선이 향하는 방향 (0 = 오른쪽, -90 = 위)
 */
@Composable
fun SparkLines(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = KkaeuColors.Yellow,
    angleDeg: Float = -45f,
) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width * 0.15f, this.size.height * 0.85f)
        val len = this.size.minDimension * 0.42f
        val start = this.size.minDimension * 0.38f
        listOf(-38f, 0f, 38f).forEach { d ->
            val a = Math.toRadians((angleDeg + d).toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val from = c + dir * start
            drawLine(color, from, from + dir * len, strokeWidth = this.size.minDimension * 0.11f, cap = StrokeCap.Round)
        }
    }
}

/**
 * "(( ))" 소리 물결. [left]가 true면 "((" 모양 (가운데 물체의 왼쪽에 둔다).
 * 안쪽 호가 가장 작고 진하며, 바깥으로 갈수록 크고 흐려진다.
 */
@Composable
fun SoundArcs(
    width: Dp,
    height: Dp,
    left: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    count: Int = 2,
    glow: Boolean = false,
) {
    Canvas(modifier.size(width, height)) {
        val stroke = size.width * 0.075f * (3f / (count + 1))
        val cy = size.height / 2f
        val maxDepth = size.height * 0.26f
        val usable = size.width - stroke * 2f - maxDepth
        val spacing = if (count > 1) usable / (count - 1) else 0f
        for (i in 0 until count) {
            val h = size.height * (0.6f + 0.4f * (i + 1) / count)
            val depth = h * 0.26f
            // 안쪽(i = 0)의 꼭짓점이 물체 쪽 끝에 오도록
            val apexFromInner = stroke + depth + i * spacing
            val apexX = if (left) size.width - apexFromInner else apexFromInner
            val endX = if (left) apexX + depth else apexX - depth
            val path = Path().apply {
                moveTo(endX, cy - h / 2f)
                quadraticTo(2f * apexX - endX, cy, endX, cy + h / 2f)
            }
            val a = color.alpha * (1f - 0.45f * i / count.coerceAtLeast(1))
            if (glow) drawPath(path, color.copy(alpha = a * 0.28f), style = Stroke(stroke * 3f, cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = a), style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** 노란 소리 막대 (주의 단계 질문 왼쪽) */
@Composable
fun SoundBars(size: Dp, modifier: Modifier = Modifier, color: Color = KkaeuColors.Yellow) {
    Canvas(modifier.size(size)) {
        val w = this.size.width * 0.16f
        val heights = listOf(0.5f, 0.92f, 0.5f)
        heights.forEachIndexed { i, hf ->
            val x = this.size.width * (0.18f + i * 0.32f)
            val h = this.size.height * hf
            drawLine(
                color,
                Offset(x, (this.size.height - h) / 2f + w / 2f),
                Offset(x, (this.size.height + h) / 2f - w / 2f),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 4갈래 반짝이 별 */
fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val k = radius * 0.22f
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + k * 0.3f, center.y - k * 0.3f, center.x + radius, center.y)
        quadraticTo(center.x + k * 0.3f, center.y + k * 0.3f, center.x, center.y + radius)
        quadraticTo(center.x - k * 0.3f, center.y + k * 0.3f, center.x - radius, center.y)
        quadraticTo(center.x - k * 0.3f, center.y - k * 0.3f, center.x, center.y - radius)
        close()
    }
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), center, radius * 1.6f), radius * 1.6f, center)
    drawPath(p, color)
}

/** 시안 크기 단위 → dp 변환을 짧게 쓰기 위한 도우미 */
@Composable
fun px(value: Int): Dp = LocalDesign.current.dp(value)

@Composable
fun px(value: Float): Dp = LocalDesign.current.dp(value)
