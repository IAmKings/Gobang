package com.gobang.viewmodel

import com.gobang.engine.GobangBoard
import com.gobang.engine.GobangSearcher
import com.gobang.engine.OpeningBook
import com.gobang.model.Difficulty
import com.gobang.model.GameMode
import com.gobang.model.GameResult
import com.gobang.model.GameState
import com.gobang.model.Move
import com.gobang.storage.GameStateRepository
import com.gobang.storage.SavedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class GameViewModel(
    private val searcher: GobangSearcher = GobangSearcher(),
    private val repository: GameStateRepository? = null,
) {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val board = GobangBoard()

    fun newGame(mode: GameMode, difficulty: Difficulty) {
        board.reset()
        var initialBoard = IntArray(15 * 15)
        var initialHistory = emptyList<Move>()
        var initialTurn = 1

        if (mode == GameMode.PvAI || mode == GameMode.AIvP) {
            val opening = OpeningBook.randomOpening()
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

    fun handleUserMove(row: Int, col: Int) {
        val s = _state.value
        if (s.isAiThinking) return
        applyMove(row, col, s.currentTurn)
    }

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

    private fun shouldAiMove(s: GameState): Boolean {
        return when (s.gameMode) {
            GameMode.PvAI -> s.currentTurn == 2
            GameMode.AIvP -> s.currentTurn == 1
            GameMode.AIvAI -> true
            GameMode.PvP -> false
        }
    }

    private fun triggerAiMove() {
        _state.value = _state.value.copy(isAiThinking = true)
    }

    suspend fun computeAiMove() {
        val s = _state.value
        if (!s.isAiThinking) return
        if (s.gameResult != null) return

        val result = withContext(Dispatchers.Default) {
            val tempBoard = GobangBoard()
            tempBoard.loads("")
            for (i in 0 until 225) {
                if (s.board[i] != 0) {
                    tempBoard.put(i / 15, i % 15, s.board[i])
                }
            }
            val depth = s.difficulty.depth
            searcher.search(tempBoard, s.currentTurn, depth)
        }

        _state.value = _state.value.copy(isAiThinking = false)

        if (result.row >= 0 && result.col >= 0) {
            applyMove(result.row, result.col, _state.value.currentTurn)
        }
    }

    fun undo() {
        val s = _state.value
        if (s.isAiThinking) return
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
    }

    fun redo() {
        val s = _state.value
        if (s.isAiThinking) return
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
    }

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

    suspend fun clearSave() {
        repository?.clearSave()
    }
}