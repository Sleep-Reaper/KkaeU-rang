package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors

/**
 * 밤하늘 배경 (시안 그대로 코드로 그림): 남색 그라데이션 하늘 · 지평선 빛 · 구름 · 별 · 도로 · 도시 실루엣.
 * 좌표는 화면 크기에 대한 비율이라 어떤 크기에서도 같은 구도로 보인다.
 *
 * @param alert 경보 화면용. 전체를 조금 어둡게 하고 오른쪽에 언덕·나무를 더한다.
 */
@Composable
fun NightSkyBackground(alert: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1) 하늘: 위는 짙은 남색, 가운데(지평선)는 밝은 보랏빛 파랑, 아래는 다시 짙게
        drawRect(
            Brush.verticalGradient(
                0f to KkaeuColors.SkyTop,
                0.30f to Color(0xFF212A63),
                0.44f to Color(0xFF2E3B90),
                0.50f to KkaeuColors.Horizon,
                0.57f to Color(0xFF2C3782),
                0.78f to KkaeuColors.SkyLow,
                1f to KkaeuColors.SkyBottom,
            )
        )
        // 지평선 가운데가 더 밝게 번지도록
        glow(Offset(w * 0.46f, h * 0.50f), w * 0.42f, Color(0x664F60D0))

        if (!alert) {
            // 2) 먼 도시 실루엣 (계기판 오른쪽 아래)
            city(left = w * 0.55f, baseY = h * 0.488f, unit = h * 0.0075f, alpha = 0.75f)
            // 3) 도로: 왼쪽 아래에서 지평선 쪽으로 휘어 들어가는 길 (경보 화면에서는 흐린 마스코트와 겹치므로 생략)
            road(w, h)
        }

        // 4) 구름
        cloudCluster(
            listOf(
                Triple(-0.02f, 0.27f, 0.085f), Triple(0.05f, 0.235f, 0.06f), Triple(0.12f, 0.29f, 0.055f),
                Triple(0.17f, 0.36f, 0.05f), Triple(0.02f, 0.40f, 0.09f), Triple(0.10f, 0.43f, 0.07f),
            ),
            w, h, KkaeuColors.Cloud,
        )
        cloudCluster(
            listOf(Triple(0.86f, 0.50f, 0.05f), Triple(0.93f, 0.47f, 0.06f), Triple(1.0f, 0.49f, 0.07f)),
            w, h, Color(0xFF3B47A0),
        )
        // 아래 양쪽 모서리 (하단 탭 옆)
        cloudCluster(
            listOf(Triple(-0.01f, 0.86f, 0.045f), Triple(0.025f, 0.83f, 0.035f), Triple(0.99f, 0.84f, 0.04f), Triple(1.02f, 0.80f, 0.04f)),
            w, h, Color(0xFF3A4598),
        )

        // 5) 별
        val stars = listOf(
            Triple(0.080f, 0.165f, 0.0085f), Triple(0.133f, 0.218f, 0.012f), Triple(0.040f, 0.262f, 0.0075f),
            Triple(0.162f, 0.258f, 0.003f), Triple(0.270f, 0.120f, 0.004f), Triple(0.880f, 0.150f, 0.004f),
            Triple(0.955f, 0.545f, 0.006f), Triple(0.735f, 0.205f, 0.003f),
        )
        stars.forEach { (x, y, r) -> sparkle(Offset(w * x, h * y), h * r * 1.6f, KkaeuColors.Star) }

        if (alert) {
            // 경보 화면: 오른쪽 언덕·나무 + 전체를 어둡게
            hills(w, h)
            drawRect(Brush.verticalGradient(listOf(Color(0x40080B24), Color(0x26080B24), Color(0x40080B24))))
        }
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

/** 동그라미 여러 개를 겹쳐 뭉게구름을 만든다. (x, y, 반지름) 모두 화면 비율 (반지름은 가로 기준) */
private fun DrawScope.cloudCluster(parts: List<Triple<Float, Float, Float>>, w: Float, h: Float, color: Color) {
    parts.forEach { (x, y, r) ->
        val c = Offset(w * x, h * y)
        val rad = w * r
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.95f),
                0.78f to color.copy(alpha = 0.85f),
                1f to color.copy(alpha = 0f),
                center = c,
                radius = rad,
            ),
            rad,
            c,
        )
    }
    // 구름 윗부분이 살짝 밝게
    parts.forEach { (x, y, r) ->
        val c = Offset(w * x, h * y - w * r * 0.35f)
        glow(c, w * r * 0.7f, KkaeuColors.CloudLight.copy(alpha = 0.18f))
    }
}

private fun DrawScope.road(w: Float, h: Float) {
    val vanish = Offset(w * 0.66f, h * 0.458f)
    val upper = Path().apply {
        moveTo(-w * 0.02f, h * 0.50f)
        quadraticTo(w * 0.32f, h * 0.462f, vanish.x, vanish.y)
    }
    val lower = Path().apply {
        moveTo(-w * 0.02f, h * 0.585f)
        quadraticTo(w * 0.36f, h * 0.49f, vanish.x, vanish.y + h * 0.004f)
    }
    val surface = Path().apply {
        addPath(upper)
        lineTo(vanish.x, vanish.y + h * 0.004f)
        quadraticTo(w * 0.36f, h * 0.49f, -w * 0.02f, h * 0.585f)
        close()
    }
    // 길은 마스코트 쪽에서만 또렷하고 지평선 쪽으로 갈수록 사라진다
    val fade = Brush.horizontalGradient(
        0f to Color(0x00C9CCFF), 0.1f to Color(0x99C9CCFF), 0.4f to Color(0x66C9CCFF), 1f to Color(0x00C9CCFF),
        startX = 0f, endX = vanish.x,
    )
    drawPath(surface, Brush.horizontalGradient(listOf(Color(0x8834408F), Color(0x223D4AA6)), 0f, vanish.x))
    drawPath(upper, fade, style = Stroke(h * 0.002f), alpha = 0.7f)
    drawPath(lower, fade, style = Stroke(h * 0.0026f))
    // 가운데 점선
    val center = Path().apply {
        moveTo(-w * 0.02f, h * 0.542f)
        quadraticTo(w * 0.34f, h * 0.476f, vanish.x, vanish.y + h * 0.002f)
    }
    drawPath(
        center,
        Brush.horizontalGradient(
            0f to Color(0x00DDE0FF), 0.1f to Color(0x99DDE0FF), 0.35f to Color(0x66DDE0FF), 0.8f to Color(0x00DDE0FF),
            startX = 0f, endX = vanish.x,
        ),
        style = Stroke(h * 0.0024f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.026f, h * 0.024f))),
    )
}

/** 작은 건물 몇 채 + 창문 불빛 */
private fun DrawScope.city(left: Float, baseY: Float, unit: Float, alpha: Float = 1f) {
    val buildings = listOf(0f to 3.2f, 2.2f to 4.6f, 4.0f to 2.6f, 6.6f to 5.4f, 8.6f to 3.6f, 10.4f to 4.2f, 12.6f to 2.4f)
    val bw = unit * 1.9f
    buildings.forEachIndexed { i, (x, hh) ->
        val top = baseY - hh * unit
        drawRect(Color(0xFF2A3478), Offset(left + x * unit, top), Size(bw, hh * unit), alpha = alpha)
        // 창문 불빛은 듬성듬성
        var wy = top + unit * 0.7f
        var row = 0
        while (wy < baseY - unit * 0.8f) {
            if ((row + i) % 2 == 0) {
                drawRect(Color(0x557C8AE0), Offset(left + x * unit + bw * 0.32f, wy), Size(bw * 0.14f, unit * 0.3f), alpha = alpha)
            }
            wy += unit * 1.1f
            row++
        }
    }
}

/** 경보 화면 오른쪽 언덕과 나무 실루엣. 아래쪽은 배경으로 서서히 사라진다 */
private fun DrawScope.hills(w: Float, h: Float) {
    val top = h * 0.52f
    val bottom = h * 0.86f
    val hill = Path().apply {
        moveTo(w * 0.58f, bottom)
        cubicTo(w * 0.74f, h * 0.70f, w * 0.84f, h * 0.555f, w * 1.02f, top)
        lineTo(w * 1.02f, bottom)
        close()
    }
    drawPath(
        hill,
        Brush.verticalGradient(0f to Color(0xE62E3888), 0.55f to Color(0x99283070), 1f to Color(0x00222B66), startY = top, endY = bottom),
    )
    // 능선 위 나무 (부드러운 동그라미)
    listOf(0.875f to 0.565f, 0.91f to 0.548f, 0.945f to 0.538f, 0.98f to 0.530f).forEachIndexed { i, (x, y) ->
        val c = Offset(w * x, h * y)
        val r = h * (0.026f + 0.006f * (i % 2))
        drawCircle(
            Brush.radialGradient(0f to Color(0xFF2C3584), 0.8f to Color(0xF02C3584), 1f to Color(0x002C3584), center = c, radius = r),
            r,
            c,
        )
    }
    city(left = w * 0.79f, baseY = h * 0.635f, unit = h * 0.007f, alpha = 0.55f)
}
