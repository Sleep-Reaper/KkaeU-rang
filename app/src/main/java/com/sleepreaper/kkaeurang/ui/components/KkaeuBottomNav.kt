package com.sleepreaper.kkaeurang.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.theme.AccentBrush
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/** 하단 탭 */
enum class KkaeuTab(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    HOME(R.drawable.ic_k_home, R.string.tab_home),
    DRIVE_LOG(R.drawable.ic_k_bar_chart, R.string.tab_drive_log),
    BENEFITS(R.drawable.ic_k_gift, R.string.tab_benefits),
    MY(R.drawable.ic_k_person, R.string.tab_my),
    SETTINGS(R.drawable.ic_k_settings, R.string.tab_settings),
}

/** 경보 중(주의 이상)에는 홈·설정만 보인다 (시안) */
val AlertTabs = listOf(KkaeuTab.HOME, KkaeuTab.SETTINGS)

/** 하단 탭 영역 높이 + 아래 여백 (시안 px) */
const val NAV_HEIGHT_PX = 120
const val NAV_BOTTOM_GAP_PX = 39

/**
 * 하단 탭 (시안: 둥근 반투명 막대 안에 아이콘 + 글자, 선택된 탭은 보라 아이콘 + 아래 빛나는 막대).
 */
@Composable
fun KkaeuBottomNav(
    tabs: List<KkaeuTab>,
    selected: KkaeuTab,
    onSelect: (KkaeuTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDesign.current
    val shape = RoundedCornerShape(d.dp(60))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(d.dp(NAV_HEIGHT_PX))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x08FFFFFF))))
            .border(d.dp(1.5f), Color(0x33C9CCFF), shape),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            NavItem(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun NavItem(tab: KkaeuTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Box(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.dp(6)),
            modifier = Modifier.offset(y = d.dp(-4)),
        ) {
            if (selected) {
                KGradientIcon(tab.icon, d.dp(42), Brush.verticalGradient(listOf(Color(0xFFC6A8FF), KkaeuColors.Accent)))
            } else {
                KIcon(tab.icon, d.dp(42), Color(0xFFDCD9FF))
            }
            Text(
                text = stringResource(tab.label),
                style = d.text(22, if (selected) W.ExtraBold else W.SemiBold, if (selected) KkaeuColors.TextPrimary else Color(0xFFDCD9FF)),
            )
        }
        if (selected) {
            // 아래 빛나는 막대
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = d.dp(3))
                    .size(d.dp(136), d.dp(9))
                    .drawBehind {
                        drawRoundRect(
                            KkaeuColors.Accent.copy(alpha = 0.35f),
                            topLeft = Offset(-size.height, -size.height * 0.8f),
                            size = Size(size.width + size.height * 2, size.height * 2.6f),
                            cornerRadius = CornerRadius(size.height * 1.3f),
                        )
                    }
                    .clip(RoundedCornerShape(50))
                    .background(AccentBrush)
            )
        }
    }
}
