package com.gobang

import androidx.compose.runtime.Composable
import com.gobang.storage.GameStateRepository

@Composable
expect fun AppContent(repository: GameStateRepository)