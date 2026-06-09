package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class GoldenMasterEvaluationTest {

    private val evaluator = GobangEvaluator()

    @Test
    fun `evaluate gives black advantage when black has more stones`() {
        val board = GobangBoard()
        board.loads("1:HH 1:HG 1:HF")
        val score = evaluator.evaluate(board, BoardConstants.BLACK)
        assertTrue(score > 0, "Black should have positive score with three stones, got $score")
    }

    @Test
    fun `evaluate gives white advantage when white has more stones`() {
        val board = GobangBoard()
        board.loads("1:HH 2:II 2:IH 2:IG")
        val score = evaluator.evaluate(board, BoardConstants.WHITE)
        assertTrue(score > 0, "White should have positive score, got $score")
    }

    @Test
    fun `evaluate returns 9999 for black five in a row`() {
        val board = GobangBoard()
        board.loads("1:HD 1:HE 1:HF 1:HG 1:HH")
        val score = evaluator.evaluate(board, BoardConstants.BLACK)
        assertTrue(score >= 9999, "Expected score >= 9999 for black five, got $score")
    }

    @Test
    fun `evaluate returns -9999 for opponent five in a row`() {
        val board = GobangBoard()
        board.loads("1:HD 1:HE 1:HF 1:HG 1:HH")
        val score = evaluator.evaluate(board, BoardConstants.WHITE)
        assertTrue(score <= -9999, "Expected score <= -9999 for opponent five, got $score")
    }

    @Test
    fun `center positions have higher position weight`() {
        val centerWeight = evaluator.POS[7][7]
        val cornerWeight = evaluator.POS[0][0]
        assertTrue(centerWeight > cornerWeight,
            "Center weight ($centerWeight) should be greater than corner ($cornerWeight)")
    }

    @Test
    fun `evaluate on symmetric position gives different scores for different turns`() {
        val board = GobangBoard()
        board.loads("1:HH 2:II")
        val blackScore = evaluator.evaluate(board, BoardConstants.BLACK)
        val whiteScore = evaluator.evaluate(board, BoardConstants.WHITE)
        assertTrue(
            blackScore != whiteScore,
            "Scores should differ based on turn: black=$blackScore, white=$whiteScore"
        )
    }
}