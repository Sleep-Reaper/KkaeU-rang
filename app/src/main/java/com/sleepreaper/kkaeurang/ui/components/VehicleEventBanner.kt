package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign

/**
 * 깨우랑이 창문·찬바람을 움직였을 때 잠깐 띄우는 안내 ("졸음 감지로 창문을 조금 열었어요" 등).
 * 홈·경보 화면 어디서든 본문 아래쪽에 겹쳐 보인다. [event]가 null이 되면 서서히 사라진다.
 * 비로 막힌 이유([VehicleControlEvent.blockedReason])는 위험 화면 창문 패널이 따로 보여 준다.
 */
@Composable
fun VehicleEventBanner(event: VehicleControlEvent?, modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    // 사라지는 동안에도 마지막 문구를 그대로 보여 준다
    var lastMessage by remember { mutableStateOf("") }
    if (event != null) lastMessage = event.message

    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Row(
            modifier = Modifier
                .height(d.dp(64))
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color(0xF02F62D8), Color(0xF04F4BD8))))
                .border(d.dp(1.5f), Color(0x80A8D4FF), RoundedCornerShape(50))
                .padding(start = d.dp(22), end = d.dp(30)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(14)),
        ) {
            KIcon(R.drawable.ic_k_window, d.dp(32), Color(0xFFE6F2FF))
            Text(
                text = lastMessage,
                style = d.text(25, FontWeight.Bold, KkaeuColors.TextPrimary),
                maxLines = 1,
            )
        }
    }
}
