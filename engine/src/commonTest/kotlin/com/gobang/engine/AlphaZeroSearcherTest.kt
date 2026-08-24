package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaZeroSearcherTest {
    private class UniformPredictor : PolicyValuePredictor {
        override val modelId: String = "test-uniform"

        override fun predict(canonicalBoard: FloatArray): PolicyValue {
            assertEquals(AlphaZeroBoard.ACTION_SIZE, canonicalBoard.size)
            return PolicyValue(FloatArray(AlphaZeroBoard.ACTION_SIZE) { 1f }, 0f)
        }
    }

    @Test
    fun `action mapping and canonical form are stable`() {
        assertEquals(0, AlphaZeroBoard.actionOf(0, 0))
        assertEquals(224, AlphaZeroBoard.actionOf(14, 14))
        assertEquals(7, AlphaZeroBoard.rowOf(112))
        assertEquals(7, AlphaZeroBoard.colOf(112))

        val board = GobangBoard()
        board.put(7, 7, BoardConstants.WHITE)
        board.put(7, 8, BoardConstants.BLACK)
        val values = AlphaZeroBoard(board, BoardConstants.WHITE).canonicalValues()
        assertEquals(1f, values[7 * 15 + 7])
        assertEquals(-1f, values[7 * 15 + 8])
        assertEquals(0f, values[0])
    }

    @Test
    fun `make and unmake restore board and hash`() {
        val board = AlphaZeroBoard(GobangBoard(), BoardConstants.BLACK)
        val initialHash = board.stableHash()
        val move = board.makeMove(AlphaZeroBoard.actionOf(7, 7))
        assertEquals(BoardConstants.BLACK, board.valueAt(112))
        assertEquals(BoardConstants.WHITE, board.playerToMove)
        board.unmakeMove(move)
        assertEquals(BoardConstants.EMPTY, board.valueAt(112))
        assertEquals(BoardConstants.BLACK, board.playerToMove)
        assertEquals(initialHash, board.stableHash())
    }

    @Test
    fun `tactical solver finds win and forced block`() {
        val board = GobangBoard()
        for (col in 3..6) board.put(7, col, BoardConstants.BLACK)
        val blackToMove = AlphaZeroBoard(board, BoardConstants.BLACK)
        assertTrue(7 * 15 + 2 in TacticalSolver().immediateWins(blackToMove))

        val whiteToMove = AlphaZeroBoard(board, BoardConstants.WHITE)
        assertTrue(7 * 15 + 2 in TacticalSolver().forcedBlocks(whiteToMove))
    }

    @Test
    fun `search returns tactical win before neural search`() {
        val board = GobangBoard()
        for (col in 3..6) board.put(7, col, BoardConstants.BLACK)
        val searcher = AlphaZeroSearcher(
            predictor = UniformPredictor(),
            config = AlphaZeroSearchConfig(simulations = 8),
        )
        val result = searcher.search(board, BoardConstants.BLACK)
        assertEquals(7, result.row)
        assertTrue(result.col == 2 || result.col == 7)
        assertEquals(0, searcher.nodeCount)
    }

    @Test
    fun `search uses legal candidate and can reuse root`() {
        val board = GobangBoard()
        board.put(7, 7, BoardConstants.BLACK)
        val searcher = AlphaZeroSearcher(
            predictor = UniformPredictor(),
            config = AlphaZeroSearchConfig(simulations = 4, candidateLimit = 8),
        )
        val first = searcher.search(board, BoardConstants.WHITE)
        assertTrue(first.row in 0..14 && first.col in 0..14)
        assertFalse(board.get(first.row, first.col) != BoardConstants.EMPTY)

        val next = AlphaZeroBoard(board, BoardConstants.WHITE)
        val firstAction = AlphaZeroBoard.actionOf(first.row, first.col)
        next.makeMove(firstAction)
        assertTrue(searcher.advanceRoot(firstAction))
        searcher.search(next)
        assertTrue(searcher.lastSearchReusedTree)
    }

    @Test
    fun `composite search falls back when model search is unavailable`() {
        val board = GobangBoard()
        val result = CompositeSearcher(alphaZero = null).search(board, BoardConstants.BLACK)
        assertTrue(result.row in 0..14 && result.col in 0..14)
    }
}
