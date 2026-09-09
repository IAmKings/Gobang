package com.gobang.engine

/**
 * 候选着法生成器：邻居过滤 + 攻防启发评分 + top-K 截断。
 *
 * 取代旧 `GobangSearcher.genmove()` 的"全盘 225 空位展开"，把每层分支因子
 * 从 O(225) 压缩到 topK（默认 12–16）。排序分值仅用于排序与截断，
 * 不进入 Negamax 的 alpha-beta 数值（不影响评估语义）。
 *
 * 正确性底线：必胜/必防点由 ThreatScanner 在根节点先行处理；
 * 本生成器只保证"候选覆盖高威胁点"（有白箱测试守护）。
 */
class CandidateGenerator {

    companion object {
        const val RADIUS = 2            // 邻居过滤半径（曼哈顿正方形窗口）
        const val TOP_K_ROOT = 16       // 根节点候选上限
        const val TOP_K_INNER = 12      // 内部节点候选上限
        const val DEFEND_K = 2          // 防守价值系数（排序用）
    }

    /** 生成排序后的候选空位（行优先索引），至多 topK 个；空盘回退中心权重排序 */
    fun generate(board: IntArray, turn: Int, topK: Int): List<Int> {
        val size = BoardConstants.BOARD_SIZE
        val empties = neighborEmpties(board, RADIUS)
        if (empties.isEmpty()) {
            // 空盘：按位置权重取中心附近（与旧行为一致：POS 最高者优先）
            return board.indices
                .filter { board[it] == 0 }
                .sortedByDescending { evaluator.POS[it / size][it % size] }
                .take(topK)
        }
        val opp = if (turn == BoardConstants.BLACK) BoardConstants.WHITE else BoardConstants.BLACK
        val scored = empties.map { idx ->
            idx to (
                patternScore(board, idx, turn) * 10L +
                    DEFEND_K * patternScore(board, idx, opp) +
                    centerBonus(idx)
                )
        }
        return scored.sortedByDescending { it.second }.take(topK).map { it.first }
    }

    private val evaluator = GobangEvaluator()

    /** 距任一非空位 ≤radius 的空位（集合去重，行优先） */
    private fun neighborEmpties(board: IntArray, radius: Int): List<Int> {
        val size = BoardConstants.BOARD_SIZE
        val seen = BooleanArray(size * size)
        val result = mutableListOf<Int>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (board[r * size + c] == 0) continue
                for (dr in -radius..radius) {
                    for (dc in -radius..radius) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr !in 0 until size || nc !in 0 until size) continue
                        val idx = nr * size + nc
                        if (board[idx] == 0 && !seen[idx]) {
                            seen[idx] = true
                            result.add(idx)
                        }
                    }
                }
            }
        }
        return result
    }

    /** 中心位置加分（曼哈顿距离倒数），鼓励抢占中腹 */
    private fun centerBonus(idx: Int): Long {
        val size = BoardConstants.BOARD_SIZE
        val r = idx / size
        val c = idx % size
        return (14 - (kotlin.math.abs(r - 7) + kotlin.math.abs(c - 7))).toLong()
    }

    /**
     * 模拟 stone 落在空位 idx 后的威胁价值：沿四方向统计"连子数 + 开放端"，
     * 按棋型映射求和。候选是空位，因此以"假设落子"计 count 起点 1。
     */
    private fun patternScore(board: IntArray, idx: Int, stone: Int): Long {
        val size = BoardConstants.BOARD_SIZE
        val r = idx / size
        val c = idx % size
        var total = 0L
        for ((dr, dc) in GobangBoard.DIRS) {
            var count = 1
            var openBefore = 0
            var openAfter = 0
            // 正向
            var nr = r + dr
            var nc = c + dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr += dr
                nc += dc
            }
            if (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == 0) openAfter = 1
            // 反向
            nr = r - dr
            nc = c - dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr -= dr
                nc -= dc
            }
            if (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == 0) openBefore = 1

            val open = openBefore + openAfter
            total += when {
                count >= 5 -> 10_000_000L          // 落子即成五（由威胁通道负责，排序给最高分兜底）
                count == 4 && open == 2 -> 500_000L // 活四
                count == 4 && open == 1 -> 50_000L  // 冲四
                count == 3 && open == 2 -> 8_000L   // 活三
                count == 3 && open == 1 -> 1_500L   // 眠三
                count == 2 && open == 2 -> 300L     // 活二
                count == 2 && open == 1 -> 60L      // 眠二
                count == 1 && open >= 1 -> 5L       // 单子
                else -> 0L
            }
        }
        return total
    }
}
