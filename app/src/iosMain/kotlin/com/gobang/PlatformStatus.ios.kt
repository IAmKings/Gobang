package com.gobang

import androidx.compose.runtime.Composable

@Composable
actual fun StatusBarStyle(isDark: Boolean) {
    // iOS status bar style is controlled via Info.plist (UIStatusBarStyle)
    // No runtime action needed
}
