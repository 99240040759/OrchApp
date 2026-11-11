package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OrchColorScheme = darkColorScheme(
    primary = OrchAccentCoral,
    onPrimary = OrchTextPrimary,
    primaryContainer = OrchSurface,
    onPrimaryContainer = OrchTextPrimary,
    secondary = OrchAccentAmber,
    onSecondary = OrchBackground,
    secondaryContainer = OrchBorder,
    onSecondaryContainer = OrchTextPrimary,
    tertiary = OrchAccentAmber,
    onTertiary = OrchBackground,
    background = OrchBackground,
    onBackground = OrchTextPrimary,
    surface = OrchSurface,
    onSurface = OrchTextPrimary,
    surfaceVariant = OrchSurface,
    onSurfaceVariant = OrchTextSecondary,
    outline = OrchBorder,
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
        content = content
    )
}
