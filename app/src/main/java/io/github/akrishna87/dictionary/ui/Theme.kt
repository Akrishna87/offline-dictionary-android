package io.github.akrishna87.dictionary.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object Palette {
    val Background = Color(0xFF0A0A0E)
    val Elevated = Color(0xFF16161D)
    val Elevated2 = Color(0xFF202029)
    val Text = Color(0xFFFFFFFF)
    val SubText = Color(0xFFA3A3B2)
    val Coral = Color(0xFFFF7A59)
    val Violet = Color(0xFF8E6BFF)
}

private val DarkColors = darkColorScheme(
    primary = Palette.Coral,
    onPrimary = Color(0xFF1A0600),
    secondary = Palette.Violet,
    secondaryContainer = Color(0xFF2E2550),
    onSecondaryContainer = Color(0xFFE9E2FF),
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Elevated2,
    onSurfaceVariant = Palette.SubText,
    surfaceContainer = Palette.Elevated,
    surfaceContainerHigh = Palette.Elevated2,
    outlineVariant = Color(0xFF2A2A34),
)

@Composable
fun DictionaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors) {
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
