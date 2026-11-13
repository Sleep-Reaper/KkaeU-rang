package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sleepreaper.kkaeurang.ui.components.AlertTabs
import com.sleepreaper.kkaeurang.ui.components.VehicleEventBanner
import com.sleepreaper.kkaeurang.ui.components.KkaeuBottomNav
import com.sleepreaper.kkaeurang.ui.components.KkaeuHeader
import com.sleepreaper.kkaeurang.ui.components.KkaeuTab
import com.sleepreaper.kkaeurang.ui.components.NAV_BOTTOM_GAP_PX
import com.sleepreaper.kkaeurang.ui.components.NightSkyBackground
import com.sleepreaper.kkaeurang.ui.debug.DebugPanel
import com.sleepreaper.kkaeurang.ui.debug.KkaeuDebugControls
import com.sleepreaper.kkaeurang.ui.debug.LiveDebugControls
import com.sleepreaper.kkaeurang.ui.debug.LiveDebugPanel
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.state.KkaeuActions
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.ProvideDesignScale

enum class KkaeuScreen { MAIN, TRIP_REPORT }

/**
 * 어떤 화면을 보여 줄지는 기어·리포트 유무로만 정한다.
 * 주행 중에는 헤더 + 본문 + 하단 탭 틀 하나이고, 단계가 바뀌면 본문만 즉시 바뀐다.
 */
fun screenFor(state: DrowsinessUiState): KkaeuScreen =
    if (state.isParked && state.tripReport != null) KkaeuScreen.TRIP_REPORT else KkaeuScreen.MAIN

/** 주의 이상이면 홈 본문 대신 경보 화면 (탭도 홈·설정만) */
fun isAlert(state: DrowsinessUiState): Boolean = state.level >= DrowsyLevel.CAUTION

/**
 * 깨우랑 UI 진입점. [state] 하나만 보고 그리고, 사용자 동작은 [actions]로만 내보낸다.
 *
 * 화면 틀 (디자인 시안 2026-11):
 * - 배경: 밤하늘 (경보 중에는 조금 어둡게)
 * - 위: 헤더 (주행 시간 · 로고 · 음성 상태 · 경고음)
 * - 가운데: 탭 본문. 홈 탭에서 주의 이상이면 경보 화면
 * - 아래: 하단 탭 (안전: 홈·주행 로그·혜택·마이·설정 / 경보 중: 홈·설정)
 *
 * @param debugControls 가짜 데이터용 디버그 패널. null이면 열리지 않는다
 * @param liveDebugControls 실제 모드(판단 엔진 연결)용 디버그 패널. 둘 다 null이면 길게 눌러도 아무 일 없음 (배포 시)
 */
@Composable
fun KkaeuApp(
    state: DrowsinessUiState,
    actions: KkaeuActions,
    debugControls: KkaeuDebugControls?,
    modifier: Modifier = Modifier,
    liveDebugControls: LiveDebugControls? = null,
) {
    var debugOpen by rememberSaveable { mutableStateOf(false) }
    val openDebug: (() -> Unit)? =
        if (debugControls != null || liveDebugControls != null) ({ debugOpen = true }) else null
    var tab by rememberSaveable { mutableStateOf(KkaeuTab.HOME) }
    val alert = isAlert(state)

    // 경보가 시작되면 홈으로 돌아온다 (경보 중에도 설정 탭은 열 수 있다)
    LaunchedEffect(alert) {
        if (alert) tab = KkaeuTab.HOME
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = KkaeuColors.Background,
        contentColor = KkaeuColors.TextPrimary,
    ) {
        NightSkyBackground(alert = alert && tab == KkaeuTab.HOME && screenFor(state) == KkaeuScreen.MAIN)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ProvideDesignScale {
                when (screenFor(state)) {
                    KkaeuScreen.MAIN -> MainFrame(
                        state = state,
                        actions = actions,
                        tab = if (alert && tab !in AlertTabs) KkaeuTab.HOME else tab,
                        onSelectTab = { tab = it },
                        openDebug = openDebug,
                    )
                    KkaeuScreen.TRIP_REPORT -> TripReportScreen(
                        report = requireNotNull(state.tripReport),
                        totalPoints = state.totalPoints,
                        onClose = actions::onCloseTripReport,
                        onLogoLongPress = openDebug,
                    )
                }
            }

            if (debugOpen && debugControls != null) {
                DebugPanel(
                    controls = debugControls,
                    state = state,
                    onClose = { debugOpen = false },
                )
            } else if (debugOpen && liveDebugControls != null) {
                LiveDebugPanel(
                    controls = liveDebugControls,
                    onClose = { debugOpen = false },
                )
            }
        }
    }
}

@Composable
private fun MainFrame(
    state: DrowsinessUiState,
    actions: KkaeuActions,
    tab: KkaeuTab,
    onSelectTab: (KkaeuTab) -> Unit,
    openDebug: (() -> Unit)?,
) {
    val d = LocalDesign.current
    val alert = isAlert(state)
    Column(Modifier.fillMaxSize()) {
        KkaeuHeader(
            state = state,
            onToggleWarningSound = actions::onToggleWarningSound,
            onLogoLongPress = openDebug,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                KkaeuTab.HOME ->
                    if (alert) {
                        AlertScreen(
                            state = state,
                            onAnswerOkay = actions::onAnswerOkay,
                            onVoiceAnswer = actions::onVoiceAnswer,
                            onCancelVehicleControl = actions::onCancelVehicleControl,
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            onFindRestStop = actions::onFindRestStop,
                            onOpenTab = onSelectTab,
                            onGaugeLongPress = openDebug,
                        )
                    }
                KkaeuTab.SETTINGS -> SettingsTab(state.warningSoundOn, actions::onToggleWarningSound)
                else -> ComingSoonTab(tab)
            }
            // 창문·찬바람 안내 ("창문을 조금 열었어요", "원래대로 닫았어요"): 어느 탭에서든 본문 맨 아래에 잠깐.
            // 홈에서는 "나의 주행 로그" 카드 글씨를 가리지 않도록 카드와 하단 탭 사이 빈 곳에 걸치게 둔다
            VehicleEventBanner(
                event = state.vehicleEvent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        KkaeuBottomNav(
            tabs = if (alert) AlertTabs else KkaeuTab.entries,
            selected = tab,
            onSelect = onSelectTab,
            modifier = Modifier.padding(start = d.dp(52), end = d.dp(52)),
        )
        Spacer(Modifier.height(d.dp(NAV_BOTTOM_GAP_PX)))
    }
}
