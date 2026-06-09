package com.gobang

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gobang.storage.GameStateRepository

@Composable
actual fun AppContent(repository: GameStateRepository) {
    AppContentImpl(modifier = Modifier, repository = repository)
}