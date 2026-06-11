package com.gobang.engine

/** AI 搜索结果：评分、行、列 */
data class SearchResult(val score: Int, val row: Int, val col: Int)

/**
 * 五子棋搜索器，使用 Negamax + Alpha-Beta 剪枝算法。
 * 基于位置权重生成候选着法，按优先级排序后搜索。
 * 对高分/低分局面会重新以深度 1 搜索以精确确认。
 */
class GobangSearcher {

    val evaluator = GobangEvaluator()
    private lateinit var board: IntArray
    private var bestMove: Pair<Int, Int>? = null
    private var maxDepth = 3

    /** 生成所有空位作为候选着法，按位置权重从高到低排序 */
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

    /** Negamax + Alpha-Beta 剪枝递归搜索 */
    private fun searchInternal(turn: Int, depth: Int, alpha: Int, beta: Int): Int {
        // 叶节点：评估当前局面
        if (depth <= 0) {
            evaluator.reset()
            return evaluator.evaluateFromBoard(board, turn)
        }

        // 当前局面评估，若已经必胜/必败则提前返回
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
                    break  // Beta 剪枝
                }
            }
        }

        // 仅在根节点记录最佳着法
        if (depth == maxDepth && localBestMove != null) {
            bestMove = localBestMove
        }

        return localAlpha
    }

    /** 搜索入口：复制棋盘并开始 Negamax 搜索 */
    fun search(boardObj: GobangBoard, turn: Int, depth: Int): SearchResult {
        this.board = boardObj.copyBoard()
        maxDepth = depth
        bestMove = null
        var score = searchInternal(turn, depth, -0x7fffffff, 0x7fffffff)
        // 高分局面重新以深度 1 搜索，精确确认结果
        if (kotlin.math.abs(score) > 8000) {
            maxDepth = depth
            score = searchInternal(turn, 1, -0x7fffffff, 0x7fffffff)
        }
        val move = bestMove ?: return SearchResult(score, -1, -1)
        return SearchResult(score, move.first, move.second)
    }
}