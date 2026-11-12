package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.ui.components.CenterGauge
import com.sleepreaper.kkaeurang.ui.components.ClimateCorner
import com.sleepreaper.kkaeurang.ui.components.DriveTimeCorner
import com.sleepreaper.kkaeurang.ui.components.RestStopCorner
import com.sleepreaper.kkaeurang.ui.components.VoiceCorner
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions

/** 네 모서리 기능의 화면 가장자리 여백 */
private val CornerEdge = 48.dp

/**
 * 메인 주행 화면 (모든 단계 공통). 디자인 시안: design/faux-ui
 *
 * 가운데: 계기판 (원의 중심이 화면 정중앙, 화면에서 가장 크게) + 위 단계 / 아래 마지막 점수 로그·누적 포인트
 * 왼쪽 위: 연속 주행 시간, 위험 시간대 배지
 * 오른쪽 위: 음성 상태, 경고음 스위치 (질문·경고 카드가 그 아래에)
 * 왼쪽 아래: 창문·외기 유입 상태, 날씨 (차량 제어 알림이 그 위에)
 * 오른쪽 아래: 가장 가까운 쉼터, 경유지 추가
 *
 * 단계가 바뀌면 블록 내용만 즉시 바뀐다 (애니메이션 없음).
 * @param onLogoLongPress 계기판을 길게 누르면 호출 (디버그 패널). null이면 아무 일 없음
 */
@Composable
fun MainDrivingScreen(
    state: DrowsinessUiState,
    actions: KkaeuActions,
    onLogoLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CenterGauge(state = state, onLongPress = onLogoLongPress)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(CornerEdge),
        ) {
            DriveTimeCorner(
                continuousDriveMin = state.continuousDriveMin,
                isRiskTimeZone = state.isRiskTimeZone,
                modifier = Modifier.align(Alignment.TopStart),
            )
            VoiceCorner(
                state = state,
                onToggleWarningSound = actions::onToggleWarningSound,
                onAnswerOkay = actions::onAnswerOkay,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            ClimateCorner(
                windowOpen = state.windowOpen,
                outsideAirOn = state.outsideAirOn,
                weather = state.weather,
                vehicleEvent = state.vehicleEvent,
                onCancelVehicleControl = actions::onCancelVehicleControl,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            RestStopCorner(
                info = state.restStop,
                onAddWaypoint = actions::onAddRestStopWaypoint,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}
