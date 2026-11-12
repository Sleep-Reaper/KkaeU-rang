package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.levelColor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 계기판 호: 왼쪽 아래(150°)에서 시계 방향으로 240°. 네 단계가 같은 크기(60°)로 나눠 갖는다
private const val DIAL_START = 150f
private const val DIAL_SWEEP = 240f
private const val ZONE_SWEEP = DIAL_SWEEP / 4f
private const val ZONE_GAP = 2.4f

// 아래 비율은 모두 바깥 반지름(Ro) 기준. 디자인 시안(design/faux-ui)과 같은 비율이다
private const val ACTIVE_STROKE = 0.12f   // 지금 단계 구역: 굵은 원색 띠
private const val IDLE_STROKE = 0.04f     // 나머지 구역: 같은 색의 가는 선
private const val IDLE_MIX = 0.7f         // 나머지 구역 색 = 배경에서 단계색 쪽으로 70%
private const val NEEDLE_IN = 0.526f      // 바늘은 점수 숫자를 피해 여기서 시작
private const val NEEDLE_OUT = 0.988f
private const val NEEDLE_BASE = 0.024f    // 바늘 밑동 반폭
private const val NEEDLE_TIP = 0.009f     // 바늘 끝 반폭
private const val NEEDLE_EDGE = 0.012f    // 바늘을 호와 떼어 보이게 하는 어두운 테두리
private const val SCORE_SIZE = 0.67f      // 점수 글자 크기
private const val DIGIT_CAP_HEIGHT = 0.711f // Roboto 숫자 높이 (글자 크기 대비)

/** 단계별 점수 구간 (DrowsyLevel 순서와 같음) */
private val ZONE_RANGES = listOf(
    0f to DrowsyThresholds.CAUTION_MIN,
    DrowsyThresholds.CAUTION_MIN to DrowsyThresholds.WARNING_MIN,
    DrowsyThresholds.WARNING_MIN to DrowsyThresholds.CRITICAL_MIN,
    DrowsyThresholds.CRITICAL_MIN to 100f,
)

/**
 * 바늘 각도. 구역은 같은 크기로 그리지만 점수 구간은 제각각이라 구역 안에서만 선형으로 나눈다.
 * 바늘은 항상 현재 단계(level) 구역 안을 가리킨다 (무응답 강제 경고처럼 점수와 단계가 어긋나도).
 */
fun needleAngle(score: Float, level: DrowsyLevel): Float {
    val i = level.ordinal
    val (from, to) = ZONE_RANGES[i]
    val fraction = ((score - from) / (to - from)).coerceIn(0.08f, 0.92f)
    return DIAL_START + ZONE_SWEEP * i + ZONE_SWEEP * fraction
}

/**
 * 계기판. 정사각형 Canvas(지름 = 2·바깥 반지름)에 그리고, 원의 중심이 Canvas 가운데다.
 * 호 아래쪽 120°는 비어 있어서, 그 밑에 로그·포인트를 둔다 (CenterGauge).
 * - 4색 구역: 지금 단계 구역만 굵은 원색 띠, 나머지는 같은 색의 가는 선
 * - 바늘: 가운데 점수를 가리지 않도록 중심에서 떨어진 곳부터 호까지
 * - 가운데: 현재 점수 (단계색, 아주 크게)
 * 애니메이션 없이 즉시 그린다 (주행 중 애니메이션 금지).
 */
@Composable
fun DrowsinessDial(score: Float, level: DrowsyLevel, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val scoreText = score.roundToInt().toString()
    val description = stringResource(R.string.main_dial_description, score.roundToInt(), stringResource(level.labelRes()))

    Canvas(modifier.semantics { contentDescription = description }) {
        val ro = size.minDimension / 2f
        val c = center
        val activeWidth = ro * ACTIVE_STROKE
        val arcR = ro - activeWidth / 2f

        // 1) 4색 구역
        DrowsyLevel.entries.forEachIndexed { i, zone ->
            val on = zone == level
            val from = DIAL_START + ZONE_SWEEP * i + if (i > 0) ZONE_GAP / 2f else 0f
            val to = DIAL_START + ZONE_SWEEP * (i + 1) - if (i < DrowsyLevel.entries.lastIndex) ZONE_GAP / 2f else 0f
            drawArc(
                color = if (on) levelColor(zone) else lerp(KkaeuColors.Background, levelColor(zone), IDLE_MIX),
                startAngle = from,
                sweepAngle = to - from,
                useCenter = false,
                topLeft = Offset(c.x - arcR, c.y - arcR),
                size = Size(arcR * 2f, arcR * 2f),
                style = Stroke(width = if (on) activeWidth else ro * IDLE_STROKE, cap = StrokeCap.Butt),
            )
        }

        // 2) 바늘: 밑동이 넓고 끝이 좁은 막대, 양끝은 둥글게
        val rad = Math.toRadians(needleAngle(score, level).toDouble())
        val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        val side = Offset(-dir.y, dir.x)
        fun at(radius: Float, offset: Float) = c + dir * radius + side * offset
        val rIn = ro * NEEDLE_IN
        val rOut = ro * NEEDLE_OUT
        val base = ro * NEEDLE_BASE
        val tip = ro * NEEDLE_TIP
        val needle = Path().apply {
            at(rIn, -base).let { moveTo(it.x, it.y) }
            at(rOut, -tip).let { lineTo(it.x, it.y) }
            val tipCtrl = at(rOut + tip * 1.4f, 0f)
            at(rOut, tip).let { quadraticTo(tipCtrl.x, tipCtrl.y, it.x, it.y) }
            at(rIn, base).let { lineTo(it.x, it.y) }
            val baseCtrl = at(rIn - base * 0.7f, 0f)
            at(rIn, -base).let { quadraticTo(baseCtrl.x, baseCtrl.y, it.x, it.y) }
            close()
        }
        drawPath(needle, KkaeuColors.Background, style = Stroke(width = ro * NEEDLE_EDGE * 2f, join = StrokeJoin.Round))
        drawPath(needle, KkaeuColors.TextPrimary)

        // 3) 가운데 점수: 숫자 높이의 가운데를 원의 중심에 맞춘다
        val fontPx = ro * SCORE_SIZE
        val layout = textMeasurer.measure(
            scoreText,
            TextStyle(fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold),
        )
        drawText(
            textLayoutResult = layout,
            color = levelColor(level),
            topLeft = Offset(
                c.x - layout.size.width / 2f,
                c.y - (layout.firstBaseline - fontPx * DIGIT_CAP_HEIGHT / 2f),
            ),
        )
    }
}
