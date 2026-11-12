package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.VoiceState
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType

/** 음성 패널을 띄우는 상태 (IDLE·ALARM은 패널 없음) */
fun VoiceState.showsVoicePanel(): Boolean = when (this) {
    VoiceState.ASKING, VoiceState.LISTENING, VoiceState.RESULT,
    VoiceState.NO_RESPONSE, VoiceState.FAILED -> true
    VoiceState.IDLE, VoiceState.ALARM -> false
}

/**
 * 음성 질문 패널 (사이드 패널용 세로 배치).
 * 상태별 문구 + 언제나 "괜찮아요" 큰 버튼 1개 (음성이 안 될 때의 백업).
 */
@Composable
fun VoicePanel(
    voiceState: VoiceState,
    question: String?,
    recognizedText: String?,
    onAnswerOkay: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = KkaeuColors.LevelCaution,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .blockSurface(color = KkaeuColors.SurfaceHigh, borderColor = accent)
            .padding(horizontal = 36.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VoiceMessage(voiceState, question ?: stringResource(R.string.voice_default_question), recognizedText)
        Text(
            text = stringResource(R.string.voice_points_hint),
            style = KkaeuType.Caption,
            color = KkaeuColors.TextSecondary,
        )
        OkayButton(onClick = onAnswerOkay, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VoiceMessage(voiceState: VoiceState, question: String, recognizedText: String?) {
    when (voiceState) {
        VoiceState.ASKING -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = stringResource(R.string.voice_icon_speaker), style = TextStyle(fontSize = 48.sp))
            Text(text = question, style = KkaeuType.Title)
        }

        VoiceState.LISTENING -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // 띵 이후 녹음이 시작되는 순간이라 가장 크게 보여 준다
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(KkaeuColors.LevelNormal.copy(alpha = 0.18f))
                    .border(4.dp, KkaeuColors.LevelNormal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.voice_icon_mic), style = TextStyle(fontSize = 56.sp))
            }
            Text(
                text = stringResource(R.string.voice_listening),
                style = KkaeuType.Emphasis,
                color = KkaeuColors.LevelNormal,
            )
        }

        VoiceState.RESULT -> Text(
            text = stringResource(R.string.voice_result, recognizedText.orEmpty()),
            style = KkaeuType.Emphasis,
        )

        VoiceState.NO_RESPONSE -> Text(
            text = stringResource(R.string.voice_no_response),
            style = KkaeuType.Emphasis,
        )

        VoiceState.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = question, style = KkaeuType.Title)
            Text(
                text = stringResource(R.string.voice_failed),
                style = KkaeuType.Caption,
                color = KkaeuColors.TextSecondary,
            )
        }

        VoiceState.IDLE, VoiceState.ALARM -> Unit
    }
}
