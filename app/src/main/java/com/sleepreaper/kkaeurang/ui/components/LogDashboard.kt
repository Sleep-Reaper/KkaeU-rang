package com.sleepreaper.kkaeurang.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.AlertCounts
import com.sleepreaper.kkaeurang.ui.model.DailyScore
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.TripSummary
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// "주행 로그"·"혜택" 탭과 주행 종료 리포트가 함께 쓰는 블록.
// 세 화면 모두 [제목 줄] → [숫자 카드 4장] → [큰 카드 + 오른쪽 카드 두 장] → [아래 카드] 구조이고,
// 홈 화면과 같은 연보라 카드·보라 강조색·굵은 글씨를 쓴다. 크기는 모두 시안 px.

const val LOG_KPI_H = 136
const val LOG_MAIN_H = 470
const val LOG_GAP = 22
const val LOG_SIDE_CARD_H = (LOG_MAIN_H - LOG_GAP) / 2
private const val CARD_RADIUS = 26

/** 카드 안 아이콘 그라데이션 (홈 "이번 주행 점수 로그"와 같은 남보라) */
val CardIconBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF5E52C9), Color(0xFF2E2A86)))

/** 카드 위 진한 남색 아이콘 */
val CardIconColor = Color(0xFF2E3170)

/** 연보라 카드 위에서 쓰는 진한 주황 (주의가 필요한 값) */
val CardWarnText = Color(0xFFB4561C)

// ───────────────────────── 제목 줄 ─────────────────────────

/** 화면 맨 위: 아이콘 + 큰 제목 + 설명 한 줄, 오른쪽에 버튼 */
@Composable
fun LogPageTitle(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val d = LocalDesign.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(d.dp(70)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KGradientIcon(icon, d.dp(46), Brush.verticalGradient(listOf(Color(0xFFC6A8FF), KkaeuColors.Accent)))
        Spacer(Modifier.width(d.dp(16)))
        Text(title, style = d.text(38, W.ExtraBold, KkaeuColors.TextPrimary), maxLines = 1)
        Spacer(Modifier.width(d.dp(20)))
        Text(
            subtitle,
            style = d.text(22, W.SemiBold, KkaeuColors.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(d.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

/** 제목 줄 오른쪽 둥근 버튼 ([primary]면 보라로 채움) */
@Composable
fun PagePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = false,
) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(d.dp(58))
            .clip(shape)
            .background(if (primary) AccentBrush else Brush.verticalGradient(listOf(KkaeuColors.PillFill, KkaeuColors.PillFill)))
            .border(d.dp(1.5f), if (primary) Color(0x66FFFFFF) else KkaeuColors.PillBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = d.dp(if (icon != null) 22 else 32), end = d.dp(32)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(10)),
    ) {
        if (icon != null) KIcon(icon, d.dp(28), KkaeuColors.TextPrimary)
        Text(text, style = d.text(24, W.Bold, KkaeuColors.TextPrimary), maxLines = 1)
    }
}

// ───────────────────────── 숫자 카드 ─────────────────────────

/** "1,234 km"처럼 숫자는 크게, 단위는 작게 */
@Composable
fun valueWithUnit(value: String, unit: String?, unitPx: Int = 22): AnnotatedString {
    val d = LocalDesign.current
    return buildAnnotatedString {
        append(value)
        if (!unit.isNullOrEmpty()) {
            withStyle(SpanStyle(fontSize = d.sp(unitPx), fontWeight = W.Bold)) {
                append(" ")
                append(unit)
            }
        }
    }
}

/** 숫자 하나를 크게 보여 주는 연보라 카드: 아이콘 + 이름 / 값 / 보조 설명 */
@Composable
fun KpiCard(
    @DrawableRes icon: Int,
    label: String,
    value: AnnotatedString,
    sub: String?,
    modifier: Modifier = Modifier,
    subColor: Color = KkaeuColors.CardTextSub,
    iconContent: (@Composable () -> Unit)? = null,
) {
    val d = LocalDesign.current
    Row(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .padding(start = d.dp(22), end = d.dp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(d.dp(62))
                .clip(CircleShape)
                .background(Color(0x99FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconContent != null) iconContent() else KGradientIcon(icon, d.dp(36), CardIconBrush)
        }
        Spacer(Modifier.width(d.dp(16)))
        Column(verticalArrangement = Arrangement.spacedBy(d.dp(1))) {
            Text(label, style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = d.text(40, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.15f), maxLines = 1)
            if (sub != null) {
                Text(sub, style = d.text(17, W.Medium, subColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── 제목 있는 카드 ─────────────────────────

/** 연보라 카드 + 제목 줄(아이콘·제목·오른쪽 보조) + 내용 */
@Composable
fun SectionCard(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val d = LocalDesign.current
    Column(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .padding(start = d.dp(30), end = d.dp(28), top = d.dp(20), bottom = d.dp(18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(d.dp(40))) {
            KGradientIcon(icon, d.dp(34), CardIconBrush)
            Spacer(Modifier.width(d.dp(14)))
            Text(title, style = d.text(25, W.ExtraBold, KkaeuColors.CardText), maxLines = 1, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/** 카드 제목 오른쪽 작은 알약 ("평균 93점", "총 17회") */
@Composable
fun CardChip(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    val d = LocalDesign.current
    Text(
        text = text,
        style = d.text(19, W.Bold, if (strong) Color.White else KkaeuColors.AccentDeep),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) AccentBrush else Brush.verticalGradient(listOf(Color(0xFFF2EDFF), KkaeuColors.AccentSoft)))
            .padding(horizontal = d.dp(16), vertical = d.dp(5)),
    )
}

/** 카드 안 가는 가로줄 */
@Composable
fun CardRule(modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(
        modifier
            .fillMaxWidth()
            .height(d.dp(1.5f))
            .background(KkaeuColors.CardDivider.copy(alpha = 0.6f))
    )
}

// ───────────────────────── 졸음 경보 기록 ─────────────────────────

/** 주의·경고·위험 횟수 세 칸 (단계 아이콘 + 이름 + 횟수) */
@Composable
fun AlertCountsContent(counts: AlertCounts, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            DrowsyLevel.CAUTION to counts.caution,
            DrowsyLevel.WARNING to counts.warning,
            DrowsyLevel.CRITICAL to counts.critical,
        ).forEachIndexed { i, (level, n) ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(d.dp(1.5f))
                        .height(d.dp(90))
                        .background(KkaeuColors.CardDivider)
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d.dp(4)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(8))) {
                    LevelIcon(level, Modifier.size(d.dp(30)))
                    Text(stringResource(level.labelRes()), style = d.text(21, W.Bold, KkaeuColors.CardTextSub))
                }
                Text(
                    valueWithUnit(n.toString(), stringResource(R.string.unit_count), unitPx = 22),
                    style = d.text(40, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.15f),
                )
            }
        }
    }
}

// ───────────────────────── 작은 값 2×2 ─────────────────────────

/** "운전 습관" 칸 하나. [level]이 있으면 아이콘 대신 단계 아이콘 */
data class MiniStat(
    @param:DrawableRes val icon: Int,
    val label: String,
    val value: String,
    val level: DrowsyLevel? = null,
    val warn: Boolean = false,
)

@Composable
fun MiniStatGrid(items: List<MiniStat>, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(d.dp(14))) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.dp(16))) {
                row.forEach { MiniStatCell(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatCell(item: MiniStat, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(d.dp(48))
                .clip(CircleShape)
                .background(Color(0x99FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.level != null) LevelIcon(item.level, Modifier.size(d.dp(30)), mark = if (item.level == DrowsyLevel.NORMAL) Color.White else KkaeuColors.OnLight)
            else KIcon(item.icon, d.dp(26), CardIconColor)
        }
        Spacer(Modifier.width(d.dp(12)))
        Column {
            Text(item.label, style = d.text(17, W.SemiBold, KkaeuColors.CardTextSub), maxLines = 1)
            Text(
                item.value,
                style = d.text(26, W.ExtraBold, if (item.warn) CardWarnText else KkaeuColors.CardText, lineHeight = 1.15f),
                maxLines = 1,
            )
        }
    }
}

// ───────────────────────── 날짜별 운전 점수 막대 ─────────────────────────

/**
 * 날짜별 운전 점수 막대그래프. 50~100점 범위, 오늘 막대는 진하게 + 말풍선, 80점 밑은 주황,
 * 주행이 없던 날은 바닥에 점만. 평균은 점선.
 */
@Composable
fun DailyScoreChart(days: List<DailyScore>, average: Int?, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val measurer = rememberTextMeasurer()
    val todayLabel = stringResource(R.string.daily_today)
    val dateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
    val res = LocalContext.current.resources
    Canvas(modifier) {
        if (days.isEmpty()) return@Canvas
        fun px(v: Float) = d.dp(v).toPx()
        val axisW = px(44f)
        val top = px(40f)
        val bottom = size.height - px(34f)
        val lo = 50f
        val hi = 100f
        fun y(v: Float) = bottom - (bottom - top) * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        val small = TextStyle(fontSize = d.sp(16), fontWeight = W.SemiBold, color = KkaeuColors.CardTextSub)

        // 눈금선과 왼쪽 숫자
        listOf(60, 70, 80, 90, 100).forEach { v ->
            val yy = y(v.toFloat())
            drawLine(KkaeuColors.CardDivider.copy(alpha = 0.55f), Offset(axisW, yy), Offset(size.width, yy), strokeWidth = px(1.2f))
            if (v % 20 == 0) {
                val t = measurer.measure(v.toString(), small)
                drawText(t, topLeft = Offset(axisW - t.size.width - px(10f), yy - t.size.height / 2f))
            }
        }

        // 평균 점선 (막대 뒤에. 값은 카드 제목 옆 "평균 93점"에 있다)
        if (average != null) {
            val ay = y(average.toFloat())
            drawLine(
                KkaeuColors.AccentDeep.copy(alpha = 0.45f),
                Offset(axisW, ay),
                Offset(size.width, ay),
                strokeWidth = px(2f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(8f), px(6f))),
            )
        }

        val slot = (size.width - axisW) / days.size
        val barW = slot * 0.46f
        days.forEachIndexed { i, day ->
            val cx = axisW + slot * (i + 0.5f)
            val isToday = i == days.lastIndex
            val label = if (isToday) todayLabel else day.date.format(dateFormat)
            val lt = measurer.measure(label, small.copy(fontWeight = if (isToday) W.ExtraBold else W.SemiBold, color = if (isToday) KkaeuColors.AccentDeep else KkaeuColors.CardTextSub))
            drawText(lt, topLeft = Offset(cx - lt.size.width / 2f, bottom + px(8f)))

            val score = day.score
            if (score == null) {
                drawCircle(KkaeuColors.CardDivider, radius = px(5f), center = Offset(cx, bottom - px(6f)))
                return@forEachIndexed
            }
            val yy = y(score.toFloat())
            val r = barW / 2f
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(cx - barW / 2f, yy, cx + barW / 2f, bottom),
                        topLeft = CornerRadius(r), topRight = CornerRadius(r),
                        bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero,
                    )
                )
            }
            val brush = when {
                isToday -> Brush.verticalGradient(listOf(Color(0xFF7A55F5), Color(0xFF4A2BC9)), yy, bottom)
                score < 80 -> Brush.verticalGradient(listOf(Color(0xFFF5B06A), Color(0xFFE07B3C)), yy, bottom)
                else -> Brush.verticalGradient(listOf(Color(0xFFB59BFF), Color(0x998B5CF6)), yy, bottom)
            }
            drawPath(path, brush)
            if (isToday) {
                // 오늘 점수 말풍선
                val t = measurer.measure(res.getString(R.string.score_log_point, score), TextStyle(fontSize = d.sp(17), fontWeight = W.Bold, color = Color.White))
                val w = t.size.width + px(22f)
                val h = t.size.height + px(6f)
                val left = (cx - w / 2f).coerceAtMost(size.width - w)
                drawRoundRect(AccentBrush, topLeft = Offset(left, yy - h - px(8f)), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
                drawText(t, topLeft = Offset(left + px(11f), yy - h - px(8f) + px(3f)))
            } else {
                val t = measurer.measure(score.toString(), small.copy(fontSize = d.sp(15)))
                drawText(t, topLeft = Offset(cx - t.size.width / 2f, yy - t.size.height - px(4f)))
            }
        }

    }
}

// ───────────────────────── 시간대별 졸음 경보 ─────────────────────────

/** 3시간 단위 8칸 막대 (0시, 3시 … 21시). 졸음 위험 시간대(새벽 2~5시, 오후 2~4시)가 걸친 칸은 주황 */
@Composable
fun HourlyAlertChart(bins: List<Int>, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val measurer = rememberTextMeasurer()
    val labels = List(bins.size) { stringResource(R.string.hourly_label, it * 24 / bins.size.coerceAtLeast(1)) }
    Canvas(modifier) {
        if (bins.isEmpty()) return@Canvas
        fun px(v: Float) = d.dp(v).toPx()
        val top = px(26f)
        val bottom = size.height - px(30f)
        val maxV = (bins.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = size.width / bins.size
        val barW = slot * 0.5f
        val small = TextStyle(fontSize = d.sp(15), fontWeight = W.SemiBold, color = KkaeuColors.CardTextSub)
        drawLine(KkaeuColors.CardDivider, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = px(1.5f))
        bins.forEachIndexed { i, n ->
            val cx = slot * (i + 0.5f)
            val risky = i in RISK_BINS
            val h = (bottom - top) * n / maxV
            if (n > 0) {
                val r = barW / 2f
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(cx - barW / 2f, bottom - h, cx + barW / 2f, bottom),
                            topLeft = CornerRadius(r), topRight = CornerRadius(r),
                            bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(
                    path,
                    if (risky) Brush.verticalGradient(listOf(Color(0xFFF5B06A), Color(0xFFE07B3C)), bottom - h, bottom)
                    else Brush.verticalGradient(listOf(Color(0xFFB59BFF), Color(0x998B5CF6)), bottom - h, bottom),
                )
            }
            val vt = measurer.measure(n.toString(), small.copy(fontWeight = W.Bold, color = if (risky) CardWarnText else KkaeuColors.CardText))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, bottom - h - vt.size.height - px(2f)))
            val lt = measurer.measure(labels[i], small)
            drawText(lt, topLeft = Offset(cx - lt.size.width / 2f, bottom + px(6f)))
        }
    }
}

/** 졸음 위험 시간대(02~05시, 14~16시)가 걸친 3시간 칸: 0~3, 3~6, 12~15, 15~18시 */
private val RISK_BINS = setOf(0, 1, 4, 5)

/** 범례 한 줄: 색 네모 + 설명 */
@Composable
fun ChartLegend(color: Color, text: String, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(8))) {
        Box(
            Modifier
                .size(d.dp(14))
                .clip(RoundedCornerShape(d.dp(4)))
                .background(color)
        )
        Text(text, style = d.text(16, W.SemiBold, KkaeuColors.CardTextSub), maxLines = 1)
    }
}

// ───────────────────────── 최근 주행 한 줄 ─────────────────────────

@Composable
fun RecentTripRow(trip: TripSummary, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val dateFormat = DateTimeFormatter.ofPattern(stringResource(R.string.recent_date), Locale.KOREA)
    val start = Instant.ofEpochMilli(trip.startedAtMs).atZone(ZoneId.systemDefault())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(d.dp(58)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(start.format(dateFormat), style = d.text(20, W.Bold, KkaeuColors.CardText), maxLines = 1)
            Text(
                stringResource(R.string.recent_meta, durationText(trip.driveMinutes), trip.distanceKm),
                style = d.text(17, W.Medium, KkaeuColors.CardTextSub),
                maxLines = 1,
            )
        }
        LevelIcon(trip.maxLevel, Modifier.size(d.dp(28)), mark = if (trip.maxLevel == DrowsyLevel.NORMAL) Color.White else KkaeuColors.OnLight)
        Spacer(Modifier.width(d.dp(14)))
        Box(Modifier.width(d.dp(92)), contentAlignment = Alignment.Center) {
            CardChip(stringResource(R.string.score_log_point, trip.averageScore), strong = trip.averageScore >= 90)
        }
        Spacer(Modifier.width(d.dp(10)))
        Row(Modifier.width(d.dp(96)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text(stringResource(R.string.recent_points, trip.earnedPoints), style = d.text(19, W.Bold, Color(0xFF8A5200)), maxLines = 1)
        }
    }
}

/** 세로로 꽉 찬 오른쪽 열 (오른쪽 카드 두 장) */
@Composable
fun RowScope.SideColumn(weight: Float, content: @Composable ColumnScope.() -> Unit) {
    val d = LocalDesign.current
    Column(
        Modifier
            .weight(weight)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        content = content,
    )
}
