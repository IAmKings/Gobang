package com.gobang

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gobang.storage.GameStateRepository
import com.gobang.ui.theme.GobangTheme

@Composable
fun App(repository: GameStateRepository? = null) {
    GobangTheme {
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