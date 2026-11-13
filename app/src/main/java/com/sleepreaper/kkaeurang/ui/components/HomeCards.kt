package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.SleepRange
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.model.RestStopInfo
import com.sleepreaper.kkaeurang.ui.model.SleepCondition
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// 홈 화면 연보라 카드들 (시안 px 기준)

private const val CARD_RADIUS = 26

// ───────────────────────── 오른쪽 위: 현재 컨디션 ─────────────────────────

/**
 * "현재 컨디션 / 기상 후 3시간 경과 / 수면 7시간 20분 · 10/12 01:30".
 * 수면 기록이 없으면 출발 전 컨디션 대답([sleepRange])으로 "수면 6~8시간 / 오늘의 시작 점수 95점"
 */
@Composable
fun ConditionCard(
    sleep: SleepCondition?,
    sleepRange: SleepRange?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    Row(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .clickable(onClick = onClick)
            .padding(start = d.dp(30), end = d.dp(22)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BedIcon(Modifier.size(d.dp(88)))
        Spacer(Modifier.width(d.dp(22)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(4))) {
            Text(stringResource(R.string.condition_title), style = d.text(20, W.Bold, KkaeuColors.CardText))
            if (sleep != null) {
                Text(
                    stringResource(R.string.condition_awake, durationText(sleep.awakeMinutes)),
                    style = d.text(31, W.ExtraBold, KkaeuColors.CardText),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.condition_sleep, durationText(sleep.sleepMinutes)) +
                        "  ·  " + SLEEP_TIME_FORMAT.format(Date(sleep.sleepStartMs)),
                    style = d.text(19, W.Medium, KkaeuColors.CardTextSub),
                    maxLines = 1,
                )
            } else if (sleepRange != null) {
                Text(
                    stringResource(R.string.condition_checkin_title, sleepRangeLabel(sleepRange)),
                    style = d.text(31, W.ExtraBold, KkaeuColors.CardText),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.condition_checkin_sub, sleepRange.startSafety),
                    style = d.text(19, W.Medium, KkaeuColors.CardTextSub),
                    maxLines = 1,
                )
            } else {
                Text(stringResource(R.string.condition_empty_title), style = d.text(31, W.ExtraBold, KkaeuColors.CardText), maxLines = 1)
                Text(stringResource(R.string.condition_empty_sub), style = d.text(19, W.Medium, KkaeuColors.CardTextSub), maxLines = 1)
            }
        }
        ChevronCircle(d.dp(46))
    }
}

private val SLEEP_TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

/** "4시간 미만" · "4~6시간" · "6~8시간" · "8시간 이상" */
@Composable
fun sleepRangeLabel(range: SleepRange): String = stringResource(
    when (range) {
        SleepRange.UNDER_4H -> R.string.sleep_under_4h
        SleepRange.H4_TO_6 -> R.string.sleep_4_to_6h
        SleepRange.H6_TO_8 -> R.string.sleep_6_to_8h
        SleepRange.OVER_8H -> R.string.sleep_over_8h
    }
)

/** 파란 침대 + zZ */
@Composable
private fun BedIcon(modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(modifier) {
        KGradientIcon(
            R.drawable.ic_k_bed,
            d.dp(76),
            Brush.verticalGradient(listOf(Color(0xFF8FB0FF), Color(0xFF4F67EE))),
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = d.sp(17))) { append("z") }
                withStyle(SpanStyle(fontSize = d.sp(23), baselineShift = BaselineShift(0.35f))) { append("Z") }
            },
            style = d.text(20, W.ExtraBold, Color(0xFF6378F2)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = d.dp(-6), y = d.dp(-2)),
        )
    }
}

// ───────────────────────── 오른쪽 위: 근처 쉼터 찾기 ─────────────────────────

@Composable
fun RestStopFindCard(info: RestStopInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val sub = when {
        info == null -> stringResource(R.string.rest_find_sub)
        info.isWaypointAdded -> stringResource(R.string.rest_find_added, info.name)
        else -> stringResource(R.string.rest_find_nearest, info.name, info.distanceKm)
    }
    Box(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(R.drawable.img_rest_stop),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = d.dp(58))
                .size(d.dp(138), d.dp(88)),
            contentScale = ContentScale.Fit,
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = d.dp(34), end = d.dp(22)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KGradientIcon(
                R.drawable.ic_k_location,
                d.dp(58),
                Brush.verticalGradient(listOf(Color(0xFF8CA2FF), Color(0xFF5B46E6))),
            )
            Spacer(Modifier.width(d.dp(22)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(6))) {
                Text(stringResource(R.string.rest_find_title), style = d.text(28, W.ExtraBold, KkaeuColors.CardText))
                Text(sub, style = d.text(18, W.Medium, KkaeuColors.CardTextSub), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ChevronCircle(d.dp(46))
        }
    }
}

// ───────────────────────── 가운데 왼쪽: 이번 주행 점수 로그 ─────────────────────────

/** 그래프에 그릴 최대 점 수 (시안은 8개) */
private const val CHART_POINTS = 8

/** 기록을 [CHART_POINTS]개로 고르게 줄인다 (처음과 마지막은 항상 포함) */
fun downsample(values: List<Int>, count: Int = CHART_POINTS): List<Int> {
    if (values.size <= count) return values
    return List(count) { i -> values[(i * (values.size - 1).toFloat() / (count - 1)).roundToInt()] }
}

@Composable
fun ScoreLogCard(history: List<Int>, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Column(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .padding(start = d.dp(34), end = d.dp(30), top = d.dp(20), bottom = d.dp(14)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KGradientIcon(R.drawable.ic_k_bar_chart, d.dp(36), Brush.verticalGradient(listOf(Color(0xFF5E52C9), Color(0xFF2E2A86))))
            Spacer(Modifier.width(d.dp(14)))
            Text(stringResource(R.string.score_log_card_title), style = d.text(24, W.ExtraBold, KkaeuColors.CardText))
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onMore)
                    .padding(horizontal = d.dp(8), vertical = d.dp(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.score_log_more), style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub))
                KIcon(R.drawable.ic_k_chevron_right, d.dp(22), KkaeuColors.CardText)
            }
        }
        val points = downsample(history)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
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
            Text(stringResource(R.string.score_log_now), style = axis)
        }
    }
}

/** 보라 꺾은선 + 아래 그라데이션 + 점선 + 최고점 말풍선 (홈 점수 로그·주행 리포트 점수 흐름) */
@Composable
internal fun ScoreChart(points: List<Int>, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val maxIndex = points.indices.maxBy { points[it] }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val padX = with(density) { d.dp(22).toPx() }
        val top = with(density) { d.dp(38).toPx() }     // 말풍선 자리
        val bottom = hPx - with(density) { d.dp(10).toPx() }
        val lo = (points.min() - 6).coerceAtLeast(0)
        val hi = (points.max() + 2).coerceAtMost(100).coerceAtLeast(lo + 12)
        fun x(i: Int) = padX + (wPx - padX * 2) * i / (points.size - 1)
        fun y(v: Int) = bottom - (bottom - top) * (v - lo) / (hi - lo).toFloat()

        Canvas(Modifier.fillMaxSize()) {
            val line = Path()
            val area = Path()
            points.forEachIndexed { i, v ->
                val p = Offset(x(i), y(v))
                if (i == 0) {
                    line.moveTo(p.x, p.y)
                    area.moveTo(p.x, bottom)
                    area.lineTo(p.x, p.y)
                } else {
                    // 부드러운 곡선: 이전 점과 지금 점 사이 가운데를 제어점으로
                    val prev = Offset(x(i - 1), y(points[i - 1]))
                    val midX = (prev.x + p.x) / 2f
                    line.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                    area.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                }
            }
            area.lineTo(x(points.lastIndex), bottom)
            area.close()
            drawPath(area, Brush.verticalGradient(listOf(Color(0x668B5CF6), Color(0x058B5CF6)), top, bottom))
            // 점에서 바닥까지 점선
            val dash = PathEffect.dashPathEffect(floatArrayOf(d.dp(5f).toPx(), d.dp(4f).toPx()))
            points.forEachIndexed { i, v ->
                drawLine(Color(0x598B5CF6), Offset(x(i), y(v)), Offset(x(i), bottom), strokeWidth = d.dp(1.6f).toPx(), pathEffect = dash)
            }
            drawPath(line, KkaeuColors.Accent, style = Stroke(d.dp(3.6f).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, v ->
                val c = Offset(x(i), y(v))
                val r = d.dp(if (i == maxIndex) 8.5f else 7f).toPx()
                drawCircle(Color.White, r, c)
                drawCircle(KkaeuColors.Accent, r, c, style = Stroke(d.dp(3.2f).toPx()))
            }
        }

        // 최고점 말풍선 "96점"
        val bx = with(density) { x(maxIndex).toDp() }
        val by = with(density) { y(points[maxIndex]).toDp() }
        Box(
            Modifier
                .offset(x = bx - d.dp(40), y = by - d.dp(48))
                .size(d.dp(80), d.dp(36)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.score_log_point, points[maxIndex]),
                style = d.text(18, W.Bold, Color.White),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFA77BFF), KkaeuColors.Accent)))
                    .padding(horizontal = d.dp(14), vertical = d.dp(3)),
            )
        }
    }
}

// ───────────────────────── 가운데 오른쪽: 누적 포인트 ─────────────────────────

@Composable
fun PointsCard(totalPoints: Int, onPreviewBenefits: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Row(
        modifier = modifier
            .lavenderCard(d.dp(CARD_RADIUS))
            .padding(start = d.dp(26), end = d.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.img_gift),
            contentDescription = null,
            modifier = Modifier.size(d.dp(100)),
        )
        Spacer(Modifier.width(d.dp(18)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(2))) {
            Text(stringResource(R.string.points_card_title), style = d.text(24, W.Bold, KkaeuColors.CardText))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.points_card_value, totalPoints))
                    withStyle(SpanStyle(fontSize = d.sp(28), fontWeight = W.Bold)) {
                        append(" ")
                        append(stringResource(R.string.points_unit))
                    }
                },
                style = d.text(48, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.1f),
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(16))) {
            Row(
                modifier = Modifier
                    .shadow(d.dp(6), RoundedCornerShape(50), ambientColor = KkaeuColors.AccentDeep, spotColor = KkaeuColors.AccentDeep)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF2EDFF), KkaeuColors.AccentSoft)))
                    .clickable(onClick = onPreviewBenefits)
                    .height(d.dp(56))
                    .padding(start = d.dp(30), end = d.dp(20)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.dp(8)),
            ) {
                Text(stringResource(R.string.benefits_preview), style = d.text(20, W.Bold, KkaeuColors.AccentDeep))
                KIcon(R.drawable.ic_k_chevron_right, d.dp(24), KkaeuColors.AccentDeep)
            }
            Text(stringResource(R.string.benefits_caption), style = d.text(18, W.Medium, KkaeuColors.CardTextSub), maxLines = 1)
        }
        Image(
            painter = painterResource(R.drawable.img_coins),
            contentDescription = null,
            modifier = Modifier.size(d.dp(122)),
        )
    }
}

// ───────────────────────── 아래: 현재 주행 로그 ─────────────────────────

/**
 * 이번 주행 기록: "현재 주행 로그 | 주행 시간 | 주행 거리 | 현재 등급 | 이번 주행 포인트".
 * 시간·거리·포인트는 모두 이번 주행 값이라 다음 출발 때 함께 0이 된다 (정차 중에는 방금 마친 주행 값).
 * 연속 주행이 2시간을 넘으면([continuousDriveMinutes]) "쉬어 가세요"로 알려 준다.
 */
@Composable
fun CurrentTripCard(
    driveMinutes: Int,
    continuousDriveMinutes: Int,
    distanceKm: Float,
    level: DrowsyLevel,
    tripPoints: Int,
    isParked: Boolean,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    val longDrive = continuousDriveMinutes >= DrowsyThresholds.LONG_DRIVE_MIN
    Row(
        modifier = modifier.lavenderCard(d.dp(CARD_RADIUS)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = d.dp(34)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(14)),
        ) {
            KIcon(R.drawable.ic_k_clock_outline, d.dp(44), Color(0xFF2E3170))
            Column(verticalArrangement = Arrangement.spacedBy(d.dp(2))) {
                Text(stringResource(R.string.trip_card_title), style = d.text(26, W.ExtraBold, KkaeuColors.CardText), maxLines = 1)
                Text(
                    stringResource(if (isParked) R.string.header_parked else R.string.trip_card_driving),
                    style = d.text(18, W.SemiBold, KkaeuColors.CardTextSub),
                )
            }
        }
        StatDivider()
        TripStatCell(
            icon = { KIcon(R.drawable.ic_k_car, d.dp(40), Color(0xFF2E3170)) },
            value = AnnotatedString(durationText(driveMinutes)),
            label = stringResource(if (longDrive) R.string.trip_card_time_long else R.string.trip_card_time),
            labelColor = if (longDrive) LONG_DRIVE_TEXT else KkaeuColors.CardTextSub,
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        TripStatCell(
            icon = { KIcon(R.drawable.ic_k_road, d.dp(40), Color(0xFF2E3170)) },
            value = buildAnnotatedString {
                append(stringResource(R.string.trip_card_km_value, distanceKm))
                withStyle(SpanStyle(fontSize = d.sp(21), fontWeight = W.SemiBold)) {
                    append(" ")
                    append(stringResource(R.string.stats_km_unit))
                }
            },
            label = stringResource(R.string.trip_card_distance),
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        TripStatCell(
            icon = { LevelIcon(level, Modifier.size(d.dp(38)), mark = if (level == DrowsyLevel.NORMAL) Color.White else KkaeuColors.OnLight) },
            value = buildAnnotatedString {
                append(stringResource(level.labelRes()))
                withStyle(SpanStyle(fontSize = d.sp(21), fontWeight = W.SemiBold, color = KkaeuColors.CardTextSub)) {
                    append(" ")
                    append(stringResource(R.string.trip_card_lv, level.lvNumber()))
                }
            },
            label = stringResource(R.string.trip_card_level),
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        TripStatCell(
            icon = { PointCoin(d.dp(38)) },
            value = buildAnnotatedString {
                append(stringResource(R.string.trip_card_points_value, tripPoints))
                withStyle(SpanStyle(fontSize = d.sp(21), fontWeight = W.SemiBold)) {
                    append(" ")
                    append(stringResource(R.string.points_unit))
                }
            },
            label = stringResource(R.string.trip_card_points),
            modifier = Modifier.weight(1f),
        )
    }
}

/** 연보라 카드 위에서 읽히는 진한 주황 (장시간 주행 알림) */
private val LONG_DRIVE_TEXT = Color(0xFFB4561C)

/** 노란 "P" 동전 (포인트 아이콘) */
@Composable
fun PointCoin(size: Dp, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFE27A), Color(0xFFF5B82E), Color(0xFFD9901A))))
            .border(d.dp(2), Color(0xFFFFF1B8), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.points_unit),
            style = TextStyle(fontSize = (size.value * 0.52f).sp, fontWeight = W.ExtraBold, color = Color(0xFF8A5200)),
        )
    }
}

@Composable
private fun TripStatCell(
    icon: @Composable () -> Unit,
    value: AnnotatedString,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = KkaeuColors.CardTextSub,
) {
    val d = LocalDesign.current
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.dp(6)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(14))) {
            icon()
            Text(value, style = d.text(31, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.1f), maxLines = 1)
        }
        Text(label, style = d.text(19, W.Medium, labelColor), maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    val d = LocalDesign.current
    Box(
        Modifier
            .width(d.dp(1.5f))
            .height(d.dp(72))
            .background(KkaeuColors.CardDivider)
    )
}
