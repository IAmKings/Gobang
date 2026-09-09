package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTimedTest {

    private val searcher = GobangSearcher()

    /** 关闭威胁通道的搜索器：纯搜索路径（迭代加深/超时回退）用例使用，避免随机局面触发威胁短路 */
    private fun pureSearcher() = GobangSearcher().apply { threatScanEnabled = false }

    private fun board(vararg stones: Triple<Int, Int, Int>): GobangBoard {
        val b = GobangBoard()
        for ((s, r, c) in stones) b.put(r, c, s)
        return b
    }

    @Test
    fun `timed search completes to max depth when budget is ample`() {
        var now = 0L
        val pos = BenchmarkPositions.all().first()
        val b = GobangBoard().apply { loads(pos.boardText) }
        val r = pureSearcher().searchTimed(b, pos.turn, 4, 60_000L) { now }
        assertTrue(r.row in 0..14 && r.col in 0..14, "应返回合法落点，实际 (${r.row},${r.col})")
    }

    @Test
    fun `timed search falls back to heuristic first move on deadline`() {
        var now = 0L
        val pos = BenchmarkPositions.all().first()
        val b = GobangBoard().apply { loads(pos.boardText) }
        // 假时钟每次调用 +5ms，预算 10ms → 第一层展开中途即超时，回退首候选
        val r = pureSearcher().searchTimed(b, pos.turn, 6, 10L) { now += 5; now }
        assertTrue(r.row in 0..14 && r.col in 0..14, "超时也应返回合法回退点，实际 (${r.row},${r.col})")
        assertEquals(0, r.score, "回退路径 score 应为 0")
    }

    @Test
    fun `timed search blocks immediate opponent five via threat pass`() {
        var now = 0L
        // 白四连 (7,3..6)，轮黑 timed：必堵 (7,2) 或 (7,7)
        val b = board(
            Triple(2, 7, 3), Triple(2, 7, 4), Triple(2, 7, 5), Triple(2, 7, 6),
            Triple(1, 4, 4), Triple(1, 5, 4), Triple(1, 6, 4),
        )
        val r = searcher.searchTimed(b, BoardConstants.BLACK, 4, 60_000L) { now }
        assertTrue(
            (r.row == 7 && (r.col == 2 || r.col == 7)),
            "应堵白 (7,2)/(7,7)，实际 (${r.row},${r.col})",
        )
    }

    @Test
    fun `timed search returns winning five move`() {
        var now = 0L
        // 黑四连 (7,3..6)，轮黑：成五 (7,2) 或 (7,7)
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 3, 7), Triple(2, 4, 7), Triple(2, 5, 7), Triple(2, 6, 7),
        )
        val r = searcher.searchTimed(b, BoardConstants.BLACK, 4, 60_000L) { now }
        assertTrue(
            (r.row == 7 && (r.col == 2 || r.col == 7)),
            "应走出成五 (7,2)/(7,7)，实际 (${r.row},${r.col})",
        )
    }
}
