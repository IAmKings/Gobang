package com.gobang.ui.theme

/** 五子棋主题颜色定义：琥珀色调 + 棋盘木色 */

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 主题色
val Amber700 = Color(0xFFF57C00)   // 主色
val Amber900 = Color(0xFFE65100)   // 深色变体
val Amber500 = Color(0xFFFFC107)   // 亮色变体
val Amber100 = Color(0xFFFFECB3)   // 浅色背景

// 棋盘色
val BoardLight = Color(0xFFDEB887)  // 棋盘浅色格/底色
val BoardDark = Color(0xFFD2A86E)   // 棋盘深色格（当前未使用）
val GridLine = Color(0xFF4A3728)    // 网格线颜色

// 棋子色
val StoneBlack = Color(0xFF1A1A1A)  // 黑棋
val StoneWhite = Color(0xFFF5F5F5)  // 白棋
val LastMoveMarker = Color(0xFFFF1744) // 最后一步标记（红色圆点）
val WinHighlight = Color(0xFFFFD600)   // 获胜高亮（当前未使用）
val StarDot = Color(0xFF4A3728)       // 星位标记点

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