package com.gobang.model

data class GameState(
    val board: IntArray = IntArray(15 * 15),
    val currentTurn: Int = 1,
    val moveHistory: List<Move> = emptyList(),
    val undoStack: List<Move> = emptyList(),
    val gameResult: GameResult? = null,
    val wonPositions: Set<Pair<Int, Int>> = emptySet(),
    val isAiThinking: Boolean = false,
    val difficulty: Difficulty = Difficulty.Medium,
    val gameMode: GameMode = GameMode.PvAI,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false
        return board.contentEquals(other.board) &&
            currentTurn == other.currentTurn &&
            moveHistory == other.moveHistory &&
            gameResult == other.gameResult &&
            wonPositions == other.wonPositions &&
            isAiThinking == other.isAiThinking &&
            difficulty == other.difficulty &&
            gameMode == other.gameMode
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + currentTurn
        result = 31 * result + moveHistory.hashCode()
        result = 31 * result + gameResult.hashCode()
        result = 31 * result + wonPositions.hashCode()
        result = 31 * result + isAiThinking.hashCode()
        result = 31 * result + difficulty.hashCode()
        result = 31 * result + gameMode.hashCode()
        return result
    }
}