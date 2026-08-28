package com.alvaro.tvplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Accent = Color(0xFF4C8DFF)
val Bg = Color(0xFF0E1116)
val Surface1 = Color(0xFF171C24)
val Surface2 = Color(0xFF222935)
val TextMuted = Color(0xFF9AA4B1)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Color(0xFFE8EBEF),
    surface = Surface1,
    onSurface = Color(0xFFE8EBEF),
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
