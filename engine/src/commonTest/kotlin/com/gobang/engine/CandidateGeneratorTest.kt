package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class CandidateGeneratorTest {

    private val gen = CandidateGenerator()

    private fun board(vararg stones: Triple<Int, Int, Int>): IntArray {
        val b = IntArray(15 * 15)
        for ((s, r, c) in stones) b[r * 15 + c] = s
        return b
    }

    @Test
    fun `empty board falls back to center`() {
        val cands = gen.generate(IntArray(225), BoardConstants.BLACK, CandidateGenerator.TOP_K_ROOT)
        assertTrue(7 * 15 + 7 in cands, "中心 (7,7) 必须在候选内，实际 $cands")
    }

    @Test
    fun `candidates are legal empty cells and bounded by topK`() {
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 8, 4), Triple(2, 8, 5),
        )
        val cands = gen.generate(b, BoardConstants.WHITE, 5)
        assertTrue(cands.size <= 5 && cands.isNotEmpty(), "候选数量应在 (0,5]，实际 ${cands.size}")
        assertTrue(cands.all { b[it] == 0 }, "候选必须是空位")
    }

    @Test
    fun `candidates include my own five-making point`() {
        // 黑四连 (7,3..6) 轮黑：(7,2) 或 (7,7) 落黑即成五
        val b = board(
            Triple(1, 7, 3), Triple(1, 7, 4), Triple(1, 7, 5), Triple(1, 7, 6),
            Triple(2, 5, 5), Triple(2, 9, 5), Triple(2, 6, 9),
        )
        val cands = gen.generate(b, BoardConstants.BLACK, CandidateGenerator.TOP_K_ROOT)
        assertTrue(7 * 15 + 2 in cands || 7 * 15 + 7 in cands,
            "成五点 (7,2)/(7,7) 必须在候选内，实际 ${cands.map { it / 15 to it % 15 }}")
    }

    @Test
    fun `candidates include opponent five-blocking point`() {
        // 白四连 (7,3..6) 轮黑：黑必须能堵 (7,2) 或 (7,7)
        val b = board(
            Triple(2, 7, 3), Triple(2, 7, 4), Triple(2, 7, 5), Triple(2, 7, 6),
            Triple(1, 6, 4), Triple(1, 8, 5),
        )
        val cands = gen.generate(b, BoardConstants.BLACK, CandidateGenerator.TOP_K_ROOT)
        assertTrue(7 * 15 + 2 in cands || 7 * 15 + 7 in cands,
            "必堵点 (7,2)/(7,7) 必须在候选内，实际 ${cands.map { it / 15 to it % 15 }}")
    }

    @Test
    fun `candidates prefer high threat neighborhood over far cells`() {
        // 棋子密集区候选应包含，远处孤立空位不应挤占前几名
        val b = board(
            Triple(1, 7, 5), Triple(1, 7, 6), Triple(1, 7, 7),
            Triple(2, 8, 6), Triple(2, 8, 7), Triple(2, 8, 8),
        )
        val cands = gen.generate(b, BoardConstants.BLACK, CandidateGenerator.TOP_K_ROOT)
        val near = setOf(
            6 * 15 + 5, 6 * 15 + 6, 6 * 15 + 7, 6 * 15 + 8,
            7 * 15 + 4, 7 * 15 + 8,
            8 * 15 + 5, 8 * 15 + 9,
            9 * 15 + 6, 9 * 15 + 7, 9 * 15 + 8,
        )
        assertTrue(cands.take(4).any { it in near }, "前 4 候选应集中在棋型附近，实际 ${cands.take(4).map { it / 15 to it % 15 }}")
    }
}
