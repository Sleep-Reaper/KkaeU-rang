package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.ConditionCard
import com.sleepreaper.kkaeurang.ui.components.GAUGE_W
import com.sleepreaper.kkaeurang.ui.components.KkaeuTab
import com.sleepreaper.kkaeurang.ui.components.CurrentTripCard
import com.sleepreaper.kkaeurang.ui.components.PointsCard
import com.sleepreaper.kkaeurang.ui.components.RestStopFindCard
import com.sleepreaper.kkaeurang.ui.components.SafetyGauge
import com.sleepreaper.kkaeurang.ui.components.ScoreLogCard
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign

/** 화면 좌우 여백 (시안 px) */
const val EDGE_LEFT_PX = 52
const val EDGE_RIGHT_PX = 38

/**
 * 홈 (안전 단계). 디자인 시안: 계기판 화면.
 *
 * 위: 마스코트 · 안전 점수 계기판 · 현재 컨디션 / 근처 쉼터 찾기
 * 가운데: 이번 주행 점수 로그 · 누적 포인트
 * 아래: 현재 주행 로그 (주행 시간·거리·등급·포인트)
 * (헤더와 하단 탭은 [KkaeuApp]이 그린다)
 */
@Composable
fun HomeScreen(
    state: DrowsinessUiState,
    onFindRestStop: () -> Unit,
    onOpenTab: (KkaeuTab) -> Unit,
    onGaugeLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    Column(modifier.fillMaxSize()) {
        // ── 위: 마스코트 · 계기판 · 오른쪽 카드 두 장 (시안 y 150~545) ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(d.dp(395)),
        ) {
            Image(
                painter = painterResource(R.drawable.img_mascot),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = d.dp(44), y = d.dp(152))
                    .size(d.dp(290), d.dp(245)),
                contentScale = ContentScale.Fit,
            )
            SafetyGauge(
                safety = state.safetyScore,
                level = state.level,
                onLongPress = onGaugeLongPress,
                modifier = Modifier.offset(x = d.dp(572 - GAUGE_W / 2), y = d.dp(160 - 150)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = d.dp(37), end = d.dp(EDGE_RIGHT_PX - 5))
                    .width(d.dp(527)),
                verticalArrangement = Arrangement.spacedBy(d.dp(22)),
            ) {
                ConditionCard(
                    sleep = state.sleep,
                    sleepRange = state.sleepRange,
                    onClick = { onOpenTab(KkaeuTab.MY) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.dp(153)),
                )
                RestStopFindCard(
                    info = state.restStop,
                    onClick = onFindRestStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.dp(124)),
                )
            }
        }

        Spacer(Modifier.height(d.dp(8)))

        // ── 가운데: 점수 로그 · 누적 포인트 (시안 y 553~723) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(d.dp(170))
                .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(23)),
        ) {
            ScoreLogCard(
                history = state.safetyHistory,
                onMore = { onOpenTab(KkaeuTab.DRIVE_LOG) },
                modifier = Modifier
                    .weight(685f)
                    .fillMaxSize(),
            )
            PointsCard(
                totalPoints = state.totalPoints,
                onPreviewBenefits = { onOpenTab(KkaeuTab.BENEFITS) },
                modifier = Modifier
                    .weight(680f)
                    .fillMaxSize(),
            )
        }

        Spacer(Modifier.height(d.dp(22)))

        // ── 아래: 현재 주행 로그 (시안 y 745~863) ──
        CurrentTripCard(
            driveMinutes = state.tripDriveMinutes,
            continuousDriveMinutes = state.continuousDriveMin,
            distanceKm = state.tripDistanceKm,
            level = state.level,
            tripPoints = state.tripPoints,
            isParked = state.isParked,
            modifier = Modifier
                .fillMaxWidth()
                .height(d.dp(118))
                .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX)),
        )
    }
}
