package com.sleepreaper.kkaeurang.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

// Material 기본 글꼴 (기존 테스트 화면용)
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** 한국어 줄바꿈을 글자 단위가 아니라 어절(띄어쓰기) 단위로 ("열었어/요" 방지, Android 13+) */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/**
 * 깨우랑 글자 크기. 1~2초 안에 읽히도록 크게.
 * 본문 최소 24sp, 단계 이름 48sp 이상 (Pleos 가이드)
 */
object KkaeuType {
    val Headline = TextStyle(fontSize = 88.sp, lineHeight = 100.sp, fontWeight = FontWeight.Bold, lineBreak = KoreanLineBreak)
    val LevelName = TextStyle(fontSize = 80.sp, lineHeight = 92.sp, fontWeight = FontWeight.Bold)
    val ScoreNumber = TextStyle(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold)
    val Emphasis = TextStyle(fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, lineBreak = KoreanLineBreak)
    val Title = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, lineBreak = KoreanLineBreak)
    val Body = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, lineBreak = KoreanLineBreak)
    val Caption = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal, lineBreak = KoreanLineBreak)
    val Button = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
    val ButtonSmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val Debug = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)

    // 메인 화면 (가운데 계기판 + 네 모서리). 디자인 시안 px × 1.6
    val MainLevel = TextStyle(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
    val MainLogTitle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
    val MainLog = TextStyle(fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium)
    val MainPointsLabel = TextStyle(fontSize = 29.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
    val MainPointsValue = TextStyle(fontSize = 35.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    /** 모서리 기능의 한 줄 글자 */
    val Corner = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, lineBreak = KoreanLineBreak)
    val CornerSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, lineBreak = KoreanLineBreak)
    val CornerStrong = TextStyle(fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
}
