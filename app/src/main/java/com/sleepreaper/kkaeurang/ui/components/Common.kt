package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors

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
