package com.gobang

import com.gobang.model.Difficulty
import com.gobang.model.GameMode

sealed class Screen {
    data object MainMenu : Screen()
    data class Game(val mode: GameMode, val difficulty: Difficulty) : Screen()
    data object Settings : Screen()
}