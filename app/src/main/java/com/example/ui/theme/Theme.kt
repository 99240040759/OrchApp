package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object OrchDimens {
    val ScreenPadding = 16.dp
    val SectionGap = 14.dp
    val ItemGap = 8.dp
    val TightGap = 4.dp
    val CardPaddingH = 14.dp
    val CardPaddingV = 12.dp
    val ButtonHeight = 44.dp
    val TouchTarget = 48.dp
    val TopBarHeight = 56.dp
}

val OrchShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private val OrchColorScheme = darkColorScheme(
    primary = OrchAccentCoral,
    onPrimary = OrchTextPrimary,
    primaryContainer = OrchSurface,
    onPrimaryContainer = OrchTextPrimary,
    secondary = OrchAccentAmber,
    onSecondary = OrchBackground,
    secondaryContainer = OrchSurfaceElevated,
    onSecondaryContainer = OrchTextPrimary,
    tertiary = OrchAccentAmber,
    onTertiary = OrchBackground,
    background = OrchBackground,
    onBackground = OrchTextPrimary,
    surface = OrchBackground,
    onSurface = OrchTextPrimary,
    surfaceVariant = OrchSurface,
    onSurfaceVariant = OrchTextSecondary,
    surfaceContainerLowest = OrchBackground,
    surfaceContainerLow = OrchSurface,
    surfaceContainer = OrchSurface,
    surfaceContainerHigh = OrchSurfaceElevated,
    surfaceContainerHighest = OrchSurfaceElevated,
    outline = OrchBorder,
    outlineVariant = OrchBorder,
    error = OrchAccentCoral,
    onError = OrchTextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OrchColorScheme,
        typography = Typography,
        shapes = OrchShapes,
        content = content
    )
}
