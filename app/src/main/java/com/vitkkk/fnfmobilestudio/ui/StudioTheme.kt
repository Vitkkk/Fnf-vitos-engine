package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColors = darkColorScheme(
    primary = Color(0xFFFF4D8D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4B1730),
    onPrimaryContainer = Color(0xFFFFD9E6),
    secondary = Color(0xFF9D8CFF),
    tertiary = Color(0xFF41D9C5),
    background = Color(0xFF0D0E14),
    surface = Color(0xFF151722),
    surfaceVariant = Color(0xFF222535),
    onBackground = Color(0xFFF1F1F6),
    onSurface = Color(0xFFF1F1F6),
    onSurfaceVariant = Color(0xFFC4C7D4)
)

private val StudioLightColors = lightColorScheme(
    primary = Color(0xFFB31558),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E6),
    onPrimaryContainer = Color(0xFF3D001C),
    secondary = Color(0xFF5C4DB5),
    tertiary = Color(0xFF006B5F),
    background = Color(0xFFF8F7FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9E8F0),
    onBackground = Color(0xFF1A1B20),
    onSurface = Color(0xFF1A1B20),
    onSurfaceVariant = Color(0xFF5E606B)
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StudioDarkColors else StudioLightColors,
        content = content
    )
}
