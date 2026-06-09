package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GobangEvaluatorTest {

    private val evaluator = GobangEvaluator()

    @Test
    fun `POS matrix has center bias`() {
        assertEquals(7, evaluator.POS[7][7])
        assertEquals(0, evaluator.POS[0][0])
        assertEquals(0, evaluator.POS[14][14])
        assertEquals(1, evaluator.POS[1][1])
    }

    @Test
    fun evaluateReturnsHighScoreForBlackFive() {
        val board = GobangBoard()
        board.loads("1:HD 1:HE 1:HF 1:HG 1:HH")
        val score = evaluator.evaluate(board, BoardConstants.BLACK)
        assertTrue(score > 9000, "Expected score > 9000 for black five, got $score")
    }

    @Test
    fun evaluateReturnsNegativeWhenOpponentHasFive() {
        val board = GobangBoard()
        board.loads("1:HD 1:HE 1:HF 1:HG 1:HH")
        val score = evaluator.evaluate(board, BoardConstants.WHITE)
        assertTrue(score < -9000, "Expected score < -9000 for opponent five, got $score")
    }

    @Test
    fun `evaluate returns positive for black four`() {
        val board = GobangBoard()
        board.loads("1:HE 1:HF 1:HG 1:HH")
        val score = evaluator.evaluate(board, BoardConstants.BLACK)
        assertTrue(score > 9000, "Expected score > 9000 for four in a row, got $score")
    }

    @Test
    fun `evaluate on empty board returns zero`() {
        val board = GobangBoard()
        val score = evaluator.evaluate(board, BoardConstants.BLACK)
        assertEquals(0, score)
    }

    @Test
    fun `analysisLine detects FIVE`() {
        val line = IntArray(30) { 0xf }
        line[0] = 1
        line[1] = 1
        line[2] = 1
        line[3] = 1
        line[4] = 1
        val record = IntArray(30)
        val result = evaluator.analysisLine(line, record, 5, 2)
        assertEquals(GobangEvaluator.FIVE, result)
    }

    @Test
    fun `analysisLine detects FOUR (open four)`() {
        val line = IntArray(30) { 0xf }
        line[0] = 0
        line[1] = 0
        line[2] = 1
        line[3] = 1
        line[4] = 1
        line[5] = 1
        line[6] = 0
        line[7] = 0
        val record = IntArray(30)
        val result = evaluator.analysisLine(line, record, 8, 4)
        assertEquals(GobangEvaluator.FOUR, result)
    }

    @Test
    fun `analysisLine detects SFOUR (blocked four)`() {
        val line = IntArray(30) { 0xf }
        line[0] = 2
        line[1] = 1
        line[2] = 1
        line[3] = 1
        line[4] = 1
        line[5] = 0
        val record = IntArray(30)
        val result = evaluator.analysisLine(line, record, 6, 3)
        assertEquals(GobangEvaluator.SFOUR, result)
    }
}