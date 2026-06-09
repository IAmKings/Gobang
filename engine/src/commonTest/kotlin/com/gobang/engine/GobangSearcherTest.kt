package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GobangSearcherTest {

    private val searcher = GobangSearcher()

    @Test
    fun `search selects center on empty board`() {
        val board = GobangBoard()
        val result = searcher.search(board, BoardConstants.BLACK, 1)
        val centerMoves = listOf(
            Pair(7, 7), Pair(6, 7), Pair(7, 6), Pair(8, 7), Pair(7, 8),
            Pair(6, 6), Pair(6, 8), Pair(8, 6), Pair(8, 8)
        )
        assertTrue(Pair(result.row, result.col) in centerMoves,
            "Expected center move, got (${result.row}, ${result.col})")
    }

    @Test
    fun `search blocks opponent winning move`() {
        val board = GobangBoard()
        for (col in 3..6) {
            board.put(7, col, BoardConstants.WHITE)
        }
        val result = searcher.search(board, BoardConstants.BLACK, 2)
        val blockingMove = Pair(result.row, result.col)
        assertTrue(
            blockingMove == Pair(7, 7) || blockingMove == Pair(7, 2),
            "Expected blocking move at (7,7) or (7,2), got (${result.row}, ${result.col})"
        )
    }

    @Test
    fun `search finds winning move`() {
        val board = GobangBoard()
        for (col in 3..6) {
            board.put(7, col, BoardConstants.BLACK)
        }
        val result = searcher.search(board, BoardConstants.BLACK, 2)
        assertTrue(
            result.row == 7 && (result.col == 2 || result.col == 7),
            "Expected winning move at row 7, got (${result.row}, ${result.col})"
        )
    }

    @Test
    fun `search with depth 1 returns valid move`() {
        val board = GobangBoard()
        board.put(7, 7, BoardConstants.BLACK)
        val result = searcher.search(board, BoardConstants.WHITE, 1)
        assertTrue(result.row in 0..14 && result.col in 0..14,
            "Expected valid move, got (${result.row}, ${result.col})")
    }

    @Test
    fun `search with depth 3 returns valid move`() {
        val board = GobangBoard()
        board.put(7, 7, BoardConstants.BLACK)
        board.put(7, 8, BoardConstants.WHITE)
        val result = searcher.search(board, BoardConstants.BLACK, 3)
        assertTrue(result.row in 0..14 && result.col in 0..14,
            "Expected valid move, got (${result.row}, ${result.col})")
    }
}