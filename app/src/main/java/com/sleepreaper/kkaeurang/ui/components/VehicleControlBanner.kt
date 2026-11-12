package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.VehicleControlEvent
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import kotlinx.coroutines.delay

const val VEHICLE_BANNER_SHOW_MS = 5_000L

/**
 * 차량 제어 알림 (메인 화면 왼쪽 아래, 창문·공조 상태 위). 이벤트가 들어오면 5초간 보여 준다.
 * blockedReason이 있으면 원래 문구 대신 그 이유를 보여 준다.
 */
@Composable
fun VehicleControlBanner(
    event: VehicleControlEvent?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 이벤트가 바뀔 때마다 5초 타이머를 새로 건다 (애니메이션 없이 즉시 표시/숨김)
    var visible by remember(event) { mutableStateOf(event != null) }
    LaunchedEffect(event) {
        if (event != null) {
            delay(VEHICLE_BANNER_SHOW_MS)
            visible = false
        }
    }
    if (!visible || event == null) return

    val blocked = event.blockedReason != null
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(KkaeuColors.SurfaceHigh)
            .border(2.dp, KkaeuColors.LevelNormal, RoundedCornerShape(28.dp))
            .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(if (blocked) R.drawable.ic_ms_air else R.drawable.ic_ms_sensor_window),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = KkaeuColors.LevelNormal,
        )
        Text(
            text = event.blockedReason ?: event.message,
            style = KkaeuType.CornerSmall,
            modifier = Modifier.weight(1f),
        )
        if (event.canCancel) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 64.dp),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(2.dp, KkaeuColors.TextPrimary.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KkaeuColors.TextPrimary),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Text(text = stringResource(R.string.btn_cancel), style = KkaeuType.CornerSmall)
            }
        }
    }
}
