package com.gobang.storage

import com.gobang.model.Difficulty
import com.gobang.model.GameMode
import com.gobang.model.Move
import kotlinx.serialization.Serializable

@Serializable
data class SavedGame(
    val boardState: String,
    val currentTurn: Int,
    val moveHistory: List<Move>,
    val difficulty: Difficulty,
    val gameMode: GameMode,
    val savedAt: Long,
)