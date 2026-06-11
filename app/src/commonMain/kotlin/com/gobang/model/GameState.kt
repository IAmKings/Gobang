package com.gobang.model

/**
 * 游戏状态，作为 ViewModel 的单一数据源。
 * board: 15×15 棋盘（一维数组，0=空,1=黑,2=白）
 * currentTurn: 当前轮次（1=黑,2=白）
 * moveHistory: 已下的棋步列表（用于撤销和重放）
 * undoStack: 被撤销的棋步（用于重做）
 * gameResult: 游戏结果，null 表示游戏进行中
 * wonPositions: 获胜连线坐标（空集表示无人获胜）
 * isAiThinking: AI 是否正在思考
 * difficulty: 难度等级
 * gameMode: 游戏模式
 */
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