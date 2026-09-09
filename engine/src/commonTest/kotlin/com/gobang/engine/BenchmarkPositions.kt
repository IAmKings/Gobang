package com.gobang.engine

import kotlin.random.Random

/**
 * S0 基线局面集（固定 seed，确定性生成，保证可复现）。
 *
 * 生成方式：黑先行、首手天元，之后双方在"距已有棋子 ≤2 格的空位"中随机落子，
 * 直到目标子数；若过程中出现五连（概率极低）则丢弃重试同一 seed。
 * 局面覆盖 8 / 12 / 18 / 24 子四档 × 各 2 个，供 P0/P1/P2 各阶段性能与效果对比
 * （对比报告见 .trellis/tasks/09-09-gobang-ai-optimization/benchmark-baseline.md）。
 *
 * 轮次约定：黑先手，偶数子落毕轮到黑(1)，奇数子轮到白(2)。
 */
object BenchmarkPositions {

    data class Position(val name: String, val stones: Int, val turn: Int, val boardText: String)

    fun all(): List<Position> = listOf(
        generate(8, 101L, "mid8a"),
        generate(8, 202L, "mid8b"),
        generate(12, 303L, "mid12a"),
        generate(12, 404L, "mid12b"),
        generate(18, 505L, "mid18a"),
        generate(18, 606L, "mid18b"),
        generate(24, 707L, "mid24a"),
        generate(24, 808L, "mid24b"),
    )

    fun generate(stones: Int, seed: Long, name: String): Position {
        while (true) {
            val board = tryGenerate(stones, seed) ?: continue
            return Position(name, stones, if (stones % 2 == 0) BoardConstants.BLACK else BoardConstants.WHITE, board)
        }
    }

    private fun tryGenerate(stones: Int, seed: Long): String? {
        val size = BoardConstants.BOARD_SIZE
        val board = IntArray(size * size)
        val rnd = Random(seed)
        board[7 * size + 7] = BoardConstants.BLACK
        var turn = BoardConstants.WHITE
        var placed = 1
        while (placed < stones) {
            val candidates = neighborEmpty(board, 2)
            if (candidates.isEmpty()) return null
            val c = candidates[rnd.nextInt(candidates.size)]
            board[c] = turn
            placed++
            if (hasFive(board, c, turn)) return null
            turn = if (turn == BoardConstants.BLACK) BoardConstants.WHITE else BoardConstants.BLACK
        }
        return boardToText(board)
    }

    /** 距任一非空位 ≤radius 的空位集合（行优先索引） */
    private fun neighborEmpty(board: IntArray, radius: Int): List<Int> {
        val size = BoardConstants.BOARD_SIZE
        val result = mutableListOf<Int>()
        val seen = BooleanArray(size * size)
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

    /** 自 (r,c) 出发四方向同色连子 ≥5 即视为五连 */
    private fun hasFive(board: IntArray, idx: Int, stone: Int): Boolean {
        val size = BoardConstants.BOARD_SIZE
        val r = idx / size
        val c = idx % size
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for ((dr, dc) in dirs) {
            var count = 1
            for (sign in listOf(1, -1)) {
                var nr = r + sign * dr
                var nc = c + sign * dc
                while (nr in 0 until size && nc in 0 until size && board[nr * size + nc] == stone) {
                    count++
                    nr += sign * dr
                    nc += sign * dc
                }
            }
            if (count >= 5) return true
        }
        return false
    }

    private fun boardToText(board: IntArray): String {
        val size = BoardConstants.BOARD_SIZE
        val sb = StringBuilder()
        for (i in 0 until size * size) {
            if (board[i] != 0) {
                sb.append(board[i]).append(':')
                    .append(('A'.code + i / size).toChar())
                    .append(('A'.code + i % size).toChar())
                    .append(' ')
            }
        }
        return sb.toString().trim()
    }
}
