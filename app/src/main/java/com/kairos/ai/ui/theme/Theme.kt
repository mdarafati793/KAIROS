package com.kairos.ai.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9EFF), onPrimary = Color(0xFF0A1B4D), primaryContainer = Color(0xFF1F3268),
    onPrimaryContainer = Color(0xFFDCE4FF), secondary = Color(0xFF4FD8EB), secondaryContainer = Color(0xFF104A53),
    onSecondaryContainer = Color(0xFFBDF4FC), background = Color(0xFF0B0F17), surface = Color(0xFF121824),
    surfaceVariant = Color(0xFF1D2536), onSurfaceVariant = Color(0xFFA9B4C7), error = Color(0xFFFF6B6B)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF3558D4), onPrimary = Color.White, primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A1B4D), secondary = Color(0xFF006874), background = Color(0xFFF6F8FC),
    surface = Color.White, surfaceVariant = Color(0xFFE9EEF7), onSurfaceVariant = Color(0xFF4B5568)
)

@Composable
fun KairosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
