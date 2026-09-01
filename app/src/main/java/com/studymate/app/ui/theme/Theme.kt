package com.studymate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.studymate.app.data.SettingsManager

// ChatGPT / Sleek AI Assistant Dark Palette
val DarkBg = Color(0xFF0F1117)
val DarkSurface = Color(0xFF181B24)
val DarkSurfaceVariant = Color(0xFF222736)
val DarkCardBorder = Color(0xFF2E3547)

val DarkPrimary = Color(0xFF10B981) // Emerald accent (ChatGPT-style)
val DarkOnPrimary = Color(0xFF042F1A)
val DarkPrimaryContainer = Color(0xFF064E3B)
val DarkOnPrimaryContainer = Color(0xFFA7F3D0)

val DarkSecondary = Color(0xFF6366F1) // Electric Indigo
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkSecondaryContainer = Color(0xFF312E81)
val DarkOnSecondaryContainer = Color(0xFFE0E7FF)

val DarkOnSurface = Color(0xFFF3F4F6)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkOutline = Color(0xFF374151)

// Crisp Modern Light Palette
val LightBg = Color(0xFFF9FAFB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF3F4F6)
val LightCardBorder = Color(0xFFE5E7EB)

val LightPrimary = Color(0xFF059669) // Emerald
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD1FAE5)
val LightOnPrimaryContainer = Color(0xFF065F46)

val LightSecondary = Color(0xFF4F46E5) // Indigo
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEEF2FF)
val LightOnSecondaryContainer = Color(0xFF3730A3)

val LightOnSurface = Color(0xFF111827)
val LightOnSurfaceVariant = Color(0xFF4B5563)
val LightOutline = Color(0xFFD1D5DB)

val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = Color(0xFF8B5CF6),
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkCardBorder
)

val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = Color(0xFF7C3AED),
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightCardBorder
)

/**
 * Custom semantic colors for AI Chat and Study components.
 */
object StudyColors {
    val UserBubbleDark = Color(0xFF262C3D)
    val UserBubbleLight = Color(0xFFE5E7EB)

    val AiBubbleDark = Color(0xFF181B24)
    val AiBubbleLight = Color(0xFFFFFFFF)

    val CodeBlockDark = Color(0xFF0B0D13)
    val CodeBlockLight = Color(0xFFF1F5F9)

    val AccentEmerald = Color(0xFF10B981)
    val AccentIndigo = Color(0xFF6366F1)
    val AccentAmber = Color(0xFFF59E0B)
    val AccentRose = Color(0xFFF43F5E)
}

/**
 * App-wide Compose theme supporting dynamic theme preferences (system, dark, light).
 */
@Composable
fun StudyMateTheme(
    themeMode: String = SettingsManager.THEME_SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        SettingsManager.THEME_DARK -> true
        SettingsManager.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
