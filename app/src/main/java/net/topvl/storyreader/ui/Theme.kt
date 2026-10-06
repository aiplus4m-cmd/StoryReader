package net.topvl.storyreader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandOrange = Color(0xFFF28A2E)
val BrandBlue = Color(0xFF2672B8)

private val Light = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandOrange,
    onSecondary = Color.White,
    tertiary = BrandOrange,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF0D2E4F),
    secondaryContainer = Color(0xFFFDE3CC),
    onSecondaryContainer = Color(0xFF4A2300),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FC1F0),
    onPrimary = Color(0xFF00325A),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4A2300),
    tertiary = BrandOrange,
    primaryContainer = Color(0xFF1B4C7C),
    secondaryContainer = Color(0xFF6B3A10),
)

@Composable
fun StoryReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
