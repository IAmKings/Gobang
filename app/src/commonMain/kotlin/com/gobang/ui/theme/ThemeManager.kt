package com.gobang.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { Light, Dark, System }

object ThemeManager {
    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    val currentThemeMode: ThemeMode get() = _themeMode.value

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }
}
