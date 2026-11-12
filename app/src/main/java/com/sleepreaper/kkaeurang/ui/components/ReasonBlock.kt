package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.DrowsyThresholds
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType

private const val MAX_REASON_LINES = 3

/**
 * 판단 근거.
 * - CAUTION 이상일 때만 근거 최대 3줄, NORMAL이면 "특이사항 없음"
 * - 연속 주행 시간은 항상 표시, 2시간 넘으면 강조
 * - 위험 시간대면 배지
 */
@Composable
fun ReasonBlock(
    level: DrowsyLevel,
    reasons: List<String>,
    continuousDriveMin: Int,
    isRiskTimeZone: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .blockSurface()
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.reason_title),
            style = KkaeuType.Caption,
            color = KkaeuColors.TextSecondary,
        )
        val shown = if (level == DrowsyLevel.NORMAL) emptyList() else reasons.take(MAX_REASON_LINES)
        if (shown.isEmpty()) {
            Text(text = stringResource(R.string.reason_none), style = KkaeuType.Body)
        } else {
            shown.forEach {
                Text(
                    text = stringResource(R.string.reason_bullet, it),
                    style = KkaeuType.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // 폭이 좁아도 줄바꿈되지 않도록 세로로 쌓는다
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            ContinuousDriveChip(continuousDriveMin)
            if (isRiskTimeZone) RiskTimeBadge()
        }
    }
}

@Composable
fun ContinuousDriveChip(continuousDriveMin: Int, modifier: Modifier = Modifier) {
    val long = continuousDriveMin >= DrowsyThresholds.LONG_DRIVE_MIN
    val clock = clockText(continuousDriveMin)
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = if (long) {
            stringResource(R.string.continuous_drive_long, clock)
        } else {
            stringResource(R.string.continuous_drive, clock)
        },
        style = KkaeuType.Body.copy(fontWeight = if (long) FontWeight.Bold else FontWeight.Medium),
        color = if (long) KkaeuColors.OnLight else KkaeuColors.TextPrimary,
        modifier = modifier
            .clip(shape)
            .background(if (long) KkaeuColors.LevelCaution else KkaeuColors.SurfaceHigh)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
fun RiskTimeBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = stringResource(R.string.risk_time_badge),
        style = KkaeuType.Body,
        color = KkaeuColors.RiskBadge,
        modifier = modifier
            .clip(shape)
            .border(3.dp, KkaeuColors.RiskBadge, shape)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}
