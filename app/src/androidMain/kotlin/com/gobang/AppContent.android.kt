package com.gobang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gobang.ai.AndroidLazyAiSearchEngine
import com.gobang.storage.GameStateRepository

@Composable
actual fun AppContent(repository: GameStateRepository) {
    val context = LocalContext.current.applicationContext
    val aiEngine = remember(context) { AndroidLazyAiSearchEngine(context) }
    DisposableEffect(aiEngine) {
        onDispose { aiEngine.close() }
    }
    AppContentImpl(modifier = Modifier, repository = repository, aiEngine = aiEngine)
}
