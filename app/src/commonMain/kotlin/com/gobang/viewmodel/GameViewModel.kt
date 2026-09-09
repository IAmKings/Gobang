package com.gobang.viewmodel

import com.gobang.engine.GobangBoard
import com.gobang.engine.GobangSearcher
import com.gobang.engine.Opening
import com.gobang.model.Difficulty
import com.gobang.model.GameMode
import com.gobang.model.GameResult
import com.gobang.model.GameState
import com.gobang.model.Move
import com.gobang.storage.GameStateRepository
import com.gobang.storage.SavedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 游戏视图模型，管理游戏状态和 AI 逻辑。
 *
 * 核心流程：
 * 1. newGame() → 设置初始状态 → 如果轮到 AI 则触发 AI 思考
 * 2. handleUserMove() → 用户点击棋盘 → applyMove() → 检查胜负 → 如果轮到 AI 则触发思考
 * 3. computeAiMove() → 在后台线程运行搜索 → 落子 → 循环检查是否仍需 AI 思考
 * 4. undo()/redo() → 撤销/重做棋步（AI 模式下自动撤两步）
 */
class GameViewModel(
    private val searcher: GobangSearcher = GobangSearcher(),
    private val repository: GameStateRepository? = null,
) {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val board = GobangBoard()

    /** 当前正在运行的 AI 搜索协程句柄（用于思考期间取消）；null 表示无进行中搜索 */
    private var aiSearchJob: Job? = null

    private companion object {
        /** Hard：迭代加深最大层（Difficulty.Hard.depth=3 字段保持存档兼容，此处映射实际搜索上限） */
        const val AI_MAX_DEPTH_HARD = 6
        /** Hard：单步时间预算（毫秒），P2 起可随平台/设置调优 */
        const val AI_BUDGET_MS_HARD = 1000L
    }

    /** 开始新游戏，可选指定开局 */
    fun newGame(mode: GameMode, difficulty: Difficulty, opening: Opening? = null) {
        board.reset()
        cancelAiSearch()
        var initialBoard = IntArray(15 * 15)
        var initialHistory = emptyList<Move>()
        var initialTurn = 1

        if (opening != null) {
            for (move in opening.moves) {
                board.put(move.row, move.col, move.stone)
                initialBoard[move.row * 15 + move.col] = move.stone
                initialHistory = initialHistory + Move(move.row, move.col, move.stone)
            }
            initialTurn = if (opening.moves.size % 2 == 0) 1 else 2
        }

        _state.value = GameState(
            board = initialBoard,
            currentTurn = initialTurn,
            moveHistory = initialHistory,
            undoStack = emptyList(),
            gameResult = null,
            wonPositions = emptySet(),
            isAiThinking = false,
            difficulty = difficulty,
            gameMode = mode,
        )

        if (shouldAiMove(_state.value)) {
            triggerAiMove()
        }
    }

/** 处理用户落子，AI 思考时禁止操作 */
    fun handleUserMove(row: Int, col: Int) {
        val s = _state.value
        if (s.isAiThinking) return
        applyMove(row, col, s.currentTurn)
    }

/** 实际落子逻辑：更新棋盘、检查胜负、触发 AI */
    private fun applyMove(row: Int, col: Int, stone: Int) {
        val s = _state.value
        if (s.gameResult != null) return
        if (s.board[row * 15 + col] != 0) return

        val newBoard = s.board.copyOf()
        newBoard[row * 15 + col] = stone
        board.loads("")
        for (i in 0 until 225) {
            if (newBoard[i] != 0) {
                board.put(i / 15, i % 15, newBoard[i])
            }
        }
        val wonPositions = if (board.check() != 0) board.won else emptySet()
        val gameResult = when {
            wonPositions.isNotEmpty() && stone == 1 -> GameResult.BlackWins
            wonPositions.isNotEmpty() && stone == 2 -> GameResult.WhiteWins
            newBoard.all { it != 0 } -> GameResult.Draw
            else -> null
        }

        val nextTurn = if (stone == 1) 2 else 1
        _state.value = s.copy(
            board = newBoard,
            currentTurn = nextTurn,
            moveHistory = s.moveHistory + Move(row, col, stone),
            undoStack = emptyList(),
            gameResult = gameResult,
            wonPositions = wonPositions,
        )

        if (gameResult == null && shouldAiMove(_state.value)) {
            triggerAiMove()
        }
    }

    /** 判断当前是否应该由 AI 落子 */
private fun shouldAiMove(s: GameState): Boolean {
        return when (s.gameMode) {
            GameMode.PvAI -> s.currentTurn == 2
            GameMode.AIvP -> s.currentTurn == 1
            GameMode.AIvAI -> true
            GameMode.PvP -> false
        }
    }

    /** 标记 AI 正在思考，触发 UI 层的 LaunchedEffect 调用 computeAiMove */
private fun triggerAiMove() {
        _state.value = _state.value.copy(isAiThinking = true)
    }

    /**
     * 取消当前 AI 搜索（幂等）：供 undo/redo/newGame 在 AI 思考期间调用。
     * 引擎单层搜索不协作中断——取消在下一次协程挂起点生效，
     * 最坏等待当前层搜索结束（Hard 由 searchTimed 的时间预算约束上限）。
     */
    fun cancelAiSearch() {
        aiSearchJob?.cancel()
        aiSearchJob = null
        if (_state.value.isAiThinking) {
            _state.value = _state.value.copy(isAiThinking = false)
        }
    }

    /**
 * 计算 AI 落子位置。使用 while 循环确保连续 AI 回合也能执行。
 * 每次搜索完成后落子，然后检查是否仍轮到 AI，如果是则继续搜索。
 * 登记自身协程句柄以便 [cancelAiSearch] 中断；finally 保证状态复位。
 */
suspend fun computeAiMove() {
        aiSearchJob = coroutineContext[Job]
        try {
        while (_state.value.isAiThinking) {
            val s = _state.value
            if (s.gameResult != null) {
                _state.value = s.copy(isAiThinking = false)
                break
            }

            val result = withContext(Dispatchers.Default) {
                val tempBoard = GobangBoard()
                tempBoard.loads("")
                for (i in 0 until 225) {
                    if (s.board[i] != 0) {
                        tempBoard.put(i / 15, i % 15, s.board[i])
                    }
                }
                when (s.difficulty) {
                    Difficulty.Hard -> searcher.searchTimed(
                        tempBoard, s.currentTurn, AI_MAX_DEPTH_HARD, AI_BUDGET_MS_HARD,
                    )
                    else -> searcher.search(tempBoard, s.currentTurn, s.difficulty.depth)
                }
            }

            _state.value = _state.value.copy(isAiThinking = false)

            if (result.row >= 0 && result.col >= 0) {
                applyMove(result.row, result.col, _state.value.currentTurn)
            }

            if (_state.value.gameResult != null || !shouldAiMove(_state.value)) break
        }
        } finally {
            aiSearchJob = null
            if (_state.value.isAiThinking) {
                _state.value = _state.value.copy(isAiThinking = false)
            }
        }
    }

/** 撤销棋步，AI 模式下自动撤销两步（人+AI）。AI 思考期间先取消搜索再撤销 */
    fun undo() {
        cancelAiSearch()
        val s = _state.value
        if (s.gameResult != null) return
        if (s.moveHistory.isEmpty()) return

        val stepsToUndo = when (s.gameMode) {
            GameMode.PvAI -> if (s.moveHistory.size >= 2) 2 else 1
            GameMode.AIvP -> if (s.moveHistory.size >= 2) 2 else 1
            GameMode.AIvAI -> 1
            GameMode.PvP -> 1
        }

        val undoCount = stepsToUndo.coerceAtMost(s.moveHistory.size)
        val undoneMoves = s.moveHistory.takeLast(undoCount)
        val remainingHistory = s.moveHistory.dropLast(undoCount)
        val newBoard = IntArray(225)
        for (move in remainingHistory) {
            newBoard[move.row * 15 + move.col] = move.stone
        }
        val lastTurn = if (remainingHistory.isEmpty()) 1 else {
            if (remainingHistory.last().stone == 1) 2 else 1
        }

        _state.value = s.copy(
            board = newBoard,
            currentTurn = lastTurn,
            moveHistory = remainingHistory,
            undoStack = s.undoStack + undoneMoves,
            gameResult = null,
            wonPositions = emptySet(),
        )
        // 撤销后若仍轮到 AI（如 AIvAI 撤一步），恢复 AI 续走
        if (shouldAiMove(_state.value)) triggerAiMove()
    }

/** 重做被撤销的棋步。AI 思考期间先取消搜索再重做 */
    fun redo() {
        cancelAiSearch()
        val s = _state.value
        if (s.undoStack.isEmpty()) return
        if (s.gameResult != null) return

        val redoCount = when (s.gameMode) {
            GameMode.PvAI -> if (s.undoStack.size >= 2) 2 else 1
            GameMode.AIvP -> if (s.undoStack.size >= 2) 2 else 1
            else -> 1
        }.coerceAtMost(s.undoStack.size)

        val redoMoves = s.undoStack.takeLast(redoCount)
        val remainingUndo = s.undoStack.dropLast(redoCount)
        val newHistory = s.moveHistory + redoMoves
        val newBoard = IntArray(225)
        for (move in newHistory) {
            newBoard[move.row * 15 + move.col] = move.stone
        }
        val lastTurn = if (newHistory.isEmpty()) 1 else {
            if (newHistory.last().stone == 1) 2 else 1
        }

        _state.value = s.copy(
            board = newBoard,
            currentTurn = lastTurn,
            moveHistory = newHistory,
            undoStack = remainingUndo,
        )
        // 重做后若轮到 AI（如 AIvAI），恢复 AI 续走
        if (shouldAiMove(_state.value)) triggerAiMove()
    }

    /** 保存游戏到持久化存储 */
suspend fun saveGame() {
        val repo = repository ?: return
        val s = _state.value
        val savedGame = SavedGame(
            boardState = s.board.joinToString(","),
            currentTurn = s.currentTurn,
            moveHistory = s.moveHistory,
            difficulty = s.difficulty,
            gameMode = s.gameMode,
            savedAt = epochMillis(),
        )
        repo.saveGame(savedGame)
    }

    /** 从持久化存储恢复游戏 */
suspend fun loadGame(): Boolean {
        val repo = repository ?: return false
        val savedGame = repo.loadGame() ?: return false

        board.reset()
        board.loads("")
        for (i in 0 until 225) {
            val stone = savedGame.boardState.split(",").getOrNull(i)?.toIntOrNull() ?: continue
            if (stone != 0) {
                board.put(i / 15, i % 15, stone)
            }
        }

        _state.value = GameState(
            board = savedGame.boardState.split(",").map { it.toIntOrNull() ?: 0 }.toIntArray(),
            currentTurn = savedGame.currentTurn,
            moveHistory = savedGame.moveHistory,
            undoStack = emptyList(),
            gameResult = null,
            wonPositions = emptySet(),
            isAiThinking = false,
            difficulty = savedGame.difficulty,
            gameMode = savedGame.gameMode,
        )
        return true
    }

    /** 清除存档 */
suspend fun clearSave() {
        repository?.clearSave()
    }
}