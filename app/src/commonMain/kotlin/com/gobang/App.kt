package com.gobang

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gobang.storage.GameStateRepository
import com.gobang.ui.theme.GobangTheme
import com.gobang.ui.theme.ThemeManager

/** 应用入口，包裹主题和系统栏内边距，避免内容被状态栏/刘海遮挡 */
@Composable
fun App(repository: GameStateRepository? = null) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    ObserveStatusBar(themeMode)
    GobangTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            if (repository != null) {
                AppContent(repository = repository)
            } else {
                AppContentImpl()
            }
        }
    }
}