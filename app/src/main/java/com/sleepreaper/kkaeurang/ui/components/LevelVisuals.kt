package com.sleepreaper.kkaeurang.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.levelColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@StringRes
fun DrowsyLevel.labelRes(): Int = when (this) {
    DrowsyLevel.NORMAL -> R.string.level_normal
    DrowsyLevel.CAUTION -> R.string.level_caution
    DrowsyLevel.WARNING -> R.string.level_warning
    DrowsyLevel.CRITICAL -> R.string.level_critical
}

/**
 * 단계 아이콘. 색이 안 보여도 모양으로 구분되게 단계마다 모양이 다르다.
 * 정상: 원+체크 / 주의: 원+느낌표 / 경고: 삼각형+느낌표 / 위험: 팔각형+느낌표
 */
@Composable
fun LevelIcon(
    level: DrowsyLevel,
    modifier: Modifier = Modifier,
    fill: Color = levelColor(level),
    mark: Color = KkaeuColors.OnLight,
) {
    Canvas(modifier) {
        val w = size.minDimension
        val ox = (size.width - w) / 2f
        val oy = (size.height - w) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * w, oy + y * w)

        when (level) {
            DrowsyLevel.NORMAL -> {
                drawCircle(fill, radius = w / 2f, center = p(0.5f, 0.5f))
                val check = Path().apply {
                    moveTo(p(0.27f, 0.52f).x, p(0.27f, 0.52f).y)
                    lineTo(p(0.44f, 0.68f).x, p(0.44f, 0.68f).y)
                    lineTo(p(0.74f, 0.35f).x, p(0.74f, 0.35f).y)
                }
                drawPath(check, mark, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            DrowsyLevel.CAUTION -> {
                drawCircle(fill, radius = w / 2f, center = p(0.5f, 0.5f))
                exclamation(mark, w, ::p, top = 0.24f, bottom = 0.58f, dot = 0.76f)
            }

            DrowsyLevel.WARNING -> {
                val tri = Path().apply {
                    moveTo(p(0.5f, 0.06f).x, p(0.5f, 0.06f).y)
                    lineTo(p(0.96f, 0.88f).x, p(0.96f, 0.88f).y)
                    lineTo(p(0.04f, 0.88f).x, p(0.04f, 0.88f).y)
                    close()
                }
                drawPath(tri, fill)
                drawPath(tri, fill, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
                exclamation(mark, w, ::p, top = 0.36f, bottom = 0.62f, dot = 0.76f)
            }

            DrowsyLevel.CRITICAL -> {
                val oct = Path().apply {
                    for (i in 0 until 8) {
                        val a = PI / 8 + i * PI / 4
                        val pt = p(0.5f + 0.5f * cos(a).toFloat(), 0.5f + 0.5f * sin(a).toFloat())
                        if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                    close()
                }
                drawPath(oct, fill)
                exclamation(mark, w, ::p, top = 0.24f, bottom = 0.58f, dot = 0.76f)
            }
        }
    }
}

private fun DrawScope.exclamation(
    color: Color,
    w: Float,
    p: (Float, Float) -> Offset,
    top: Float,
    bottom: Float,
    dot: Float,
) {
    drawLine(color, p(0.5f, top), p(0.5f, bottom), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
    drawCircle(color, radius = w * 0.065f, center = p(0.5f, dot))
}
