package com.sleepreaper.kkaeurang.ui.theme

import androidx.compose.ui.graphics.Color
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel

/** 깨우랑 색상. 차량(야간) 환경에 맞춘 다크·고대비 */
object KkaeuColors {
    val Background = Color(0xFF0A0D12)
    val Surface = Color(0xFF151A21)
    val SurfaceHigh = Color(0xFF1F2630)
    val Outline = Color(0xFF384250)
    val TextPrimary = Color(0xFFF4F6F9)
    val TextSecondary = Color(0xFFAEB7C4)
    val OnLight = Color(0xFF0A0D12)      // 밝은 단계색 위에 올리는 글자색
    val Points = Color(0xFFFFD45C)
    val RiskBadge = Color(0xFFB9A6FF)
    val Scrim = Color(0x99000000)

    // 단계 색상. 색만으로 구분하지 않도록 항상 글자·아이콘 모양과 함께 쓴다
    val LevelNormal = Color(0xFF34D17A)   // 초록 (안정)
    val LevelCaution = Color(0xFFFFD43B)  // 노랑 (주의)
    val LevelWarning = Color(0xFFFF8A1F)  // 주황 (경고)
    val LevelCritical = Color(0xFFFF4545) // 빨강 (위험)

    // 경고 패널 배경 (고대비, 깜빡임 없음)
    val WarningScreenBg = Color(0xFFFF8A1F)
    val OnWarningScreen = Color(0xFF140800)
    val CriticalScreenBg = Color(0xFFC0161C)
    val OnCriticalScreen = Color(0xFFFFFFFF)
}

fun levelColor(level: DrowsyLevel): Color = when (level) {
    DrowsyLevel.NORMAL -> KkaeuColors.LevelNormal
    DrowsyLevel.CAUTION -> KkaeuColors.LevelCaution
    DrowsyLevel.WARNING -> KkaeuColors.LevelWarning
    DrowsyLevel.CRITICAL -> KkaeuColors.LevelCritical
}

/** 경고 화면 배경색 */
fun warningScreenBg(level: DrowsyLevel): Color =
    if (level == DrowsyLevel.CRITICAL) KkaeuColors.CriticalScreenBg else KkaeuColors.WarningScreenBg

/** 경고 화면 배경 위 글자색 */
fun onWarningScreen(level: DrowsyLevel): Color =
    if (level == DrowsyLevel.CRITICAL) KkaeuColors.OnCriticalScreen else KkaeuColors.OnWarningScreen
