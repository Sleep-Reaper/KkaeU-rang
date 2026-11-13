package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.AlertCountsContent
import com.sleepreaper.kkaeurang.ui.components.CardChip
import com.sleepreaper.kkaeurang.ui.components.CardIconColor
import com.sleepreaper.kkaeurang.ui.components.CardRule
import com.sleepreaper.kkaeurang.ui.components.CardWarnText
import com.sleepreaper.kkaeurang.ui.components.KIcon
import com.sleepreaper.kkaeurang.ui.components.KpiCard
import com.sleepreaper.kkaeurang.ui.components.LOG_GAP
import com.sleepreaper.kkaeurang.ui.components.LOG_KPI_H
import com.sleepreaper.kkaeurang.ui.components.LOG_MAIN_H
import com.sleepreaper.kkaeurang.ui.components.LevelIcon
import com.sleepreaper.kkaeurang.ui.components.LogPageTitle
import com.sleepreaper.kkaeurang.ui.components.MiniStat
import com.sleepreaper.kkaeurang.ui.components.MiniStatGrid
import com.sleepreaper.kkaeurang.ui.components.PagePillButton
import com.sleepreaper.kkaeurang.ui.components.PointCoin
import com.sleepreaper.kkaeurang.ui.components.ScoreChart
import com.sleepreaper.kkaeurang.ui.components.SectionCard
import com.sleepreaper.kkaeurang.ui.components.SideColumn
import com.sleepreaper.kkaeurang.ui.components.downsample
import com.sleepreaper.kkaeurang.ui.components.durationText
import com.sleepreaper.kkaeurang.ui.components.labelRes
import com.sleepreaper.kkaeurang.ui.components.valueWithUnit
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.TripLogEntry
import com.sleepreaper.kkaeurang.ui.model.TripLogKind
import com.sleepreaper.kkaeurang.ui.model.TripReport
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/** 리포트 점수 흐름 그래프의 점 수 */
private const val REPORT_CHART_POINTS = 12

/** 상세 로그에 보여 줄 최대 줄 수 (오래된 것부터 잘라 낸다) */
private const val MAX_DETAIL_ROWS = 150

/**
 * 주행 종료 리포트 (기어 P + tripReport 있을 때 홈 자리에 뜬다).
 * "주행 로그" 탭([DriveLogTab])과 같은 구조로, 이번 주행 한 번의 값을 보여 준다.
 *
 * [제목] 이번 주행 리포트 · 시간 범위 + [상세 로그] [닫기]
 * [숫자 4장] 주행 시간 · 주행 거리 · 평균 안전 점수 · 획득 포인트
 * [가운데] 이번 주행 점수 흐름 | 졸음 경보 기록 + 운전 습관
 * [아래, 스크롤] 상세 로그 (시작·컨디션·졸음 신호·단계 변화·질문과 대답·경고·창문·종료를 시간순으로)
 *
 * 축하 연출(색종이)은 정차 중인 이 화면에서만 쓴다.
 */
@Composable
fun TripReportScreen(
    report: TripReport,
    totalPoints: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var detailY by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX), top = d.dp(4), bottom = d.dp(36)),
            verticalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        ) {
            LogPageTitle(
                icon = R.drawable.ic_k_location,
                title = stringResource(R.string.report_title),
                subtitle = stringResource(R.string.report_subtitle, timeRangeText(report)),
            ) {
                PagePillButton(
                    text = stringResource(R.string.report_detail_button),
                    icon = R.drawable.ic_k_list,
                    onClick = { scope.launch { scroll.animateScrollTo(detailY) } },
                )
                PagePillButton(text = stringResource(R.string.btn_close), onClick = onClose, primary = true)
            }

            // ── 숫자 4장 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(d.dp(LOG_KPI_H)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
            ) {
                val kpi = Modifier.weight(1f).fillMaxSize()
                KpiCard(
                    icon = R.drawable.ic_k_clock_outline,
                    label = stringResource(R.string.report_drive_time),
                    value = AnnotatedString(durationText(report.driveMinutes)),
                    sub = stringResource(R.string.report_kpi_time_sub, durationText(report.awakeMinutes)),
                    modifier = kpi,
                )
                val avgSpeed = if (report.driveMinutes > 0) (report.distanceKm / (report.driveMinutes / 60f)).roundToInt() else 0
                KpiCard(
                    icon = R.drawable.ic_k_road,
                    label = stringResource(R.string.trip_card_distance),
                    value = valueWithUnit(stringResource(R.string.trip_card_km_value, report.distanceKm), stringResource(R.string.stats_km_unit)),
                    sub = stringResource(R.string.report_kpi_distance_sub, avgSpeed),
                    modifier = kpi,
                )
                KpiCard(
                    icon = R.drawable.ic_k_star,
                    label = stringResource(R.string.report_avg_safety),
                    value = valueWithUnit(report.averageSafety?.toString() ?: stringResource(R.string.stats_no_value), stringResource(R.string.unit_point)),
                    sub = stringResource(R.string.report_kpi_score_sub, report.startSafety),
                    modifier = kpi,
                )
                KpiCard(
                    icon = R.drawable.ic_k_gift,
                    label = stringResource(R.string.report_points),
                    value = valueWithUnit(stringResource(R.string.trip_card_points_value, report.earnedPoints), stringResource(R.string.points_unit)),
                    sub = stringResource(R.string.report_kpi_points_sub, totalPoints),
                    modifier = kpi,
                    iconContent = { PointCoin(d.dp(40)) },
                )
            }

            // ── 가운데: 점수 흐름 | 경보 기록 + 운전 습관 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(d.dp(LOG_MAIN_H)),
                horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
            ) {
                SectionCard(
                    title = stringResource(R.string.report_chart_title),
                    icon = R.drawable.ic_k_bar_chart,
                    trailing = { report.averageSafety?.let { CardChip(stringResource(R.string.daily_avg_chip, it)) } },
                    modifier = Modifier
                        .weight(1.52f)
                        .fillMaxSize(),
                ) {
                    val points = downsample(report.safetyHistory, REPORT_CHART_POINTS)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = d.dp(10)),
                    ) {
                        if (points.size >= 2) {
                            ScoreChart(points, Modifier.fillMaxSize())
                        } else {
                            Text(
                                stringResource(R.string.score_log_collecting),
                                style = d.text(20, W.SemiBold, KkaeuColors.CardTextSub),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = d.dp(8))) {
                        val axis = d.text(18, W.Medium, KkaeuColors.CardTextSub)
                        Text(stringResource(R.string.score_log_start), style = axis)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.score_log_mid), style = axis)
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.report_chart_end), style = axis)
                    }
                }
                SideColumn(1f) {
                    SectionCard(
                        title = stringResource(R.string.alerts_title),
                        icon = R.drawable.ic_k_siren,
                        trailing = {
                            CardChip(
                                if (report.alertCounts.total == 0) stringResource(R.string.alerts_none)
                                else stringResource(R.string.alerts_total, report.alertCounts.total)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        AlertCountsContent(report.alertCounts)
                        Spacer(Modifier.weight(1f))
                    }
                    SectionCard(
                        title = stringResource(R.string.habits_title),
                        icon = R.drawable.ic_k_timer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        MiniStatGrid(
                            listOf(
                                MiniStat(R.drawable.ic_k_clock_outline, stringResource(R.string.habit_awake), durationText(report.awakeMinutes)),
                                MiniStat(
                                    R.drawable.ic_k_timer,
                                    stringResource(R.string.report_avg_response),
                                    report.avgResponseMs?.let { stringResource(R.string.habit_response_value, it / 1000f) }
                                        ?: stringResource(R.string.report_no_record),
                                ),
                                MiniStat(
                                    R.drawable.ic_k_road,
                                    stringResource(R.string.habit_events),
                                    stringResource(R.string.report_count_value, report.eventCount),
                                    warn = report.eventCount > 0,
                                ),
                                MiniStat(
                                    R.drawable.ic_k_siren,
                                    stringResource(R.string.report_max_level),
                                    stringResource(report.maxLevel.labelRes()),
                                    level = report.maxLevel,
                                    warn = report.maxLevel >= DrowsyLevel.WARNING,
                                ),
                            )
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── 아래: 상세 로그 ──
            val rows = report.logs.takeLast(MAX_DETAIL_ROWS)
            SectionCard(
                title = stringResource(R.string.report_detail_title),
                icon = R.drawable.ic_k_list,
                trailing = { CardChip(stringResource(R.string.report_detail_count, report.logs.size)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { detailY = it.positionInParent().y.roundToInt() },
            ) {
                Spacer(Modifier.height(d.dp(8)))
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.report_detail_empty),
                        style = d.text(20, W.SemiBold, KkaeuColors.CardTextSub),
                        modifier = Modifier.padding(vertical = d.dp(20)),
                    )
                }
                rows.forEachIndexed { i, entry ->
                    if (i > 0) CardRule()
                    TripLogRow(entry)
                }
            }
        }
        Confetti(Modifier.fillMaxSize())
    }
}

/** "11월 13일 14:02 ~ 15:14" (시각을 모르면 빈 문자열) */
@Composable
private fun timeRangeText(report: TripReport): String {
    val start = report.startedAtMs ?: return stringResource(R.string.report_drive_time)
    val startText = SimpleDateFormat(stringResource(R.string.report_time_start), Locale.KOREA).format(Date(start))
    val end = report.endedAtMs ?: return startText
    return startText + " ~ " + SimpleDateFormat(stringResource(R.string.report_time_end), Locale.KOREA).format(Date(end))
}

/** 상세 로그 한 줄: 시각 · 종류 아이콘 · 내용 · 안전 점수 변화 */
@Composable
private fun TripLogRow(entry: TripLogEntry) {
    val d = LocalDesign.current
    val timeFormat = SimpleDateFormat(stringResource(R.string.report_detail_time), Locale.KOREA)
    Row(
        Modifier
            .fillMaxWidth()
            .height(d.dp(52)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeFormat.format(Date(entry.timeMs)),
            style = d.text(18, W.SemiBold, KkaeuColors.CardTextSub),
            modifier = Modifier.width(d.dp(112)),
        )
        Box(
            Modifier
                .size(d.dp(38))
                .clip(CircleShape)
                .background(Color(0x99FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            val level = entry.level
            if (level != null) {
                LevelIcon(level, Modifier.size(d.dp(26)), mark = if (level == DrowsyLevel.NORMAL) Color.White else KkaeuColors.OnLight)
            } else {
                val (icon, tint) = kindIcon(entry.kind)
                KIcon(icon, d.dp(22), tint)
            }
        }
        Spacer(Modifier.width(d.dp(14)))
        Text(
            entry.message,
            style = d.text(20, if (entry.kind == TripLogKind.LEVEL_UP) W.ExtraBold else W.SemiBold, KkaeuColors.CardText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.safetyDelta?.let { delta ->
            Text(
                stringResource(R.string.report_detail_delta, delta),
                style = d.text(19, W.ExtraBold, if (delta < 0) CardWarnText else KkaeuColors.AccentDeep),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCCFFFFFF))
                    .padding(horizontal = d.dp(14), vertical = d.dp(3)),
            )
        }
    }
}

private fun kindIcon(kind: TripLogKind): Pair<Int, Color> = when (kind) {
    TripLogKind.START -> R.drawable.ic_k_car to KkaeuColors.AccentDeep
    TripLogKind.CONDITION -> R.drawable.ic_k_bed to Color(0xFF4F67EE)
    TripLogKind.LEVEL_UP, TripLogKind.LEVEL_DOWN -> R.drawable.ic_k_bar_chart to CardIconColor
    TripLogKind.EVENT -> R.drawable.ic_k_road to CardWarnText
    TripLogKind.QUESTION -> R.drawable.ic_ms_record_voice_over to KkaeuColors.AccentDeep
    TripLogKind.ANSWER -> R.drawable.ic_ms_mic to Color(0xFF2E8B63)
    TripLogKind.ALARM -> R.drawable.ic_ms_notifications_active to CardWarnText
    TripLogKind.VEHICLE -> R.drawable.ic_k_window to Color(0xFF2F62D8)
    TripLogKind.REST -> R.drawable.ic_k_cafe to Color(0xFF2E8B63)
    TripLogKind.END -> R.drawable.ic_k_location to KkaeuColors.AccentDeep
    TripLogKind.OTHER -> R.drawable.ic_k_list to CardIconColor
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
