package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.RestStopInfo
import com.sleepreaper.kkaeurang.ui.model.RestStopType
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType

/**
 * 쉼터 안내 카드 (사이드 패널용 세로 배치). 강요하지 않는 말투로 제안만 한다.
 * 경유지로 추가되면 "○○ 경유 중"으로 바뀌고 버튼은 사라진다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestStopCard(
    info: RestStopInfo,
    onAddWaypoint: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .blockSurface(borderColor = KkaeuColors.LevelNormal.copy(alpha = 0.6f))
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (info.isWaypointAdded) {
            Text(
                text = stringResource(R.string.rest_waypoint_added, info.name),
                style = KkaeuType.Title.copy(fontWeight = FontWeight.Bold),
                color = KkaeuColors.LevelNormal,
            )
        } else {
            Text(
                text = stringResource(R.string.rest_suggest),
                style = KkaeuType.Body,
                color = KkaeuColors.TextSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = info.name, style = KkaeuType.Title.copy(fontWeight = FontWeight.Bold))
                RestStopTypeTag(info.type)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(R.string.rest_distance, info.distanceKm), style = KkaeuType.Body)
            Text(
                text = stringResource(if (info.hasToilet) R.string.rest_toilet_yes else R.string.rest_toilet_no),
                style = KkaeuType.Body,
            )
            Text(
                text = info.parkingSpaces?.let { stringResource(R.string.rest_parking, it) }
                    ?: stringResource(R.string.rest_parking_unknown),
                style = KkaeuType.Body,
            )
        }
        if (!info.isWaypointAdded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MediumButton(
                    text = stringResource(R.string.rest_add_waypoint),
                    onClick = onAddWaypoint,
                    modifier = Modifier.weight(1.3f),
                )
                OutlineButton(
                    text = stringResource(R.string.btn_okay),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RestStopTypeTag(type: RestStopType) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = stringResource(
            when (type) {
                RestStopType.DROWSY_SHELTER -> R.string.rest_type_drowsy_shelter
                RestStopType.SERVICE_AREA -> R.string.rest_type_service_area
            }
        ),
        style = KkaeuType.Caption.copy(fontWeight = FontWeight.SemiBold),
        color = KkaeuColors.LevelNormal,
        modifier = Modifier
            .border(2.dp, KkaeuColors.LevelNormal, shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
