package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.BigButton
import com.sleepreaper.kkaeurang.ui.components.KkaeuTopBar
import com.sleepreaper.kkaeurang.ui.components.LevelIcon
import com.sleepreaper.kkaeurang.ui.components.blockSurface
import com.sleepreaper.kkaeurang.ui.components.durationText
import com.sleepreaper.kkaeurang.ui.components.labelRes
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.TripReport
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import com.sleepreaper.kkaeurang.ui.theme.levelColor
import kotlin.math.sin
import kotlin.random.Random

/**
 * ⑥ 주행 종료 리포트 (기어 P + tripReport 있을 때).
 * 축하 연출(색종이)은 정차 중인 이 화면에서만 쓴다.
 */
@Composable
fun TripReportScreen(
    report: TripReport,
    totalPoints: Int,
    onClose: () -> Unit,
    onLogoLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            KkaeuTopBar(onLogoLongPress = onLogoLongPress)

            Text(
                text = stringResource(R.string.report_headline, durationText(report.awakeMinutes), report.earnedPoints),
                style = KkaeuType.Emphasis,
            )

            val tiles = listOf<@Composable (Modifier) -> Unit>(
                { m -> ReportTile(stringResource(R.string.report_drive_time), durationText(report.driveMinutes), m) },
                { m -> ReportTile(stringResource(R.string.report_awake_time), durationText(report.awakeMinutes), m) },
                { m ->
                    ReportTile(
                        stringResource(R.string.report_points),
                        stringResource(R.string.report_points_value, report.earnedPoints),
                        m,
                        valueColor = KkaeuColors.Points,
                    )
                },
                { m ->
                    ReportTile(
                        stringResource(R.string.report_avg_response),
                        report.avgResponseMs?.let { stringResource(R.string.report_avg_response_value, it / 1000f) }
                            ?: stringResource(R.string.report_no_record),
                        m,
                    )
                },
                { m -> MaxLevelTile(report.maxLevel, m) },
                { m ->
                    ReportTile(
                        stringResource(R.string.report_event_count),
                        stringResource(R.string.report_count_value, report.eventCount),
                        m,
                    )
                },
                { m ->
                    ReportTile(
                        stringResource(R.string.report_rest_count),
                        stringResource(R.string.report_count_value, report.restCount),
                        m,
                    )
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                tiles.chunked(TILES_PER_ROW).forEach { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                        repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.report_total_points, totalPoints),
                    style = KkaeuType.Title.copy(fontWeight = FontWeight.Bold),
                    color = KkaeuColors.Points,
                )
                BigButton(text = stringResource(R.string.btn_close), onClick = onClose)
            }
        }
        Confetti(Modifier.fillMaxSize())
    }
}

private const val TILES_PER_ROW = 4

@Composable
private fun ReportTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = KkaeuColors.TextPrimary,
) {
    Column(
        modifier = modifier
            .blockSurface()
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(text = label, style = KkaeuType.Body, color = KkaeuColors.TextSecondary)
        Text(text = value, style = KkaeuType.Emphasis, color = valueColor)
    }
}

@Composable
private fun MaxLevelTile(level: DrowsyLevel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .blockSurface()
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(text = stringResource(R.string.report_max_level), style = KkaeuType.Body, color = KkaeuColors.TextSecondary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LevelIcon(level = level, modifier = Modifier.size(56.dp))
            Text(text = stringResource(level.labelRes()), style = KkaeuType.Emphasis, color = levelColor(level))
        }
    }
}

// ───────── 축하 색종이 (이 화면에서만, 4.5초 한 번) ─────────

private class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val phase: Float,
    val color: Color,
    val sizeDp: Float,
    val rotation: Float,
)

@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 4_500, easing = LinearEasing))
    }
    val pieces = remember {
        val rnd = Random(7)
        val palette = listOf(
            KkaeuColors.LevelNormal, KkaeuColors.LevelCaution, KkaeuColors.Points,
            KkaeuColors.RiskBadge, KkaeuColors.TextPrimary,
        )
        List(80) {
            ConfettiPiece(
                x = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.35f,
                speed = 0.8f + rnd.nextFloat() * 0.6f,
                sway = rnd.nextFloat() * 0.03f,
                phase = rnd.nextFloat() * 6.28f,
                color = palette[rnd.nextInt(palette.size)],
                sizeDp = 14f + rnd.nextFloat() * 14f,
                rotation = rnd.nextFloat() * 360f,
            )
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        pieces.forEach { c ->
            val t = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = -0.05f * size.height + t * c.speed * size.height * 1.1f
            val x = c.x * size.width + sin(c.phase + t * 12f) * c.sway * size.width
            val s = c.sizeDp.dp.toPx()
            rotate(degrees = c.rotation + t * 540f, pivot = Offset(x, y)) {
                drawRect(
                    color = c.color.copy(alpha = 1f - t * 0.5f),
                    topLeft = Offset(x - s / 2f, y - s / 4f),
                    size = Size(s, s / 2f),
                )
            }
        }
    }
}
