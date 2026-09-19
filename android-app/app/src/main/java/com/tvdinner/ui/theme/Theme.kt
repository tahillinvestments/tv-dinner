package com.tvdinner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ThemeOption(
    val id: String,
    val name: String,
    val tagline: String,
    val bgColor: Color,
    val cardColor: Color,
    val accentColor: Color
)

val AVAILABLE_THEMES = listOf(
    ThemeOption(
        id = "classic",
        name = "Midnight Classic",
        tagline = "Streambert dark slate & ruby red accent",
        bgColor = Color(0xFF000000),
        cardColor = Color(0xFF131927),
        accentColor = Color(0xFFEF4444)
    ),
    ThemeOption(
        id = "bento",
        name = "Obsidian Bento",
        tagline = "Deep obsidian, frosted glass & amber glow",
        bgColor = Color(0xFF000000),
        cardColor = Color(0xFF141823),
        accentColor = Color(0xFFF59E0B)
    ),
    ThemeOption(
        id = "cyber",
        name = "Cyber Stage",
        tagline = "Tactile cockpit dark slate & electric cyan",
        bgColor = Color(0xFF000000),
        cardColor = Color(0xFF101422),
        accentColor = Color(0xFF06B6D4)
    ),
    ThemeOption(
        id = "light",
        name = "Nordic Daybreak",
        tagline = "Clean porcelain light mode & electric indigo",
        bgColor = Color(0xFFF8FAFC),
        cardColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFF4F46E5)
    )
)

private val ClassicColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),
    secondary = Color(0xFFA855F7),
    tertiary = Color(0xFFEF4444),
    background = Color(0xFF000000),
    surface = Color(0xFF111624),
    surfaceVariant = Color(0xFF1A2234),
    onPrimary = Color(0xFFF8FAFC),
    onSecondary = Color(0xFFF8FAFC),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444)
)

private val BentoColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFFD97706),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF000000),
    surface = Color(0xFF121622),
    surfaceVariant = Color(0xFF1B2132),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFA3AAB5),
    error = Color(0xFFEF4444)
)

private val CyberColorScheme = darkColorScheme(
    primary = Color(0xFF06B6D4),
    secondary = Color(0xFFEC4899),
    tertiary = Color(0xFF06B6D4),
    background = Color(0xFF000000),
    surface = Color(0xFF0E1320),
    surfaceVariant = Color(0xFF171E32),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF4338CA),
    tertiary = Color(0xFF4F46E5),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFEF4444)
)

data class TVDinnerColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceLight: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val focus: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val red: Color = Color(0xFFEF4444),
    val green: Color = Color(0xFF10B981),
    val yellow: Color = Color(0xFFF59E0B)
)

val ClassicTVColors = TVDinnerColors(
    background = Color(0xFF000000),
    surface = Color(0xFF111624),
    surfaceVariant = Color(0xFF1A2234),
    surfaceLight = Color(0xFF222B40),
    primary = Color(0xFF6366F1),
    secondary = Color(0xFFA855F7),
    accent = Color(0xFF06B6D4),
    focus = Color(0xFF38BDF8),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B)
)

val BentoTVColors = TVDinnerColors(
    background = Color(0xFF000000),
    surface = Color(0xFF121622),
    surfaceVariant = Color(0xFF1B2132),
    surfaceLight = Color(0xFF242C42),
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFFD97706),
    accent = Color(0xFFF59E0B),
    focus = Color(0xFFFBBF24),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA3AAB5),
    textMuted = Color(0xFF6B7280)
)

val CyberTVColors = TVDinnerColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0E1320),
    surfaceVariant = Color(0xFF171E32),
    surfaceLight = Color(0xFF202A44),
    primary = Color(0xFF06B6D4),
    secondary = Color(0xFFEC4899),
    accent = Color(0xFF06B6D4),
    focus = Color(0xFF22D3EE),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B)
)

val LightTVColors = TVDinnerColors(
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    surfaceLight = Color(0xFFCBD5E1),
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF4338CA),
    accent = Color(0xFF4F46E5),
    focus = Color(0xFF6366F1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B)
)

val LocalTVDinnerColors = androidx.compose.runtime.staticCompositionLocalOf { ClassicTVColors }

@Composable
fun TVDinnerTheme(
    themeKey: String = "classic",
    content: @Composable () -> Unit
) {
    val (colorScheme, tvColors) = when (themeKey.lowercase()) {
        "bento" -> BentoColorScheme to BentoTVColors
        "cyber" -> CyberColorScheme to CyberTVColors
        "light" -> LightColorScheme to LightTVColors
        else -> ClassicColorScheme to ClassicTVColors
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTVDinnerColors provides tvColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
