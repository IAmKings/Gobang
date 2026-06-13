package com.gobang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
expect fun StatusBarStyle(isDark: Boolean)

@Composable
fun ObserveStatusBar(themeMode: com.gobang.ui.theme.ThemeMode) {
    val isDark = when (themeMode) {
        com.gobang.ui.theme.ThemeMode.Light -> false
        com.gobang.ui.theme.ThemeMode.Dark -> true
        com.gobang.ui.theme.ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    StatusBarStyle(isDark)
}
