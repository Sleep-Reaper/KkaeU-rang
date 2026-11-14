package com.sleepreaper.kkaeurang.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sleepreaper.kkaeurang.ui.components.NightSkyBackground
import com.sleepreaper.kkaeurang.ui.screens.MyTab
import com.sleepreaper.kkaeurang.ui.screens.SettingsTab
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.ui.theme.ProvideDesignScale

// 마이·설정 탭 본문만 미리보기 (헤더·하단 탭 제외)

@Composable
private fun TabPreview(content: @Composable () -> Unit) {
    KkaeUrangTheme {
        Box(Modifier.fillMaxSize()) {
            NightSkyBackground(alert = false)
            ProvideDesignScale { content() }
        }
    }
}

@LandscapePreview
@Composable
fun PreviewMyTab() = TabPreview { MyTab() }

@LandscapePreview
@Composable
fun PreviewSettingsTab() = TabPreview { SettingsTab(warningSoundOn = true, onToggleWarningSound = {}) }

@LandscapePreview
@Composable
fun PreviewSettingsTabSoundOff() = TabPreview { SettingsTab(warningSoundOn = false, onToggleWarningSound = {}) }