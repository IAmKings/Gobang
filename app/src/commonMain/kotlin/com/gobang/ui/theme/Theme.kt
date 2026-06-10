package com.gobang.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber700 = Color(0xFFF57C00)
val Amber900 = Color(0xFFE65100)
val Amber500 = Color(0xFFFFC107)
val Amber100 = Color(0xFFFFECB3)

val BoardLight = Color(0xFFDEB887)
val BoardDark = Color(0xFFD2A86E)
val GridLine = Color(0xFF4A3728)

val StoneBlack = Color(0xFF1A1A1A)
val StoneWhite = Color(0xFFF5F5F5)
val LastMoveMarker = Color(0xFFFF1744)
val WinHighlight = Color(0xFFFFD600)
val StarDot = Color(0xFF4A3728)

private val LightColorScheme = lightColorScheme(
    primary = Amber700,
    onPrimary = Color.White,
    secondary = Amber500,
    background = Color(0xFFFFFBF0),
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColorScheme = darkColorScheme(
    primary = Amber500,
    onPrimary = Color(0xFF1A1A1A),
    secondary = Amber700,
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF2D2D2D),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
)

@Composable
fun GobangTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}