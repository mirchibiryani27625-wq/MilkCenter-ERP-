package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color.White,
    primaryContainer = NavyDark,
    onPrimaryContainer = Color.White,
    secondary = DairyGreenSecondary,
    onSecondary = Color.White,
    background = Color(0xFF101924),
    surface = Color(0xFF182332),
    surfaceVariant = Color(0xFF223042),
    onBackground = Color(0xFFF0F4F8),
    onSurface = Color(0xFFF0F4F8),
    tertiary = GoldenYellowHighlight
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary, // #17365D
    onPrimary = Color.White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = OnNavyPrimaryContainer,
    secondary = DairyGreenSecondary, // #239B56
    onSecondary = Color.White,
    secondaryContainer = DairyGreenContainer,
    background = SoftWhiteBackground, // #F5F8FC
    surface = CardPureWhite, // #FFFFFF
    surfaceVariant = SurfaceVariantSoft,
    onBackground = DarkNavyTextPrimary, // #172B4D
    onSurface = DarkNavyTextPrimary,
    onSurfaceVariant = GreyTextSecondary, // #667085
    tertiary = GoldenYellowHighlight, // #F4C542
    error = ErrorRed,
    errorContainer = ErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve custom Deep Navy & Dairy Green identity
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
