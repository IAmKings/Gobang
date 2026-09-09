package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreatScannerTest {

    private val scanner = ThreatScanner()

    private fun board(vararg stones: Triple<Int, Int, Int>): IntArray {
        val b = IntArray(15 * 15)
        for ((s, r, c) in stones) b[r * 15 + c] = s
        return b
    }

    @Test
    fun `winning move takes priority`() {
        // 黑四连 (7,3..6)，轮黑：(7,2) 或 (7,7) 落黑即成五
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 3, 7), Triple(2, 4, 7), Triple(2, 5, 7), Triple(2, 6, 7),
        )
        val m = scanner.scan(b, BoardConstants.BLACK)
        assertEquals(7 * 15 + 2, m, "应走出成五 (7,2)")
    }

    @Test
    fun `opponent five must be blocked`() {
        // 白四连 (7,3..6)，轮黑：必堵 (7,2) 或 (7,7)
        val b = board(
            Triple(2, 7, 3), Triple(2, 7, 4), Triple(2, 7, 5), Triple(2, 7, 6),
            Triple(1, 4, 4), Triple(1, 5, 4), Triple(1, 6, 4),
        )
        val m = scanner.scan(b, BoardConstants.BLACK)
        assertEquals(7 * 15 + 2, m, "应堵白 (7,2)")
    }

    @Test
    fun `own double-threat point is preferred`() {
        // 黑 (7,5)(7,7) 与 (5,6)(6,6)：落 P=(7,6) 形成横活三 + 竖活三（双活三）
        val b = board(
            Triple(1, 7, 5), Triple(1, 7, 7), Triple(1, 5, 6), Triple(1, 6, 6),
            Triple(2, 9, 5), Triple(2, 9, 6), Triple(2, 9, 7),
        )
        val m = scanner.scan(b, BoardConstants.BLACK)
        assertEquals(7 * 15 + 6, m, "应选择双杀构造点 (7,6)，实际 ${m / 15},${m % 15}")
    }

    @Test
    fun `opponent double-threat point is occupied`() {
        // 白具备同形双活三构造点 (7,6)，轮黑：黑应占 (7,6) 破坏
        val b = board(
            Triple(2, 7, 5), Triple(2, 7, 7), Triple(2, 5, 6), Triple(2, 6, 6),
            Triple(1, 3, 3), Triple(1, 3, 4), Triple(1, 4, 3),
        )
        val m = scanner.scan(b, BoardConstants.BLACK)
        assertEquals(7 * 15 + 6, m, "应抢占对方双杀点 (7,6)，实际 ${m / 15},${m % 15}")
    }

    @Test
    fun `no threat returns -1`() {
        val b = board(Triple(1, 7, 7), Triple(2, 8, 8))
        assertEquals(-1, scanner.scan(b, BoardConstants.BLACK), "零星局面无威胁短路")
    }
}
