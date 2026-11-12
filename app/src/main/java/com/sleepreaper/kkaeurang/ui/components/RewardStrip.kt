package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.AccrualState
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType

/**
 * 이번 주행 적립 카드 (사이드 패널). HIDDEN(WARNING 이상)이면 아예 그리지 않는다.
 * 깨어 있던 시간이 전체 주행 시간보다 짧으면 전체 주행 시간도 함께 보여 준다.
 */
@Composable
fun RewardStrip(
    accrualState: AccrualState,
    awakeMinutes: Int,
    tripDriveMinutes: Int,
    tripPoints: Int,
    modifier: Modifier = Modifier,
) {
    if (accrualState == AccrualState.HIDDEN) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .blockSurface()
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.reward_title),
                style = KkaeuType.Caption,
                color = KkaeuColors.TextSecondary,
            )
            Text(
                text = stringResource(R.string.reward_trip_points, tripPoints),
                style = KkaeuType.Body.copy(fontWeight = FontWeight.Bold),
                color = KkaeuColors.Points,
                modifier = Modifier.weight(1f),
            )
            AccrualLabel(accrualState)
        }
        Text(text = stringResource(R.string.reward_awake, durationText(awakeMinutes)), style = KkaeuType.Body)
        if (awakeMinutes < tripDriveMinutes) {
            Text(
                text = stringResource(R.string.reward_drive_total, durationText(tripDriveMinutes)),
                style = KkaeuType.Caption,
                color = KkaeuColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AccrualLabel(accrualState: AccrualState) {
    val (textRes, color, weight) = when (accrualState) {
        AccrualState.ACCRUING -> Triple(R.string.accrual_accruing, KkaeuColors.LevelNormal.copy(alpha = 0.75f), FontWeight.Medium)
        AccrualState.PAUSED -> Triple(R.string.accrual_paused, KkaeuColors.TextSecondary, FontWeight.Medium)
        AccrualState.RESUMED -> Triple(R.string.accrual_resumed, KkaeuColors.LevelNormal, FontWeight.Bold)
        AccrualState.HIDDEN -> return
    }
    Text(
        text = stringResource(textRes),
        style = KkaeuType.Body.copy(fontWeight = weight),
        color = color,
    )
}
