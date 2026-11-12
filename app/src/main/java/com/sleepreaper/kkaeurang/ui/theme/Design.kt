package com.sleepreaper.kkaeurang.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 디자인 시안(가로 1478 × 세로 1064px)의 1px이 화면에서 몇 dp인지.
 *
 * 화면 코드는 시안에서 잰 px 값을 그대로 쓰고 [dp]·[sp]로 바꾼다 → 앱 영역 크기가 달라도 시안 비율이 유지된다.
 * Pleos Connect 앱 영역(약 1707×1208dp)에서는 1px ≈ 1.135dp.
 */
@Immutable
class DesignScale(val dpPerPx: Float) {
    fun dp(px: Int): Dp = (px * dpPerPx).dp
    fun dp(px: Float): Dp = (px * dpPerPx).dp
    fun sp(px: Int): TextUnit = (px * dpPerPx).sp
    fun sp(px: Float): TextUnit = (px * dpPerPx).sp

    /** 시안 기준 글자 스타일 */
    fun text(
        px: Int,
        weight: FontWeight = FontWeight.Medium,
        color: Color = Color.Unspecified,
        lineHeight: Float = 1.25f,
        italic: Boolean = false,
    ) = TextStyle(
        fontSize = sp(px),
        lineHeight = sp(px * lineHeight),
        fontWeight = weight,
        color = color,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineBreak = KoreanPhraseBreak,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    companion object {
        const val DESIGN_WIDTH = 1478f
        const val DESIGN_HEIGHT = 1064f

        /** 미리보기 기본값 (Pleos 앱 영역) */
        val Default = DesignScale(1.135f)
    }
}

val LocalDesign = staticCompositionLocalOf { DesignScale.Default }

/** 한국어 줄바꿈을 어절 단위로 (Android 13+) */
private val KoreanPhraseBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/**
 * 영역 크기에 맞춰 [DesignScale]을 정하고 [content]에 제공한다.
 * 가로·세로 중 더 빠듯한 쪽에 맞추므로 원·아이콘이 찌그러지지 않는다. 남는 가로 폭은 각 화면이 나눠 쓴다.
 */
@Composable
fun ProvideDesignScale(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = minOf(maxWidth.value / DesignScale.DESIGN_WIDTH, maxHeight.value / DesignScale.DESIGN_HEIGHT)
        CompositionLocalProvider(LocalDesign provides DesignScale(scale)) {
            content()
        }
    }
}

/** 자주 쓰는 굵기 */
object W {
    val Medium = FontWeight.Medium
    val SemiBold = FontWeight.SemiBold
    val Bold = FontWeight.Bold
    val ExtraBold = FontWeight.ExtraBold
}
