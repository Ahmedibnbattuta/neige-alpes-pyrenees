package fr.neige.stations.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B4F8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E5F7),
    onPrimaryContainer = Color(0xFF00213F),
    secondary = Color(0xFF3E6A8C),
    tertiary = Color(0xFFD9480F),
    background = Color(0xFFF6F9FC),
    surface = Color(0xFFF6F9FC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CCBF5),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF0B4F8A),
    onPrimaryContainer = Color(0xFFD3E5F7),
    secondary = Color(0xFFA9CBE6),
    tertiary = Color(0xFFFFB59A),
    background = Color(0xFF0C1620),
    surface = Color(0xFF0C1620),
)

@Composable
fun NeigeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
