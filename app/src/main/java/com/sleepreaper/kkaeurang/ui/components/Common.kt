package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType

val BlockShape = RoundedCornerShape(32.dp)

/** 블록 공통 배경: 둥근 카드 + (선택) 테두리 */
fun Modifier.blockSurface(
    color: Color = KkaeuColors.Surface,
    borderColor: Color? = null,
    borderWidth: Dp = 3.dp,
    shape: Shape = BlockShape,
): Modifier = this
    .clip(shape)
    .background(color)
    .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)

/** 주행 중 화면당 1개만 두는 큰 버튼 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = KkaeuColors.LevelNormal,
    contentColor: Color = KkaeuColors.OnLight,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 128.dp).widthIn(min = 360.dp),
        shape = RoundedCornerShape(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 24.dp),
    ) {
        Text(text = text, style = KkaeuType.Button)
    }
}

/** "괜찮아요" 큰 버튼 (음성 응답의 백업 수단) */
@Composable
fun OkayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = KkaeuColors.LevelNormal,
    contentColor: Color = KkaeuColors.OnLight,
) {
    BigButton(stringResource(R.string.btn_okay), onClick, modifier, containerColor, contentColor)
}

/** 보조 버튼 (채움) */
@Composable
fun MediumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = KkaeuColors.LevelNormal,
    contentColor: Color = KkaeuColors.OnLight,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
    ) {
        Text(text = text, style = KkaeuType.ButtonSmall)
    }
}

/** 보조 버튼 (테두리) */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KkaeuColors.TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(3.dp, color.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
    ) {
        Text(text = text, style = KkaeuType.ButtonSmall)
    }
}

/** 로고. 길게 누르면 디버그 패널이 열린다 (onLongPress가 null이면 아무 일 없음) */
@Composable
fun KkaeuLogo(
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = KkaeuColors.LevelNormal,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Text(
        text = stringResource(R.string.logo_text),
        style = KkaeuType.Title.copy(fontWeight = FontWeight.Black),
        color = color,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { currentOnLongPress?.invoke() })
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** 화면 맨 위 줄: 왼쪽 로고, 오른쪽 trailing */
@Composable
fun KkaeuTopBar(
    onLogoLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    logoColor: Color = KkaeuColors.LevelNormal,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KkaeuLogo(onLongPress = onLogoLongPress, color = logoColor)
        trailing()
    }
}

/** 누적 포인트 (작게, 우상단) */
@Composable
fun PointsBadge(totalPoints: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.points_total, totalPoints),
        style = KkaeuType.Body.copy(fontWeight = FontWeight.SemiBold),
        color = KkaeuColors.Points,
        modifier = modifier,
    )
}

/** 128분 → "2시간 8분" */
@Composable
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_m, m)
        m == 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_h_m, h, m)
    }
}

/** 102분 → "1:42", 161초 → "2:41" (앞 단위 : 뒤 단위 두 자리) */
@Composable
fun clockText(total: Int): String = stringResource(R.string.clock_format, total / 60, total % 60)
