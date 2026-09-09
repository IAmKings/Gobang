package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 战术局面集成测试：经公开搜索入口（search / searchTimed）验证引擎能
 * 正确走出"成五、必堵、冲四取胜、双杀构造/防守"等战术要点。
 * 全部局面手工构造、确定性断言，不依赖随机局面。
 */
class TacticalSearchTest {

    private val searcher = GobangSearcher()

    private fun board(vararg stones: Triple<Int, Int, Int>): GobangBoard {
        val b = GobangBoard()
        for ((s, r, c) in stones) b.put(r, c, s)
        return b
    }

    @Test
    fun `empty board opens near center`() {
        val r = searcher.search(GobangBoard(), BoardConstants.BLACK, 2)
        val center = setOf(
            7 to 7, 6 to 7, 7 to 6, 8 to 7, 7 to 8,
            6 to 6, 6 to 8, 8 to 6, 8 to 8,
        )
        assertTrue(r.row to r.col in center, "空盘首着应居中区域，实际 (${r.row},${r.col})")
    }

    @Test
    fun `makes five from own open four`() {
        // 黑四连 (7,3..6)，两端空，轮黑：补 (7,2) 或 (7,7) 成五
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 3, 8), Triple(2, 4, 8), Triple(2, 5, 8), Triple(2, 6, 8),
        )
        val r = searcher.search(b, BoardConstants.BLACK, 1)
        assertTrue(r.row == 7 && (r.col == 2 || r.col == 7), "应走出成五，实际 (${r.row},${r.col})")
    }

    @Test
    fun `blocks opponent open four`() {
        // 白四连 (7,3..6)，两端空，轮黑：必堵 (7,2)/(7,7)
        val b = board(
            Triple(2, 7, 3), Triple(2, 7, 4), Triple(2, 7, 5), Triple(2, 7, 6),
            Triple(1, 4, 4), Triple(1, 5, 4), Triple(1, 6, 4),
        )
        val r = searcher.search(b, BoardConstants.BLACK, 1)
        assertTrue(r.row == 7 && (r.col == 2 || r.col == 7), "应堵对方活四，实际 (${r.row},${r.col})")
    }

    @Test
    fun `wins through only open end of own four`() {
        // 黑四连 (7,3..6)，(7,7) 被白占，轮黑：只能在 (7,2) 成五
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 7, 7),
        )
        val r = searcher.search(b, BoardConstants.BLACK, 1)
        assertTrue(r.row == 7 && r.col == 2, "应走唯一成五点 (7,2)，实际 (${r.row},${r.col})")
    }

    @Test
    fun `blocks only open end of opponent four`() {
        // 白四连 (7,4..7)，(7,8) 被黑堵，轮黑：必须堵唯一成五点 (7,3)
        val b = board(
            Triple(2, 7, 4), Triple(2, 7, 5), Triple(2, 7, 6), Triple(2, 7, 7),
            Triple(1, 7, 8),
        )
        val r = searcher.search(b, BoardConstants.BLACK, 1)
        assertTrue(r.row == 7 && r.col == 3, "应堵对方冲四唯一空端 (7,3)，实际 (${r.row},${r.col})")
    }

    @Test
    fun `hard timed search constructs double threat`() {
        // 黑 (7,5)(7,7) 与 (5,6)(6,6)，轮黑：落 (7,6) 形成横+竖双活三（双杀构造）
        val b = board(
            Triple(1, 7, 5), Triple(1, 7, 7), Triple(1, 5, 6), Triple(1, 6, 6),
            Triple(2, 9, 5), Triple(2, 9, 6), Triple(2, 9, 7),
        )
        val r = searcher.searchTimed(b, BoardConstants.BLACK, 6, 1000L)
        assertTrue(r.row == 7 && r.col == 6, "Hard timed 应构造双杀点 (7,6)，实际 (${r.row},${r.col})")
    }

    @Test
    fun `hard timed search occupies opponent double threat point`() {
        // 白具备双活三构造点 (7,6)，轮黑：黑应占 (7,6) 破坏对方双杀
        val b = board(
            Triple(2, 7, 5), Triple(2, 7, 7), Triple(2, 5, 6), Triple(2, 6, 6),
            Triple(1, 3, 3), Triple(1, 3, 4), Triple(1, 4, 3),
        )
        val r = searcher.searchTimed(b, BoardConstants.BLACK, 6, 1000L)
        assertTrue(r.row == 7 && r.col == 6, "Hard timed 应抢占对方双杀点 (7,6)，实际 (${r.row},${r.col})")
    }
}
