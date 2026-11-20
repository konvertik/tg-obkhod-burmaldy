package ru.mtproxymanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B6CB0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FA),
    onPrimaryContainer = Color(0xFF0B2C52),
    secondary = Color(0xFF4A6077),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1C1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF45494F),
    outline = Color(0xFF8A9099),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2C52),
    primaryContainer = Color(0xFF1F3A5F),
    onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = Color(0xFFB7C7D9),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF1F2530),
    onSurfaceVariant = Color(0xFFB9BEC6),
    outline = Color(0xFF7D838C),
    error = Color(0xFFF2B8B5),
)

@Composable
fun MtProxyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

val OnlineColor = Color(0xFF2E9E5B)
val OfflineColor = Color(0xFFD9534F)
val WarnColor = Color(0xFFD08A00)
