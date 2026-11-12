package com.sleepreaper.kkaeurang.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.KGradientIcon
import com.sleepreaper.kkaeurang.ui.components.KIcon
import com.sleepreaper.kkaeurang.ui.components.KkaeuSwitch
import com.sleepreaper.kkaeurang.ui.components.KkaeuTab
import com.sleepreaper.kkaeurang.ui.components.lavenderCard
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/**
 * 시안이 아직 없는 탭 (주행 로그·혜택·마이). 탭 이름과 "준비 중" 안내만 보여 준다.
 * TODO(design): 각 탭 시안이 나오면 화면을 만든다.
 */
@Composable
fun ComingSoonTab(tab: KkaeuTab, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KGradientIcon(tab.icon, d.dp(110), Brush.verticalGradient(listOf(Color(0xFFC6A8FF), KkaeuColors.Accent)))
        Spacer(Modifier.height(d.dp(28)))
        Text(stringResource(tab.label), style = d.text(46, W.ExtraBold, KkaeuColors.TextPrimary))
        Spacer(Modifier.height(d.dp(14)))
        Text(stringResource(R.string.tab_coming_soon), style = d.text(28, W.SemiBold, KkaeuColors.TextSecondary))
        Spacer(Modifier.height(d.dp(6)))
        Text(stringResource(R.string.tab_coming_soon_sub), style = d.text(22, W.Medium, KkaeuColors.TextMuted))
    }
}

/** 설정 탭: 지금은 경고음 켜고 끄기 하나 */
@Composable
fun SettingsTab(warningSoundOn: Boolean, onToggleWarningSound: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth()
            .width(d.dp(1000))
            .padding(vertical = d.dp(60)),
        verticalArrangement = Arrangement.spacedBy(d.dp(24)),
    ) {
        Text(stringResource(R.string.tab_settings), style = d.text(40, W.ExtraBold, KkaeuColors.TextPrimary))
        Row(
            modifier = Modifier
                .lavenderCard(d.dp(26))
                .toggleable(value = warningSoundOn, role = Role.Switch, onValueChange = { onToggleWarningSound() })
                .padding(horizontal = d.dp(36), vertical = d.dp(30)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KIcon(
                if (warningSoundOn) R.drawable.ic_ms_volume_up else R.drawable.ic_ms_volume_off,
                d.dp(44),
                KkaeuColors.CardText,
            )
            Spacer(Modifier.width(d.dp(24)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(4))) {
                Text(stringResource(R.string.main_warning_sound), style = d.text(30, W.ExtraBold, KkaeuColors.CardText))
                Text(stringResource(R.string.settings_warning_sound_sub), style = d.text(21, W.Medium, KkaeuColors.CardTextSub))
            }
            KkaeuSwitch(warningSoundOn)
        }
    }
}
