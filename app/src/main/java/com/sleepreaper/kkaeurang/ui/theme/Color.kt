package com.sleepreaper.kkaeurang.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel

/**
 * 깨우랑 색상. 밤하늘 남색 배경 + 연보라 카드 + 보라 강조색 (디자인 시안 2026-11 기준).
 * 시안 이미지에서 직접 뽑은 값이다.
 */
object KkaeuColors {
    // ── 밤하늘 배경 ──
    val SkyTop = Color(0xFF1B2352)
    val SkyMid = Color(0xFF26306E)
    val Horizon = Color(0xFF4252B8)      // 도로 너머 지평선의 밝은 빛
    val SkyLow = Color(0xFF222B66)
    val SkyBottom = Color(0xFF131A40)
    val Cloud = Color(0xFF3A4596)
    val CloudLight = Color(0xFF5563C9)
    val Star = Color(0xFFF4F1FF)

    // ── 어두운 배경 위 글자 ──
    val TextPrimary = Color(0xFFF7F7FF)
    val TextSecondary = Color(0xFFC6C9F2)
    val TextMuted = Color(0xFF9097D6)

    // ── 연보라 카드 ──
    val CardTop = Color(0xFFD6D8FF)
    val CardBottom = Color(0xFFB9BCFB)
    val CardBorder = Color(0x80FFFFFF)
    val CardText = Color(0xFF1E1F4E)
    val CardTextSub = Color(0xFF4B4E86)
    val CardDivider = Color(0xFF9EA2E2)

    // ── 보라 강조 ──
    val Accent = Color(0xFF8B5CF6)
    val AccentLight = Color(0xFFB794FF)
    val AccentDeep = Color(0xFF5B34D6)
    val AccentSoft = Color(0xFFE4DBFF)  // "혜택 미리보기" 같은 밝은 버튼 배경
    val Yellow = Color(0xFFFFD54A)

    // ── 반투명 알약 (헤더 음성·경고음) ──
    val PillFill = Color(0x1FFFFFFF)
    val PillBorder = Color(0x26FFFFFF)
    val ToggleOff = Color(0xFF4A5084)

    // ── 단계 색상. 색만으로 구분하지 않도록 항상 글자·아이콘 모양과 함께 쓴다 ──
    val LevelNormal = Color(0xFF5CC596)   // 초록 (안전)
    val LevelCaution = Color(0xFFF7C948)  // 노랑 (주의)
    val LevelWarning = Color(0xFFF08A3C)  // 주황 (경고)
    val LevelCritical = Color(0xFFFF4D5A) // 빨강 (위험)

    // 단계 배지 배경 (위: 밝은 쪽, 아래: 어두운 쪽)
    val BadgeNormalTop = Color(0xCC3F6C93)
    val BadgeNormalBottom = Color(0xCC2F557A)
    val BadgeCautionTop = Color(0xFFA58A2C)
    val BadgeCautionBottom = Color(0xFF7D6620)
    val BadgeWarningTop = Color(0xFFA9582A)
    val BadgeWarningBottom = Color(0xFF7E3C1C)
    val BadgeCriticalTop = Color(0xFFC22E3A)
    val BadgeCriticalBottom = Color(0xFF8E1D27)

    // ── 이전 화면(주행 리포트·디버그 패널)이 쓰는 이름. 새 팔레트에 맞춘 값 ──
    val Background = SkyBottom
    val Surface = Color(0xFF232B66)
    val SurfaceHigh = Color(0xFF2F3878)
    val Outline = Color(0xFF4B5394)
    val OnLight = Color(0xFF14183A)       // 밝은 단계색 위에 올리는 글자색
    val Points = Yellow
    val RiskBadge = AccentLight
    val Scrim = Color(0x99000000)
}

fun levelColor(level: DrowsyLevel): Color = when (level) {
    DrowsyLevel.NORMAL -> KkaeuColors.LevelNormal
    DrowsyLevel.CAUTION -> KkaeuColors.LevelCaution
    DrowsyLevel.WARNING -> KkaeuColors.LevelWarning
    DrowsyLevel.CRITICAL -> KkaeuColors.LevelCritical
}

/** 단계 배지 배경 (위→아래 그라데이션) */
fun levelBadgeBrush(level: DrowsyLevel): Brush = Brush.verticalGradient(
    when (level) {
        DrowsyLevel.NORMAL -> listOf(KkaeuColors.BadgeNormalTop, KkaeuColors.BadgeNormalBottom)
        DrowsyLevel.CAUTION -> listOf(KkaeuColors.BadgeCautionTop, KkaeuColors.BadgeCautionBottom)
        DrowsyLevel.WARNING -> listOf(KkaeuColors.BadgeWarningTop, KkaeuColors.BadgeWarningBottom)
        DrowsyLevel.CRITICAL -> listOf(KkaeuColors.BadgeCriticalTop, KkaeuColors.BadgeCriticalBottom)
    }
)

/** 단계 배지 위 글자색 */
fun levelBadgeText(level: DrowsyLevel): Color = when (level) {
    DrowsyLevel.NORMAL -> KkaeuColors.TextPrimary
    DrowsyLevel.CAUTION -> Color(0xFFFFF3C4)
    DrowsyLevel.WARNING -> Color(0xFFFFE6D3)
    DrowsyLevel.CRITICAL -> Color(0xFFFFFFFF)
}

/** 연보라 카드 배경 */
val CardBrush: Brush = Brush.linearGradient(listOf(KkaeuColors.CardTop, KkaeuColors.CardBottom))

/** 보라 버튼·강조 아이콘 */
val AccentBrush: Brush = Brush.verticalGradient(listOf(KkaeuColors.AccentLight, KkaeuColors.Accent))
