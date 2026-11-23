package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = HorizonAmber,
    onPrimary = MidnightNavy,
    primaryContainer = HorizonAmberContainer,
    onPrimaryContainer = HorizonAmber,
    secondary = StarlightBlue,
    onSecondary = MidnightNavy,
    secondaryContainer = StarlightBlueContainer,
    onSecondaryContainer = StarlightText,
    tertiary = DawnCoral,
    onTertiary = MidnightNavy,
    tertiaryContainer = DawnCoralContainer,
    onTertiaryContainer = DawnCoral,
    background = MidnightNavy,
    onBackground = StarlightText,
    surface = DeepTwilight,
    onSurface = StarlightText,
    surfaceVariant = TwilightSurface,
    onSurfaceVariant = MutedStarlight,
    surfaceContainerHigh = TwilightSurfaceHigh,
    outline = TwilightOutline,
    outlineVariant = TwilightSurfaceHigh,
    error = DawnCoral,
    onError = MidnightNavy
)

private val LightColorScheme = lightColorScheme(
    primary = DeepIndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepIndigoContainer,
    onPrimaryContainer = DeepIndigoPrimary,
    secondary = WarmAmberAccent,
    onSecondary = Color.White,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = InkNavy,
    tertiary = CoralSecondary,
    onTertiary = Color.White,
    background = DawnSkyBackground,
    onBackground = InkNavy,
    surface = DawnCardSurface,
    onSurface = InkNavy,
    surfaceVariant = DawnCardVariant,
    onSurfaceVariant = MutedSlate,
    surfaceContainerHigh = DawnCardVariant,
    outline = DawnOutline,
    outlineVariant = DawnCardVariant,
    error = CoralSecondary,
    onError = Color.White
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TwilightAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
