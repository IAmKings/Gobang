package com.gobang

import androidx.compose.ui.window.ComposeUIViewController
import com.gobang.storage.IOSGameStateRepository
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val repository = IOSGameStateRepository()
    return ComposeUIViewController {
        App(repository = repository)
    }
}