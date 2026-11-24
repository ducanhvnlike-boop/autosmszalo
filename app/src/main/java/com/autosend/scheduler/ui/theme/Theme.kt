package com.autosend.scheduler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZaloBlue = Color(0xFF0068FF)
val ZaloBlueDark = Color(0xFF004FC4)
val ZaloBlueLight = Color(0xFFE8F1FF)
val SuccessGreen = Color(0xFF15803D)
val SuccessGreenBg = Color(0xFFDCFCE7)
val WarningAmber = Color(0xFFB45309)
val WarningAmberBg = Color(0xFFFEF3C7)
val DangerRed = Color(0xFFB91C1C)
val DangerRedBg = Color(0xFFFEE2E2)
val SurfaceBg = Color(0xFFF6F8FC)

private val AppColorScheme = lightColorScheme(
    primary = ZaloBlue,
    onPrimary = Color.White,
    primaryContainer = ZaloBlueLight,
    onPrimaryContainer = ZaloBlueDark,
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    background = SurfaceBg,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF475569),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun AutoSendZaloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
