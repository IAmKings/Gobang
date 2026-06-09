package com.gobang

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gobang.storage.JvmGameStateRepository

fun main() = application {
    val repository = JvmGameStateRepository()
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gobang",
        state = rememberWindowState(width = 600.dp, height = 800.dp)
    ) {
        App(repository = repository)
    }
}