package com.sleepreaper.kkaeurang.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KkaeuDarkColorScheme = darkColorScheme(
    primary = KkaeuColors.LevelNormal,
    onPrimary = KkaeuColors.OnLight,
    secondary = KkaeuColors.LevelCaution,
    onSecondary = KkaeuColors.OnLight,
    background = KkaeuColors.Background,
    onBackground = KkaeuColors.TextPrimary,
    surface = KkaeuColors.Surface,
    onSurface = KkaeuColors.TextPrimary,
    surfaceVariant = KkaeuColors.SurfaceHigh,
    onSurfaceVariant = KkaeuColors.TextSecondary,
    outline = KkaeuColors.Outline,
)

/** 차량 환경이므로 시스템 설정과 상관없이 항상 다크·고대비 테마를 쓴다 */
@Composable
fun KkaeUrangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KkaeuDarkColorScheme,
        typography = Typography,
        content = content
    )
}
