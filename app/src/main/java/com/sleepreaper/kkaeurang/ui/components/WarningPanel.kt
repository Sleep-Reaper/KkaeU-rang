package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import com.sleepreaper.kkaeurang.ui.theme.onWarningScreen
import com.sleepreaper.kkaeurang.ui.theme.warningScreenBg

/**
 * 경고 패널 (WARNING / CRITICAL일 때 사이드 패널 자리). 고대비 배경, 깜빡임 없음.
 * 계기판은 왼쪽에 그대로 두고, 행동이 필요한 정보와 큰 "괜찮아요" 버튼만 여기에 모은다.
 */
@Composable
fun WarningPanel(
    state: DrowsinessUiState,
    onAnswerOkay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = warningScreenBg(state.level)
    val fg = onWarningScreen(state.level)
    val lineStyle = KkaeuType.Title.copy(fontWeight = FontWeight.Bold)

    Column(
        modifier = modifier
            .clip(BlockShape)
            .background(bg)
            .padding(horizontal = 40.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.warning_title), style = KkaeuType.Emphasis, color = fg)
        if (state.isAlarmRunning) {
            Text(text = stringResource(R.string.warning_alarm_running), style = lineStyle, color = fg)
        }
        state.forcedWarningRemainSec?.let { sec ->
            Text(text = stringResource(R.string.warning_forced, clockText(sec)), style = lineStyle, color = fg)
        }
        when (state.voiceState) {
            VoiceState.LISTENING -> Text(
                text = stringResource(R.string.voice_icon_mic) + " " + stringResource(R.string.voice_listening),
                style = lineStyle,
                color = fg,
            )

            VoiceState.ASKING -> Text(
                text = stringResource(R.string.voice_icon_speaker) + " " +
                    (state.voiceQuestion ?: stringResource(R.string.voice_default_question)),
                style = lineStyle,
                color = fg,
            )

            else -> Unit
        }
        Spacer(Modifier.weight(1f))
        OkayButton(
            onClick = onAnswerOkay,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp),
            containerColor = fg,
            contentColor = bg,
        )
    }
}
