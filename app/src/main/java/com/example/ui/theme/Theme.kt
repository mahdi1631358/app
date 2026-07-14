package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CodyarLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C2B4A), // CodyarNavy
    primaryContainer = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    secondary = Color(0xFFE0393E), // CodyarRed
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA), // CodyarBg
    onBackground = Color(0xFF0F172A), // Very dark slate for high contrast
    surface = Color.White,
    onSurface = Color(0xFF0F172A), // Very dark slate for high contrast
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    error = Color(0xFFEF4444),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CodyarLightColorScheme,
        typography = Typography,
        content = content
    )
}
