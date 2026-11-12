package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sleepreaper.kkaeurang.ui.debug.DebugPanel
import com.sleepreaper.kkaeurang.ui.debug.KkaeuDebugControls
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors

enum class KkaeuScreen { MAIN, TRIP_REPORT }

/**
 * 어떤 화면을 보여 줄지는 기어·리포트 유무로만 정한다.
 * 주행 중에는 단계와 상관없이 계기판 화면 하나이고, 단계가 바뀌면 그 안의 블록만 즉시 바뀐다.
 */
fun screenFor(state: DrowsinessUiState): KkaeuScreen =
    if (state.isParked && state.tripReport != null) KkaeuScreen.TRIP_REPORT else KkaeuScreen.MAIN

/**
 * 깨우랑 UI 진입점. [state] 하나만 보고 그리고, 사용자 동작은 [actions]로만 내보낸다.
 * 화면 전환은 애니메이션 없이 즉시.
 *
 * @param debugControls null이면 디버그 패널이 열리지 않는다 (실제 모듈 연동·배포 시)
 */
@Composable
fun KkaeuApp(
    state: DrowsinessUiState,
    actions: KkaeuActions,
    debugControls: KkaeuDebugControls?,
    modifier: Modifier = Modifier,
) {
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    val openDebug: (() -> Unit)? = debugControls?.let { { debugOpen = true } }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = KkaeuColors.Background,
        contentColor = KkaeuColors.TextPrimary,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when (screenFor(state)) {
                KkaeuScreen.MAIN -> MainDrivingScreen(state, actions, openDebug)
                KkaeuScreen.TRIP_REPORT -> TripReportScreen(
                    report = requireNotNull(state.tripReport),
                    totalPoints = state.totalPoints,
                    onClose = actions::onCloseTripReport,
                    onLogoLongPress = openDebug,
                )
            }

            if (debugOpen && debugControls != null) {
                DebugPanel(
                    controls = debugControls,
                    state = state,
                    onClose = { debugOpen = false },
                )
            }
        }
    }
}
