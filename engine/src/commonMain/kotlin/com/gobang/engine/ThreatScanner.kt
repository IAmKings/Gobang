package com.gobang.engine

/**
 * 威胁前置通道：在根节点搜索前按固定优先级短路返回"必走着"。
 * ① 本方一步成五 → 直接赢；
 * ② 对方一步成五 → 必堵；
 * ③ 本方存在双杀构造点（双活三/四三/活四）→ 抢先形成必胜组合；
 * ④ 对方存在双杀构造点 → 落子占位破坏。
 *
 * 输出为合法空位索引；无可走威胁返回 -1。仅根节点调用一次，不进入递归。
 */
class ThreatScanner {

    /** 返回优先落点索引；无威胁可走返回 -1 */
    fun scan(board: IntArray, me: Int): Int {
        val opp = if (me == BoardConstants.BLACK) BoardConstants.WHITE else BoardConstants.BLACK
        findFive(board, me)?.let { return it }
        findFive(board, opp)?.let { return it }
        bestDoubleThreat(board, me)?.let { return it }
        bestDoubleThreat(board, opp)?.let { return it }
        return -1
    }

    /** 一步可成五的空位（遍历全部空位，O(225×局部)，根节点一次性可接受） */
    private fun findFive(board: IntArray, stone: Int): Int? {
        for (idx in board.indices) {
            if (board[idx] == 0 && LineCheck.hasFiveAt(board, idx, stone)) return idx
        }
        return null
    }

    /** 落子后威胁 ≥ 双杀阈值（2）的点中估值最高者 */
    private fun bestDoubleThreat(board: IntArray, stone: Int): Int? {
        var bestIdx = -1
        var bestScore = 1
        for (idx in board.indices) {
            if (board[idx] != 0) continue
            val t = LineCheck.threatSum(board, idx, stone)
            if (t > bestScore) {
                bestScore = t
                bestIdx = idx
            }
        }
        return if (bestIdx >= 0) bestIdx else null
    }
}
