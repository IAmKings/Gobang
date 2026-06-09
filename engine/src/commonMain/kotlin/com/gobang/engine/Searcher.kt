package com.gobang.engine

data class SearchResult(val score: Int, val row: Int, val col: Int)

class GobangSearcher {

    val evaluator = GobangEvaluator()
    private lateinit var board: IntArray
    private var bestMove: Pair<Int, Int>? = null
    private var maxDepth = 3

    private fun genmove(turn: Int): List<Triple<Int, Int, Int>> {
        val SIZE = BoardConstants.BOARD_SIZE
        val moves = mutableListOf<Triple<Int, Int, Int>>()
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                if (board[i * SIZE + j] == 0) {
                    val score = evaluator.POS[i][j]
                    moves.add(Triple(score, i, j))
                }
            }
        }
        moves.sortByDescending { it.first }
        return moves
    }

    private fun searchInternal(turn: Int, depth: Int, alpha: Int, beta: Int): Int {
        if (depth <= 0) {
            evaluator.reset()
            return evaluator.evaluateFromBoard(board, turn)
        }

        evaluator.reset()
        var score = evaluator.evaluateFromBoard(board, turn)
        if (kotlin.math.abs(score) >= 9999 && depth < maxDepth) {
            return score
        }

        val moves = genmove(turn)
        var localAlpha = alpha
        var localBestMove: Pair<Int, Int>? = null

        for ((_, row, col) in moves) {
            val SIZE = BoardConstants.BOARD_SIZE
            board[row * SIZE + col] = turn
            val nturn = if (turn == 1) 2 else 1
            score = -searchInternal(nturn, depth - 1, -beta, -localAlpha)
            board[row * SIZE + col] = 0

            if (score > localAlpha) {
                localAlpha = score
                localBestMove = row to col
                if (localAlpha >= beta) {
                    break
                }
            }
        }

        if (depth == maxDepth && localBestMove != null) {
            bestMove = localBestMove
        }

        return localAlpha
    }

    fun search(boardObj: GobangBoard, turn: Int, depth: Int): SearchResult {
        this.board = boardObj.copyBoard()
        maxDepth = depth
        bestMove = null
        var score = searchInternal(turn, depth, -0x7fffffff, 0x7fffffff)
        if (kotlin.math.abs(score) > 8000) {
            maxDepth = depth
            score = searchInternal(turn, 1, -0x7fffffff, 0x7fffffff)
        }
        val move = bestMove ?: return SearchResult(score, -1, -1)
        return SearchResult(score, move.first, move.second)
    }
}