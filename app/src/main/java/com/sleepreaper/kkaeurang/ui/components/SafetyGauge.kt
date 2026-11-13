package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import com.sleepreaper.kkaeurang.ui.theme.levelBadgeBrush
import com.sleepreaper.kkaeurang.ui.theme.levelBadgeText
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 계기판 크기 (시안 px). 원의 중심은 상자 안 (CX, CY)
const val GAUGE_W = 560
const val GAUGE_H = 340
private const val CX = 280f
private const val CY = 262f
private const val BAND_OUTER = 220f   // 색 띠 바깥 반지름
private const val BAND_WIDTH = 40f
private const val RING_R = 233f       // 바깥 가는 테두리
private const val LABEL_R = 250f      // 40·70·80 자리 숫자
private const val TICK_IN = 158f
private const val TICK_OUT = 169f
// 바늘은 가운데 "92" 글자 바깥(안전 구역 315°~360°에서 글자와 닿지 않는 거리)에서 시작한다
private const val NEEDLE_IN = 116f
private const val NEEDLE_OUT = 180f
private const val NEEDLE_MIN_LEN = 22f   // 글자를 피하느라 바늘이 짧아져도 이만큼은 남긴다
private const val BASE_CLEAR = 18f       // 바늘 밑동 동그라미(반지름 10 + 테두리)가 글자에서 떨어질 거리

// 가운데 글자 자리 (시안 px, 원의 중심 기준 y. 위쪽이 음수). 글자 줄 높이에 흔들리지 않도록 기준선(baseline)으로 정한다.
// 점수 바로 아래 "나의 안전 점수"(간격 약 11), 그 아래로 넉넉히 띄워 단계 배지(간격 약 38)
private const val NUMBER_BASELINE = -46f
private const val CAPTION_BASELINE = -16f
private const val CAPTION_SIZE = 22
private const val BADGE_TOP = 26f
// 실제 획 높이 비율 (숫자: 글자 크기의 약 0.74, 한글: 기준선 위 약 0.85 · 아래 약 0.15)
private const val DIGIT_HEIGHT = 0.74f
private const val HANGUL_ASCENT = 0.85f
private const val HANGUL_DESCENT = 0.15f
private const val TEXT_MARGIN = 3f

/**
 * 바늘 시작 거리 (시안 px). 숫자·설명 글자 상자를 밑동 동그라미 크기만큼 넓힌 뒤, 원의 중심에서 [dir] 방향으로
 * 나가는 선이 그 상자를 지나면 상자를 벗어난 곳에서 시작한다 → 동그라미가 글자에 닿지 않는다.
 * 안전 단계(오른쪽 위)에서는 늘 [NEEDLE_IN].
 */
private fun needleStart(dir: Offset, numberHalfW: Float, numberSize: Int, captionHalfW: Float): Float {
    val c = BASE_CLEAR + TEXT_MARGIN
    val numberTop = NUMBER_BASELINE - numberSize * DIGIT_HEIGHT
    val captionTop = CAPTION_BASELINE - CAPTION_SIZE * HANGUL_ASCENT
    val captionBottom = CAPTION_BASELINE + CAPTION_SIZE * HANGUL_DESCENT
    val exits = listOfNotNull(
        rayExit(dir, numberHalfW + c, numberTop - c, NUMBER_BASELINE + c),
        rayExit(dir, captionHalfW + c, captionTop - c, captionBottom + c),
    )
    val start = (exits.maxOrNull() ?: 0f).coerceAtLeast(NEEDLE_IN)
    return start.coerceAtMost(NEEDLE_OUT - NEEDLE_MIN_LEN)
}

/** 중심에서 나가는 반직선이 가로 [-halfW, halfW] × 세로 [top, bottom] 상자를 빠져나가는 거리. 안 지나가면 null */
private fun rayExit(dir: Offset, halfW: Float, top: Float, bottom: Float): Float? {
    fun slab(o: Float, lo: Float, hi: Float): Pair<Float, Float>? =
        if (o == 0f) {
            if (lo <= 0f && 0f <= hi) Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY else null
        } else {
            val a = lo / o
            val b = hi / o
            minOf(a, b) to maxOf(a, b)
        }
    val (x0, x1) = slab(dir.x, -halfW, halfW) ?: return null
    val (y0, y1) = slab(dir.y, top, bottom) ?: return null
    val enter = maxOf(x0, y0)
    val exit = minOf(x1, y1)
    return exit.takeIf { enter <= exit && exit > 0f }
}

/** 계기판 호: 왼쪽(180°)에서 위를 지나 오른쪽(360°)까지. 네 단계가 45°씩 나눠 갖는다 */
private const val START = 180f
private const val ZONE = 45f

/** 단계 → 계기판 구역 번호 (왼쪽부터 위험·경고·주의·안전) */
private fun DrowsyLevel.zone(): Int = 3 - ordinal

/** 단계 번호 표시 (Lv.4 = 안전 … Lv.1 = 위험) */
fun DrowsyLevel.lvNumber(): Int = 4 - ordinal

/**
 * 바늘 각도. 구역은 같은 크기로 그리지만 점수 구간은 제각각이라 구역 안에서만 선형으로 나눈다.
 * 바늘은 항상 현재 단계 구역 안을 가리킨다 (무응답 강제 경고처럼 점수와 단계가 어긋나도).
 */
fun safetyNeedleAngle(safety: Int, level: DrowsyLevel): Float {
    val i = level.zone()
    val from = DrowsyThresholds.SAFETY_STOPS[i]
    val to = DrowsyThresholds.SAFETY_STOPS[i + 1]
    val f = ((safety - from) / (to - from)).coerceIn(0.06f, 0.94f)
    return START + ZONE * i + ZONE * f
}

/**
 * 안전 점수 계기판 (시안: 빨강→주황→올리브→초록 띠 + 가는 바깥 테두리 + 눈금 + 흰 바늘,
 * 가운데 굵은 점수 "92"와 "나의 안전 점수", 그 아래 "✓ 안전 (Lv.4)" 배지). 애니메이션 없이 즉시 그린다.
 *
 * @param onLongPress 길게 누르면 호출 (디버그 패널). null이면 아무 일 없음
 */
@Composable
fun SafetyGauge(
    safety: Int,
    level: DrowsyLevel,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val measurer = rememberTextMeasurer()
    val levelName = stringResource(level.labelRes())
    val description = stringResource(R.string.gauge_description, safety, levelName)
    val labels = DrowsyThresholds.SAFETY_STOPS.map { it.roundToInt().toString() }
    // 세 자리(100)는 눈금과 닿지 않게 조금 작게
    // 세 자리(100)는 눈금과 닿지 않게 조금 작게. 기준선은 같아서 아래 글자 자리는 바뀌지 않는다
    val numberSize = if (safety >= 100) 96 else 108
    val numberStyle = d.text(numberSize, FontWeight.Black, KkaeuColors.TextPrimary, lineHeight = 1.0f)
        .copy(letterSpacing = (-2).sp)
    val caption = stringResource(R.string.gauge_caption)
    val captionStyle = d.text(CAPTION_SIZE, W.SemiBold, KkaeuColors.TextSecondary, lineHeight = 1.0f)

    Box(
        modifier = modifier
            .size(d.dp(GAUGE_W), d.dp(GAUGE_H))
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnLongPress?.invoke() }) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / GAUGE_W   // 시안 px → 화면 px
            val c = Offset(CX * k, CY * k)
            fun polar(r: Float, deg: Float): Offset {
                val a = Math.toRadians(deg.toDouble())
                return c + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r * k)
            }

            // 안쪽 어두운 원판
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xE6151B4A),
                    0.75f to Color(0x99182058),
                    1f to Color(0x001A2259),
                    center = c,
                    radius = (BAND_OUTER - BAND_WIDTH) * k,
                ),
                radius = (BAND_OUTER - BAND_WIDTH) * k,
                center = c,
            )

            // 색 띠: 위험(왼쪽) → 안전(오른쪽)
            val bandR = (BAND_OUTER - BAND_WIDTH / 2f) * k
            val bandBrush = Brush.sweepGradient(
                0.0f to Color(0xFF3F8098),  // 360° (오른쪽 끝 = 100)
                0.5f to Color(0xFFA34B8E),  // 180° (왼쪽 끝 = 0)
                0.625f to Color(0xFFBA7A80),
                0.75f to Color(0xFFADA78E), // 270° (맨 위)
                0.875f to Color(0xFF609B93),
                1.0f to Color(0xFF3F8098),
                center = c,
            )
            drawArc(
                brush = bandBrush,
                startAngle = START,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - bandR, c.y - bandR),
                size = Size(bandR * 2, bandR * 2),
                style = Stroke(BAND_WIDTH * k, cap = StrokeCap.Butt),
                alpha = 0.88f,
            )
            // 띠 안쪽 그늘 (입체감)
            val innerR = (BAND_OUTER - BAND_WIDTH * 0.82f) * k
            drawArc(
                color = Color(0x33101540),
                startAngle = START,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - innerR, c.y - innerR),
                size = Size(innerR * 2, innerR * 2),
                style = Stroke(BAND_WIDTH * 0.36f * k),
            )
            // 구역 경계 (띠를 가로지르는 옅은 선)
            for (i in 1..3) {
                val a = START + ZONE * i
                drawLine(Color(0x59FFFFFF), polar(BAND_OUTER - BAND_WIDTH, a), polar(BAND_OUTER, a), strokeWidth = 2f * k)
            }

            // 바깥 테두리 (숫자 자리는 비운다)
            val ringBrush = Brush.sweepGradient(
                0.0f to Color(0xFFC9C4FF), 0.5f to Color(0xFF8E6CF0), 0.75f to Color(0xFFA99BFF), 1.0f to Color(0xFFC9C4FF),
                center = c,
            )
            val ringR = RING_R * k
            val gaps = listOf(START + ZONE, START + ZONE * 2, START + ZONE * 3)
            var from = START
            (gaps + (START + 180f)).forEach { g ->
                val to = if (g == START + 180f) g else g - 9f
                drawArc(
                    brush = ringBrush,
                    startAngle = from,
                    sweepAngle = to - from,
                    useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR),
                    size = Size(ringR * 2, ringR * 2),
                    style = Stroke(3.2f * k, cap = StrokeCap.Round),
                    alpha = 0.85f,
                )
                from = g + 9f
            }

            // 안쪽 눈금
            val ticks = 24
            for (t in 0..ticks) {
                val a = START + 180f * t / ticks
                val major = t % 6 == 0
                drawLine(
                    Color(if (major) 0x99B9BEFF else 0x668A93E8),
                    polar(TICK_IN - if (major) 3f else 0f, a),
                    polar(TICK_OUT, a),
                    strokeWidth = (if (major) 3f else 2.4f) * k,
                    cap = StrokeCap.Round,
                )
            }

            // 숫자: 40·70·80 자리(위쪽 세 개)는 테두리 틈에, 0·100은 띠 끝 아래에
            val labelStyle = TextStyle(fontSize = (27f * k).toSp(), fontWeight = FontWeight.SemiBold, color = KkaeuColors.TextPrimary)
            labels.forEachIndexed { i, text ->
                val layout = measurer.measure(text, labelStyle)
                val pos = when (i) {
                    0 -> Offset(c.x - (BAND_OUTER - BAND_WIDTH / 2f + 2f) * k, c.y + 32f * k)
                    4 -> Offset(c.x + (BAND_OUTER - BAND_WIDTH / 2f + 6f) * k, c.y + 32f * k)
                    else -> polar(LABEL_R, START + ZONE * i)
                }
                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
            }

            // 가운데 점수 "92"와 바로 아래 "나의 안전 점수" (기준선에 맞춰 그린다)
            val numberLayout = measurer.measure(safety.toString(), numberStyle)
            val captionLayout = measurer.measure(caption, captionStyle)
            drawText(
                numberLayout,
                topLeft = Offset(c.x - numberLayout.size.width / 2f, c.y + NUMBER_BASELINE * k - numberLayout.firstBaseline),
            )
            drawText(
                captionLayout,
                topLeft = Offset(c.x - captionLayout.size.width / 2f, c.y + CAPTION_BASELINE * k - captionLayout.firstBaseline),
            )

            // 바늘: 가운데 숫자·"나의 안전 점수"를 피해 떨어진 곳(빈 동그라미)에서 띠 안쪽까지.
            // 경보 화면을 닫으면 주의 이상에서도 홈 계기판이 보이므로, 바늘이 글자를 지나면 글자 바깥에서 시작한다
            val ang = safetyNeedleAngle(safety, level)
            val rad = Math.toRadians(ang.toDouble())
            val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
            val side = Offset(-dir.y, dir.x)
            val needleIn = needleStart(
                dir = dir,
                numberHalfW = numberLayout.size.width / 2f / k,
                numberSize = numberSize,
                captionHalfW = captionLayout.size.width / 2f / k,
            )
            val base = c + dir * (needleIn * k)
            val tip = c + dir * (NEEDLE_OUT * k)
            val needle = Path().apply {
                (base + side * (4.2f * k)).let { moveTo(it.x, it.y) }
                (tip + side * (1.6f * k)).let { lineTo(it.x, it.y) }
                (tip + dir * (2.2f * k)).let { p -> (tip - side * (1.6f * k)).let { e -> quadraticTo(p.x, p.y, e.x, e.y) } }
                (base - side * (4.2f * k)).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(needle, Color(0x55B9A8FF), style = Stroke(6f * k))
            drawPath(needle, Brush.linearGradient(listOf(Color(0xFFDCD6FF), Color.White), base, tip))
            drawCircle(Color(0xFF1A2157), radius = 10f * k, center = base)
            drawCircle(Color.White, radius = 10f * k, center = base, style = Stroke(3.6f * k))
        }

        // 단계 배지 "✓ 안전 (Lv.4)": "나의 안전 점수"에서 넉넉히 띄운다
        LevelBadge(
            level = level,
            text = stringResource(R.string.gauge_level_badge, levelName, level.lvNumber()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = d.dp(CY + BADGE_TOP)),
        )
    }
}

/** 계기판 아래 단계 배지 */
@Composable
private fun LevelBadge(level: DrowsyLevel, text: String, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(d.dp(48))
            .clip(shape)
            .background(levelBadgeBrush(level))
            .border(d.dp(1.5f), Color(0x26FFFFFF), shape)
            .padding(start = d.dp(18), end = d.dp(26)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(12)),
    ) {
        LevelIcon(
            level = level,
            modifier = Modifier.size(d.dp(32)),
            mark = if (level == DrowsyLevel.NORMAL) Color.White else KkaeuColors.OnLight,
        )
        Text(text, style = d.text(25, W.SemiBold, levelBadgeText(level)))
    }
}
