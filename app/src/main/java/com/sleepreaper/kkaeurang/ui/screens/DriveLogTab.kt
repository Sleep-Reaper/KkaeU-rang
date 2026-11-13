package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.AlertCountsContent
import com.sleepreaper.kkaeurang.ui.components.CardChip
import com.sleepreaper.kkaeurang.ui.components.CardRule
import com.sleepreaper.kkaeurang.ui.components.ChartLegend
import com.sleepreaper.kkaeurang.ui.components.DailyScoreChart
import com.sleepreaper.kkaeurang.ui.components.HourlyAlertChart
import com.sleepreaper.kkaeurang.ui.components.KpiCard
import com.sleepreaper.kkaeurang.ui.components.LOG_GAP
import com.sleepreaper.kkaeurang.ui.components.LOG_KPI_H
import com.sleepreaper.kkaeurang.ui.components.LOG_MAIN_H
import com.sleepreaper.kkaeurang.ui.components.LogPageTitle
import com.sleepreaper.kkaeurang.ui.components.MiniStat
import com.sleepreaper.kkaeurang.ui.components.MiniStatGrid
import com.sleepreaper.kkaeurang.ui.components.RecentTripRow
import com.sleepreaper.kkaeurang.ui.components.SectionCard
import com.sleepreaper.kkaeurang.ui.components.SideColumn
import com.sleepreaper.kkaeurang.ui.components.durationText
import com.sleepreaper.kkaeurang.ui.components.valueWithUnit
import com.sleepreaper.kkaeurang.ui.model.DriveLogSummary
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 서울–부산 거리 (km). "총 주행 거리" 카드의 재미 삼아 붙이는 비교 */
private const val SEOUL_BUSAN_KM = 325f

/**
 * 주행 로그 탭: 깨우랑과 함께한 모든 주행을 한눈에.
 *
 * [제목] 깨우랑과 함께 달린 날
 * [숫자 4장] 함께한 시간 · 총 주행 거리 · 운전 점수 평균 · 경고 없이 달린 날
 * [가운데] 날짜별 운전 점수 막대 | 졸음 경보 기록 + 운전 습관
 * [아래, 스크롤] 최근 주행 | 시간대별 졸음 경보
 *
 * 값은 [DriveLogSummary] 하나만 본다. 지금은 임시 값(SampleRecords)이고, 주행 기록 저장소가 생기면
 * RecordsRepository 구현만 바꾸면 된다. 주행 종료 리포트([TripReportScreen])도 같은 구조다.
 */
@Composable
fun DriveLogTab(log: DriveLogSummary, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val sinceText = log.since?.format(DateTimeFormatter.ofPattern(stringResource(R.string.log_date_long), Locale.KOREA))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX), top = d.dp(4), bottom = d.dp(36)),
        verticalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
    ) {
        LogPageTitle(
            icon = R.drawable.ic_k_bar_chart,
            title = stringResource(R.string.log_title),
            subtitle = sinceText?.let { stringResource(R.string.log_subtitle, it) } ?: stringResource(R.string.log_subtitle_empty),
        )

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
                label = stringResource(R.string.kpi_together),
                value = valueWithUnit("%,d".format(log.totalDriveMinutes / 60), stringResource(R.string.unit_hour)),
                sub = stringResource(R.string.kpi_together_sub, log.tripCount),
                modifier = kpi,
            )
            KpiCard(
                icon = R.drawable.ic_k_road,
                label = stringResource(R.string.kpi_distance),
                value = valueWithUnit("%,d".format(log.totalDistanceKm.roundToInt()), stringResource(R.string.stats_km_unit)),
                sub = stringResource(R.string.kpi_distance_sub, (log.totalDistanceKm / SEOUL_BUSAN_KM).roundToInt()),
                modifier = kpi,
            )
            KpiCard(
                icon = R.drawable.ic_k_star,
                label = stringResource(R.string.kpi_avg_score),
                value = valueWithUnit(log.averageScore?.toString() ?: stringResource(R.string.stats_no_value), stringResource(R.string.unit_point)),
                sub = log.averageScoreChange?.let { stringResource(R.string.kpi_avg_score_sub, it) },
                modifier = kpi,
            )
            KpiCard(
                icon = R.drawable.ic_k_flame,
                label = stringResource(R.string.kpi_streak),
                value = valueWithUnit(log.safeStreakDays.toString(), stringResource(R.string.kpi_streak_unit)),
                sub = stringResource(R.string.kpi_streak_sub, log.bestStreakDays),
                modifier = kpi,
            )
        }

        // ── 가운데: 날짜별 점수 | 경보 기록 + 운전 습관 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.dp(LOG_MAIN_H)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        ) {
            SectionCard(
                title = stringResource(R.string.daily_title),
                icon = R.drawable.ic_k_calendar,
                trailing = { log.averageScore?.let { CardChip(stringResource(R.string.daily_avg_chip, it)) } },
                modifier = Modifier
                    .weight(1.52f)
                    .fillMaxSize(),
            ) {
                DailyScoreChart(
                    days = log.dailyScores,
                    average = log.averageScore,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = d.dp(10)),
                )
                Text(
                    stringResource(R.string.daily_caption, log.dailyScores.size),
                    style = d.text(16, W.Medium, KkaeuColors.CardTextSub),
                    maxLines = 1,
                )
            }
            SideColumn(1f) {
                SectionCard(
                    title = stringResource(R.string.alerts_title),
                    icon = R.drawable.ic_k_siren,
                    trailing = { CardChip(stringResource(R.string.alerts_total, log.alertCounts.total)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Spacer(Modifier.weight(1f))
                    AlertCountsContent(log.alertCounts)
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
                    val driveDays = log.dailyScores.filter { it.score != null }
                    MiniStatGrid(
                        listOf(
                            MiniStat(
                                R.drawable.ic_k_timer,
                                stringResource(R.string.habit_response),
                                log.avgResponseMs?.let { stringResource(R.string.habit_response_value, it / 1000f) }
                                    ?: stringResource(R.string.report_no_record),
                            ),
                            MiniStat(R.drawable.ic_k_cafe, stringResource(R.string.habit_rest), stringResource(R.string.report_count_value, log.restCount)),
                            MiniStat(R.drawable.ic_k_gift, stringResource(R.string.habit_points), stringResource(R.string.habit_points_value, log.totalEarnedPoints)),
                            MiniStat(
                                R.drawable.ic_k_car,
                                stringResource(R.string.habit_daily_drive),
                                durationText(if (driveDays.isEmpty()) 0 else driveDays.sumOf { it.driveMinutes } / driveDays.size),
                            ),
                        )
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── 아래: 최근 주행 | 시간대별 졸음 경보 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.dp(330)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        ) {
            SectionCard(
                title = stringResource(R.string.recent_title),
                icon = R.drawable.ic_k_car,
                modifier = Modifier
                    .weight(1.52f)
                    .fillMaxSize(),
            ) {
                Spacer(Modifier.height(d.dp(8)))
                log.recentTrips.forEachIndexed { i, trip ->
                    if (i > 0) CardRule()
                    RecentTripRow(trip)
                }
            }
            SectionCard(
                title = stringResource(R.string.hourly_title),
                icon = R.drawable.ic_ms_schedule,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                HourlyAlertChart(
                    bins = log.hourlyAlerts,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = d.dp(8)),
                )
                ChartLegend(Color(0xFFE88A48), stringResource(R.string.hourly_legend_risk), Modifier.align(Alignment.Start))
            }
        }
    }
}
