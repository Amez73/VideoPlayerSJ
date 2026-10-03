package com.shareef.videoplayersj.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same accent as the Android app, so the two read as one product.
private val AccentBlue = Color(0xFF7AA2FF)
private val AccentBlueDark = Color(0xFF4C6FE0)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color(0xFF0B1638),
    primaryContainer = Color(0xFF26386E),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondaryContainer = Color(0xFF2A2D38),
    onSecondaryContainer = Color(0xFFE0E2EE),
    background = Color(0xFF0F1015),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF0F1015),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF22242C),
    onSurfaceVariant = Color(0xFFA9ABB8),
    surfaceContainerLowest = Color(0xFF0A0B0F),
    surfaceContainerLow = Color(0xFF15161C),
    surfaceContainer = Color(0xFF1A1B22),
    surfaceContainerHigh = Color(0xFF22242C),
    surfaceContainerHighest = Color(0xFF2B2D36),
    outline = Color(0xFF454857),
    outlineVariant = Color(0xFF2E303A),
)

private val LightColors = lightColorScheme(
    primary = AccentBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF0B1638),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF7F7FB),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE3E4EE),
    onSurfaceVariant = Color(0xFF5A5D6B),
    surfaceContainerLow = Color(0xFFF0F0F6),
    surfaceContainer = Color(0xFFEAEAF2),
    surfaceContainerHigh = Color(0xFFE3E4EE),
    surfaceContainerHighest = Color(0xFFDCDDE8),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

@Composable
fun VideoPlayerSJTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
