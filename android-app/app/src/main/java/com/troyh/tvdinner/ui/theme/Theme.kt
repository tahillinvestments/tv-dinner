package com.troyh.tvdinner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CinemaPrimary,
    secondary = CinemaSecondary,
    tertiary = CinemaAccent,
    background = CinemaBackground,
    surface = CinemaSurface,
    surfaceVariant = CinemaSurfaceVariant,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = CinemaRed
)

@Composable
fun TVDinnerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
