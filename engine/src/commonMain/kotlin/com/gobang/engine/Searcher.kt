package com.gobang.engine

import kotlin.time.TimeSource

/** AI 搜索结果：评分、行、列 */
data class SearchResult(val score: Int, val row: Int, val col: Int)

/**
 * 五子棋搜索器：Negamax + Alpha-Beta 剪枝 + 压缩候选 + 威胁前置通道。
 *
 * - [search]：固定深度搜索（Easy/Medium 与既有调用方使用），根层候选 top-K。
 * - [searchTimed]：迭代加深 + 时间预算（Hard 使用），层未完成即回退最近完整层。
 * - 递归不再保存实例棋盘状态（boardArr 传参、落子/回溯原位修改），内部节点不再整盘预评估，
 *   落子后以局部五连检测短路终局分支；叶节点才调用评估器。
 */
class GobangSearcher {

    /** 威胁前置通道开关（默认开启）：根节点短路必胜/必堵/双杀构造/反杀预判着 */
    var threatScanEnabled: Boolean = true

    val evaluator = GobangEvaluator()
    private val threatScanner = ThreatScanner()
    private val candidateGen = CandidateGenerator()

    companion object {
        private const val WIN_SCORE = 9999
        private const val INF = 0x7fffffff

        /** 默认单调时钟（毫秒）。测试可注入假时钟。 */
        val defaultClock: () -> Long = {
            TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds
        }

        private fun other(turn: Int): Int = if (turn == BoardConstants.BLACK) BoardConstants.WHITE else BoardConstants.BLACK
    }

    /**
     * 固定深度搜索。先跑威胁通道；高分局面（>8000）以深度 1 重新确认最佳点（与旧行为一致）。
     */
    fun search(boardObj: GobangBoard, turn: Int, depth: Int): SearchResult {
        val size = BoardConstants.BOARD_SIZE
        val copy = boardObj.copyBoard()
        if (threatScanEnabled) {
            val threat = threatScanner.scan(copy, turn)
            if (threat >= 0) return SearchResult(WIN_SCORE, threat / size, threat % size)
        }
        if (depth <= 0) return SearchResult(0, -1, -1)

        var score: Int
        var bestIdx: Int
        val first = rootSearch(copy, turn, depth)
        score = first.first
        bestIdx = first.second
        // 高分局面重新以深度 1 搜索，精确确认最佳着法
        if (kotlin.math.abs(score) > 8000) {
            val confirm = rootSearch(copy, turn, 1)
            if (confirm.second >= 0) {
                score = confirm.first
                bestIdx = confirm.second
            }
        }
        if (bestIdx < 0) return SearchResult(score, -1, -1)
        return SearchResult(score, bestIdx / size, bestIdx % size)
    }

    /**
     * 迭代加深 + 时间预算搜索（Hard）。
     * 根候选一次生成并逐层按上一轮得分重排（move ordering）；
     * 层遍历中发现超时即丢弃该层、回退最近完整层结果；
     * 若连第一层都未完成，回退启发式首候选。
     *
     * @param budgetMs 时间预算（毫秒）；@param clock 当前时间（毫秒），可注入假时钟做确定性测试。
     */
    fun searchTimed(
        boardObj: GobangBoard,
        turn: Int,
        maxDepth: Int,
        budgetMs: Long,
        clock: () -> Long = defaultClock,
    ): SearchResult {
        val size = BoardConstants.BOARD_SIZE
        val copy = boardObj.copyBoard()
        if (threatScanEnabled) {
            val threat = threatScanner.scan(copy, turn)
            if (threat >= 0) return SearchResult(WIN_SCORE, threat / size, threat % size)
        }

        var moves = candidateGen.generate(copy, turn, CandidateGenerator.TOP_K_ROOT).toIntArray()
        if (moves.isEmpty()) return SearchResult(0, -1, -1)

        val deadline = clock() + budgetMs
        val perMove = IntArray(moves.size)
        var bestScore = 0
        var bestIdx = moves[0]
        var anyCompleted = false

        for (d in 2..maxDepth.coerceAtLeast(2)) {
            if (clock() > deadline) break
            var alpha = -INF
            var dBestIdx = -1
            var aborted = false
            for (i in moves.indices) {
                if (clock() > deadline) {
                    aborted = true
                    break
                }
                val idx = moves[i]
                copy[idx] = turn
                val s = if (LineCheck.hasFiveAt(copy, idx, turn)) {
                    WIN_SCORE
                } else {
                    -negamax(copy, other(turn), d - 1, -INF, -alpha)
                }
                copy[idx] = 0
                perMove[i] = s
                if (s > alpha) {
                    alpha = s
                    dBestIdx = idx
                }
            }
            if (aborted) break
            anyCompleted = true
            bestScore = alpha
            bestIdx = dBestIdx
            // 按本层得分降序重排候选（下一层优先搜最佳分支）
            moves = moves.indices.sortedByDescending { perMove[it] }.map { moves[it] }.toIntArray()
        }

        if (bestIdx < 0) {
            // 无完整层（理论：满盘）——与固定深度搜索的空候选语义一致
            if (!anyCompleted) return SearchResult(bestScore, -1, -1)
            return SearchResult(bestScore, -1, -1)
        }
        return SearchResult(bestScore, bestIdx / size, bestIdx % size)
    }

    /** 根层展开：对所有根候选落子并取最大得分，返回 (score, 最佳点索引) */
    private fun rootSearch(boardArr: IntArray, turn: Int, depth: Int): Pair<Int, Int> {
        val moves = candidateGen.generate(boardArr, turn, CandidateGenerator.TOP_K_ROOT)
        var alpha = -INF
        var bestIdx = -1
        for (idx in moves) {
            boardArr[idx] = turn
            val s = if (LineCheck.hasFiveAt(boardArr, idx, turn)) {
                WIN_SCORE
            } else {
                -negamax(boardArr, other(turn), depth - 1, -INF, -alpha)
            }
            boardArr[idx] = 0
            if (s > alpha) {
                alpha = s
                bestIdx = idx
            }
        }
        return alpha to bestIdx
    }

    /**
     * Negamax + Alpha-Beta 递归。boardArr 原位落子/回溯。
     * depth<=0 为叶节点（整盘评估）；内部节点不再整盘预评估，
     * 落子形成五连时以局部检测短路返回 WIN_SCORE。
     */
    private fun negamax(boardArr: IntArray, turn: Int, depth: Int, alpha: Int, beta: Int): Int {
        if (depth <= 0) {
            evaluator.reset()
            return evaluator.evaluateFromBoard(boardArr, turn)
        }
        val moves = candidateGen.generate(boardArr, turn, CandidateGenerator.TOP_K_INNER)
        if (moves.isEmpty()) return 0
        var a = alpha
        for (idx in moves) {
            boardArr[idx] = turn
            val s = if (LineCheck.hasFiveAt(boardArr, idx, turn)) {
                WIN_SCORE
            } else {
                -negamax(boardArr, other(turn), depth - 1, -beta, -a)
            }
            boardArr[idx] = 0
            if (s > a) {
                a = s
                if (a >= beta) break
            }
        }
        return a
    }
}
