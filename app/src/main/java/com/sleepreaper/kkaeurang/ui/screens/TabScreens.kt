package com.sleepreaper.kkaeurang.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.CardRule
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

/**
 * 설정 탭.
 *
 * [알림] 경고음 크기 · 음성으로 질문하기
 * [졸음 대응] 쉼터 자동 경유지 추가
 * [앱 정보] 버전 · 쉼터 데이터 출처
 *
 * 경고음 켜고 끄기는 헤더 토글([warningSoundOn])에서 한다. 꺼져 있으면 크기 조절을 막는다.
 * TODO(voice): 경고음 크기는 아직 화면에서만 바뀐다. 음성 모듈에 음량 설정이 생기면 연결한다.
 * TODO(state): 음성 질문·쉼터 자동 추가 토글도 화면에서만 바뀐다 (탭을 나갔다 오면 처음 값으로).
 */
@Composable
fun SettingsTab(warningSoundOn: Boolean, onToggleWarningSound: () -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    var volume by remember { mutableFloatStateOf(0.7f) }
    var voiceAsk by remember { mutableStateOf(true) }
    var autoRestStop by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth()
            .width(d.dp(1000))
            .padding(vertical = d.dp(40)),
        verticalArrangement = Arrangement.spacedBy(d.dp(18)),
    ) {
        Text(stringResource(R.string.tab_settings), style = d.text(40, W.ExtraBold, KkaeuColors.TextPrimary))

        SettingsSection("알림")
        VolumeRow(
            volume = volume,
            enabled = warningSoundOn,
            onVolumeChange = { volume = it },
        )
        ToggleRow(
            icon = R.drawable.ic_ms_record_voice_over,
            title = "음성으로 질문하기",
            sub = "졸음이 의심되면 먼저 말로 상태를 물어봐요",
            on = voiceAsk,
            onToggle = { voiceAsk = !voiceAsk },
        )

        SettingsSection("졸음 대응")
        ToggleRow(
            icon = R.drawable.ic_k_location,
            title = "쉼터 자동 경유지 추가",
            sub = "위험 단계에서 가장 가까운 졸음쉼터를 내비 경유지로 넣어요",
            on = autoRestStop,
            onToggle = { autoRestStop = !autoRestStop },
        )

        SettingsSection("앱 정보")
        InfoCard(
            rows = listOf(
                "버전" to "1.0.0",
                "쉼터 데이터" to "공공데이터포털 전국졸음쉼터표준데이터",
            ),
        )
    }
}

/** 섹션 제목 (카드 위 작은 글씨) */
@Composable
private fun SettingsSection(title: String) {
    val d = LocalDesign.current
    Text(
        title,
        style = d.text(24, W.Bold, KkaeuColors.TextSecondary),
        modifier = Modifier.padding(start = d.dp(8), top = d.dp(14)),
    )
}

/** 경고음 크기: 아이콘 + 제목·슬라이더 + 퍼센트 */
@Composable
private fun VolumeRow(volume: Float, enabled: Boolean, onVolumeChange: (Float) -> Unit) {
    val d = LocalDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lavenderCard(d.dp(26))
            .padding(horizontal = d.dp(36), vertical = d.dp(26)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(
            if (enabled && volume > 0f) R.drawable.ic_ms_volume_up else R.drawable.ic_ms_volume_off,
            d.dp(44),
            KkaeuColors.CardText,
        )
        Spacer(Modifier.width(d.dp(24)))
        Column(Modifier.weight(1f)) {
            Text("경고음 크기", style = d.text(30, W.ExtraBold, KkaeuColors.CardText))
            Text(
                if (enabled) "졸음 경고 때 안내 문구와 신호음의 크기예요" else "경고음이 꺼져 있어요 (화면 위 경고음 토글)",
                style = d.text(21, W.Medium, KkaeuColors.CardTextSub),
            )
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = KkaeuColors.Accent,
                    activeTrackColor = KkaeuColors.Accent,
                    inactiveTrackColor = KkaeuColors.CardDivider,
                ),
            )
        }
        Spacer(Modifier.width(d.dp(24)))
        Text(
            if (enabled) "${(volume * 100).toInt()}%" else "-",
            style = d.text(30, W.ExtraBold, KkaeuColors.CardText),
            modifier = Modifier.width(d.dp(90)),
        )
    }
}

/** 켜고 끄는 줄: 아이콘 + 제목·설명 + 스위치 */
@Composable
private fun ToggleRow(@DrawableRes icon: Int, title: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    val d = LocalDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lavenderCard(d.dp(26))
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = d.dp(36), vertical = d.dp(30)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(icon, d.dp(44), KkaeuColors.CardText)
        Spacer(Modifier.width(d.dp(24)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(4))) {
            Text(title, style = d.text(30, W.ExtraBold, KkaeuColors.CardText))
            Text(sub, style = d.text(21, W.Medium, KkaeuColors.CardTextSub))
        }
        KkaeuSwitch(on)
    }
}

/** 이름 · 값 줄 목록 */
@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    val d = LocalDesign.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lavenderCard(d.dp(26))
            .padding(horizontal = d.dp(36), vertical = d.dp(8)),
    ) {
        rows.forEachIndexed { i, (name, value) ->
            if (i > 0) CardRule()
            Row(Modifier.padding(vertical = d.dp(22)), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = d.text(26, W.ExtraBold, KkaeuColors.CardText), modifier = Modifier.weight(1f))
                Text(value, style = d.text(22, W.SemiBold, KkaeuColors.CardTextSub))
            }
        }
    }
}