package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.CardChip
import com.sleepreaper.kkaeurang.ui.components.CardIconColor
import com.sleepreaper.kkaeurang.ui.components.CardRule
import com.sleepreaper.kkaeurang.ui.components.KIcon
import com.sleepreaper.kkaeurang.ui.components.KpiCard
import com.sleepreaper.kkaeurang.ui.components.LOG_GAP
import com.sleepreaper.kkaeurang.ui.components.LOG_KPI_H
import com.sleepreaper.kkaeurang.ui.components.LOG_MAIN_H
import com.sleepreaper.kkaeurang.ui.components.LogPageTitle
import com.sleepreaper.kkaeurang.ui.components.PointCoin
import com.sleepreaper.kkaeurang.ui.components.SectionCard
import com.sleepreaper.kkaeurang.ui.components.valueWithUnit
import com.sleepreaper.kkaeurang.ui.model.BenefitKind
import com.sleepreaper.kkaeurang.ui.model.BenefitUse
import com.sleepreaper.kkaeurang.ui.model.BenefitsSummary
import com.sleepreaper.kkaeurang.ui.model.FuelBenefit
import com.sleepreaper.kkaeurang.ui.model.InsuranceBenefit
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 혜택 탭: 포인트와 그 포인트로 받는 혜택을 한눈에.
 *
 * [숫자 4장] 보유 포인트(실제 값) · 지금까지 받은 혜택 · 이번 달 적립 · 예상 보험료 할인
 * [가운데] 주유 할인 (포인트 결제 + 점수 구간별 리터당 할인) | 보험료 할인 (안전운전 점수 연동 특약)
 * [아래, 스크롤] 혜택 받은 내역 | 포인트 모으는 법
 *
 * 혜택 조건은 아직 정해지지 않아 커넥티드카 안전운전 보험 할인 같은 방식을 참고한 예시 값이다 (화면 맨 아래에 안내).
 */
@Composable
fun BenefitsTab(totalPoints: Int, benefits: BenefitsSummary, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val insuranceRate = insuranceDiscount(benefits.insurance)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX), top = d.dp(4), bottom = d.dp(36)),
        verticalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
    ) {
        LogPageTitle(
            icon = R.drawable.ic_k_gift,
            title = stringResource(R.string.benefits_title),
            subtitle = stringResource(R.string.benefits_subtitle),
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
                icon = R.drawable.ic_k_star,
                label = stringResource(R.string.kpi_points),
                value = valueWithUnit("%,d".format(totalPoints), stringResource(R.string.points_unit)),
                sub = stringResource(R.string.kpi_points_sub, benefits.fuel.wonPerPoint),
                modifier = kpi,
                iconContent = { PointCoin(d.dp(40)) },
            )
            KpiCard(
                icon = R.drawable.ic_k_gift,
                label = stringResource(R.string.kpi_received),
                value = valueWithUnit("%,d".format(benefits.receivedTotalWon), stringResource(R.string.unit_won)),
                sub = stringResource(R.string.kpi_received_sub, benefits.history.size),
                modifier = kpi,
            )
            KpiCard(
                icon = R.drawable.ic_k_calendar,
                label = stringResource(R.string.kpi_month),
                value = valueWithUnit("+%,d".format(benefits.monthEarnedPoints), stringResource(R.string.points_unit)),
                sub = stringResource(R.string.kpi_month_sub),
                modifier = kpi,
            )
            KpiCard(
                icon = R.drawable.ic_k_shield,
                label = stringResource(R.string.kpi_insurance),
                value = valueWithUnit(insuranceRate.toString(), stringResource(R.string.unit_percent)),
                sub = benefits.insurance.averageScore?.let { stringResource(R.string.kpi_insurance_sub, it) },
                modifier = kpi,
            )
        }

        // ── 가운데: 주유 할인 | 보험료 할인 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.dp(LOG_MAIN_H)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        ) {
            FuelCard(totalPoints, benefits.fuel, benefits.insurance.averageScore, Modifier.weight(1f).fillMaxSize())
            InsuranceCard(benefits.insurance, insuranceRate, Modifier.weight(1f).fillMaxSize())
        }

        // ── 아래: 혜택 받은 내역 | 포인트 모으는 법 ──
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.dp(330)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
        ) {
            SectionCard(
                title = stringResource(R.string.history_title),
                icon = R.drawable.ic_k_list,
                trailing = { CardChip(stringResource(R.string.history_amount, benefits.receivedTotalWon)) },
                modifier = Modifier
                    .weight(1.52f)
                    .fillMaxSize(),
            ) {
                Spacer(Modifier.height(d.dp(6)))
                benefits.history.forEachIndexed { i, use ->
                    if (i > 0) CardRule()
                    BenefitRow(use)
                }
            }
            SectionCard(
                title = stringResource(R.string.earn_title),
                icon = R.drawable.ic_k_star,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(d.dp(22))) {
                    listOf(R.string.earn_1, R.string.earn_2, R.string.earn_3).forEachIndexed { i, res ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(14))) {
                            Box(
                                Modifier
                                    .size(d.dp(36))
                                    .clip(CircleShape)
                                    .background(AccentBrush),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text((i + 1).toString(), style = d.text(19, W.ExtraBold, Color.White))
                            }
                            Text(stringResource(res), style = d.text(20, W.SemiBold, KkaeuColors.CardText))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Text(
            stringResource(R.string.benefits_footnote),
            style = d.text(18, W.Medium, KkaeuColors.TextMuted),
        )
    }
}

/** 평균 안전 점수로 받는 보험료 할인율 (%). 조건 점수에 못 미치면 0 */
private fun insuranceDiscount(ins: InsuranceBenefit): Int {
    val score = ins.averageScore ?: return 0
    return ins.tiers.filter { score >= it.minScore }.maxOfOrNull { it.discountPercent } ?: 0
}

// ───────────────────────── 주유 할인 ─────────────────────────

@Composable
private fun FuelCard(totalPoints: Int, fuel: FuelBenefit, averageScore: Int?, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val myTier = averageScore?.let { s -> fuel.tiers.filter { s >= it.minScore }.maxByOrNull { it.minScore } }
    SectionCard(
        title = stringResource(R.string.fuel_title),
        icon = R.drawable.ic_k_fuel,
        trailing = { Text(fuel.partnerText, style = d.text(18, W.SemiBold, KkaeuColors.CardTextSub), maxLines = 1) },
        modifier = modifier,
    ) {
        Spacer(Modifier.height(d.dp(12)))
        Text(stringResource(R.string.fuel_now), style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub))
        Text(
            valueWithUnit("%,d".format(totalPoints * fuel.wonPerPoint), stringResource(R.string.unit_won), unitPx = 26),
            style = d.text(50, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.15f),
        )
        Text(stringResource(R.string.fuel_rule, fuel.wonPerPoint), style = d.text(17, W.Medium, KkaeuColors.CardTextSub))
        Spacer(Modifier.height(d.dp(16)))
        Text(stringResource(R.string.fuel_tier_title), style = d.text(19, W.Bold, KkaeuColors.CardText))
        Spacer(Modifier.height(d.dp(8)))
        Column(verticalArrangement = Arrangement.spacedBy(d.dp(8))) {
            fuel.tiers.forEach { tier ->
                val mine = tier == myTier
                val shape = RoundedCornerShape(d.dp(16))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.dp(46))
                        .clip(shape)
                        .background(if (mine) Color(0xE6FFFFFF) else Color(0x66FFFFFF))
                        .then(if (mine) Modifier.border(d.dp(2), KkaeuColors.Accent, shape) else Modifier)
                        .padding(horizontal = d.dp(18)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.fuel_tier_row, tier.minScore), style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub))
                    if (mine) {
                        Spacer(Modifier.width(d.dp(10)))
                        CardChip(stringResource(R.string.fuel_my_tier), strong = true)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.fuel_tier_value, tier.wonPerLiter), style = d.text(21, W.ExtraBold, KkaeuColors.CardText))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(10))) {
            KIcon(R.drawable.ic_k_fuel, d.dp(24), CardIconColor)
            Text(stringResource(R.string.fuel_saved, fuel.savedWon), style = d.text(19, W.Bold, KkaeuColors.CardText))
        }
    }
}

// ───────────────────────── 보험료 할인 ─────────────────────────

@Composable
private fun InsuranceCard(ins: InsuranceBenefit, rate: Int, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val score = ins.averageScore
    val next = score?.let { s -> ins.tiers.filter { it.minScore > s }.minByOrNull { it.minScore } }
    SectionCard(
        title = stringResource(R.string.ins_title),
        icon = R.drawable.ic_k_shield,
        trailing = { CardChip(stringResource(R.string.ins_program, ins.programName)) },
        modifier = modifier,
    ) {
        Spacer(Modifier.height(d.dp(12)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ins_my_score), style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub))
                Text(
                    valueWithUnit(score?.toString() ?: stringResource(R.string.stats_no_value), stringResource(R.string.unit_point), unitPx = 26),
                    style = d.text(50, W.ExtraBold, KkaeuColors.CardText, lineHeight = 1.15f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.ins_expected), style = d.text(19, W.SemiBold, KkaeuColors.CardTextSub))
                Text(
                    valueWithUnit(rate.toString(), stringResource(R.string.unit_percent), unitPx = 26),
                    style = d.text(50, W.ExtraBold, KkaeuColors.AccentDeep, lineHeight = 1.15f),
                )
            }
        }
        Spacer(Modifier.height(d.dp(14)))
        ScoreTierBar(ins, Modifier.fillMaxWidth().height(d.dp(86)))
        Spacer(Modifier.height(d.dp(14)))
        Text(
            stringResource(R.string.ins_expected_won, ins.annualPremiumWon, ins.annualPremiumWon / 100 * rate),
            style = d.text(21, W.Bold, KkaeuColors.CardText),
        )
        Spacer(Modifier.height(d.dp(4)))
        Text(
            if (next != null) stringResource(R.string.ins_next, next.minScore, next.discountPercent) else stringResource(R.string.ins_top),
            style = d.text(18, W.SemiBold, KkaeuColors.AccentDeep),
        )
        Spacer(Modifier.weight(1f))
        InsuranceSteps()
        Spacer(Modifier.height(d.dp(14)))
        Text(stringResource(R.string.ins_condition, ins.minDistanceKm), style = d.text(16, W.Medium, KkaeuColors.CardTextSub))
    }
}

/** 할인이 적용되는 순서: 안전 점수 공유 동의 → 보험 가입·갱신 → 할인 자동 적용 (예시) */
@Composable
private fun InsuranceSteps() {
    val d = LocalDesign.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.dp(18)))
            .background(Color(0x66FFFFFF))
            .padding(horizontal = d.dp(18), vertical = d.dp(12)),
        verticalArrangement = Arrangement.spacedBy(d.dp(8)),
    ) {
        Text(stringResource(R.string.ins_steps_title), style = d.text(17, W.Bold, KkaeuColors.CardTextSub))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(R.string.ins_step_1, R.string.ins_step_2, R.string.ins_step_3).forEachIndexed { i, res ->
                if (i > 0) {
                    KIcon(R.drawable.ic_k_chevron_right, d.dp(22), KkaeuColors.CardTextSub, Modifier.padding(horizontal = d.dp(6)))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.dp(8))) {
                    Box(
                        Modifier
                            .size(d.dp(26))
                            .clip(CircleShape)
                            .background(AccentBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((i + 1).toString(), style = d.text(15, W.ExtraBold, Color.White))
                    }
                    Text(stringResource(res), style = d.text(18, W.Bold, KkaeuColors.CardText), maxLines = 1)
                }
            }
        }
    }
}

/** 60~100점 막대 위에 할인 구간(70·80·90점) 눈금과 내 점수 표시 */
@Composable
private fun ScoreTierBar(ins: InsuranceBenefit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    val lo = 60f
    val hi = 100f
    val score = ins.averageScore
    val tierLabels = ins.tiers.map { stringResource(R.string.ins_tier, it.minScore, it.discountPercent) }
    BoxWithConstraints(modifier) {
        val w = maxWidth
        fun xOf(v: Float) = w * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        val barTop = d.dp(16)
        val barH = d.dp(16)
        Canvas(Modifier.fillMaxWidth().height(barTop + barH)) {
            val y = barTop.toPx()
            val h = barH.toPx()
            drawRoundRect(Color(0x80FFFFFF), topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2f))
            if (score != null) {
                val fx = size.width * ((score - lo) / (hi - lo)).coerceIn(0f, 1f)
                drawRoundRect(AccentBrush, topLeft = Offset(0f, y), size = Size(fx, h), cornerRadius = CornerRadius(h / 2f))
            }
            ins.tiers.forEach { t ->
                val tx = size.width * ((t.minScore - lo) / (hi - lo))
                drawLine(Color.White, Offset(tx, y - 4f), Offset(tx, y + h + 4f), strokeWidth = 3f)
            }
        }
        // 내 점수 표시 (막대 위 동그라미)
        if (score != null) {
            Box(
                Modifier
                    .offset(x = xOf(score.toFloat()) - d.dp(14), y = barTop - d.dp(6))
                    .size(d.dp(28))
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(d.dp(5), KkaeuColors.AccentDeep, CircleShape)
            )
        }
        // 구간 이름 (눈금 아래)
        ins.tiers.forEachIndexed { i, t ->
            Text(
                tierLabels[i],
                style = d.text(17, W.Bold, if (score != null && score >= t.minScore) KkaeuColors.AccentDeep else KkaeuColors.CardTextSub),
                maxLines = 1,
                modifier = Modifier.offset(x = xOf(t.minScore.toFloat()) - d.dp(44), y = barTop + barH + d.dp(10)),
            )
        }
    }
}

// ───────────────────────── 혜택 받은 내역 ─────────────────────────

@Composable
private fun BenefitRow(use: BenefitUse) {
    val d = LocalDesign.current
    val icon = when (use.kind) {
        BenefitKind.FUEL -> R.drawable.ic_k_fuel
        BenefitKind.INSURANCE -> R.drawable.ic_k_shield
        BenefitKind.REST_STOP -> R.drawable.ic_k_location
        BenefitKind.CAFE -> R.drawable.ic_k_cafe
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(d.dp(48)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            use.date.format(DateTimeFormatter.ofPattern(stringResource(R.string.history_date), Locale.KOREA)),
            style = d.text(18, W.SemiBold, KkaeuColors.CardTextSub),
            modifier = Modifier.width(d.dp(110)),
        )
        Box(
            Modifier
                .size(d.dp(36))
                .clip(CircleShape)
                .background(Color(0x99FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            KIcon(icon, d.dp(22), CardIconColor)
        }
        Spacer(Modifier.width(d.dp(14)))
        Text(use.title, style = d.text(20, W.Bold, KkaeuColors.CardText), maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.history_amount, use.amountWon),
            style = d.text(21, W.ExtraBold, KkaeuColors.AccentDeep),
            maxLines = 1,
        )
    }
}
