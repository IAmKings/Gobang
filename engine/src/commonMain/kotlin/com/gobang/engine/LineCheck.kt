package com.gobang.engine

/**
 * 棋盘局部线型检测工具：五连判定与单点威胁估值。
 * 供 ThreatScanner（威胁前置通道）与 Searcher（落子后提前终止）复用。
 */
object LineCheck {

    /**
     * 空位 idx 假设落下 stone 后，是否沿四方向形成 ≥5 连。
     * 只读棋盘（board[idx] 本身为空位，count 从 1 起算）。
     */
    fun hasFiveAt(board: IntArray, idx: Int, stone: Int): Boolean {
        val size = BoardConstants.BOARD_SIZE
        val r = idx / size
        val c = idx % size
        for ((dr, dc) in GobangBoard.DIRS) {
            var count = 1
            var nr = r + dr
            var nc = c + dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr += dr
                nc += dc
            }
            nr = r - dr
            nc = c - dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr -= dr
                nc -= dc
            }
            if (count >= 5) return true
        }
        return false
    }

    /**
     * 空位 idx 假设落下 stone 后的威胁估值，用于双杀（双活三/四三）预判。
     * 沿四方向统计 (同色连子数 count, 开放端数 open)：同色延伸遇到空格记 open=1 并停止，
     * 遇到异色/边界即停止（保守估计）。
     * 返回：任一方位形成活四（count==4 且 open==2）→ 大常数；否则返回
     * Σ(方向威胁)：冲四(count==4 且 open==1)计 2、活三(count==3 且 open==2)计 1。
     */
    fun threatSum(board: IntArray, idx: Int, stone: Int): Int {
        val size = BoardConstants.BOARD_SIZE
        val r = idx / size
        val c = idx % size
        var sum = 0
        for ((dr, dc) in GobangBoard.DIRS) {
            var count = 1
            var open = 0
            var nr = r + dr
            var nc = c + dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr += dr
                nc += dc
            }
            if (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == 0) open++
            nr = r - dr
            nc = c - dc
            while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                count++
                nr -= dr
                nc -= dc
            }
            if (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == 0) open++

            when {
                count == 4 && open == 2 -> return BIG_THREAT   // 活四：下一手必胜
                count == 4 && open == 1 -> sum += 2            // 冲四
                count == 3 && open == 2 -> sum += 1            // 活三
            }
        }
        return sum
    }

    /** 活四级威胁（视为必胜，大于任何双杀阈值） */
    const val BIG_THREAT = 100
}
