package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GobangBoardTest {

    private val board = GobangBoard()

    @Test
    fun `board resets to all empty`() {
        board.put(7, 7, BoardConstants.BLACK)
        board.put(8, 8, BoardConstants.WHITE)
        board.reset()
        for (i in 0 until BoardConstants.BOARD_SIZE) {
            for (j in 0 until BoardConstants.BOARD_SIZE) {
                assertEquals(BoardConstants.EMPTY, board.get(i, j))
            }
        }
    }

    @Test
    fun `put and get stones`() {
        board.reset()
        board.put(0, 0, BoardConstants.BLACK)
        board.put(7, 7, BoardConstants.BLACK)
        board.put(14, 14, BoardConstants.WHITE)
        assertEquals(BoardConstants.BLACK, board.get(0, 0))
        assertEquals(BoardConstants.BLACK, board.get(7, 7))
        assertEquals(BoardConstants.WHITE, board.get(14, 14))
        assertEquals(BoardConstants.EMPTY, board.get(1, 1))
    }

    @Test
    fun `get returns zero for out of bounds`() {
        board.reset()
        assertEquals(0, board.get(-1, 0))
        assertEquals(0, board.get(0, -1))
        assertEquals(0, board.get(15, 0))
        assertEquals(0, board.get(0, 15))
    }

    @Test
    fun `put ignores out of bounds`() {
        board.reset()
        board.put(-1, 0, BoardConstants.BLACK)
        board.put(0, -1, BoardConstants.BLACK)
        board.put(15, 0, BoardConstants.BLACK)
        assertEquals(BoardConstants.EMPTY, board.get(0, 0))
    }

    @Test
    fun `check detects horizontal five in a row`() {
        board.reset()
        for (col in 0..4) {
            board.put(7, col, BoardConstants.BLACK)
        }
        assertEquals(BoardConstants.BLACK, board.check())
        assertEquals(5, board.won.size)
        for (col in 0..4) {
            assertTrue(Pair(7, col) in board.won)
        }
    }

    @Test
    fun `check detects vertical five in a row`() {
        board.reset()
        for (row in 3..7) {
            board.put(row, 5, BoardConstants.WHITE)
        }
        assertEquals(BoardConstants.WHITE, board.check())
        assertEquals(5, board.won.size)
    }

    @Test
    fun `check detects diagonal right five in a row`() {
        board.reset()
        for (i in 0..4) {
            board.put(i, i, BoardConstants.BLACK)
        }
        assertEquals(BoardConstants.BLACK, board.check())
        assertEquals(5, board.won.size)
    }

    @Test
    fun `check detects diagonal left five in a row`() {
        board.reset()
        for (i in 0..4) {
            board.put(i, 4 - i, BoardConstants.WHITE)
        }
        assertEquals(BoardConstants.WHITE, board.check())
        assertEquals(5, board.won.size)
    }

    @Test
    fun `check returns zero when no winner`() {
        board.reset()
        board.put(7, 7, BoardConstants.BLACK)
        board.put(7, 8, BoardConstants.WHITE)
        assertEquals(0, board.check())
        assertTrue(board.won.isEmpty())
    }

    @Test
    fun `dumps and loads roundtrip`() {
        board.reset()
        board.put(7, 7, BoardConstants.BLACK)
        board.put(8, 8, BoardConstants.WHITE)
        val dumped = board.dumps()

        val board2 = GobangBoard()
        board2.loads(dumped)
        assertEquals(BoardConstants.BLACK, board2.get(7, 7))
        assertEquals(BoardConstants.WHITE, board2.get(8, 8))
    }

    @Test
    fun `dumps produces python-compatible format`() {
        board.reset()
        board.put(7, 7, 1)
        board.put(8, 8, 2)
        val result = board.dumps()
        assertTrue(result.contains("1:HH"))
        assertTrue(result.contains("2:II"))
    }

    @Test
    fun `loads handles comma-separated format`() {
        board.reset()
        board.loads("1:HH,2:II")
        assertEquals(BoardConstants.BLACK, board.get(7, 7))
        assertEquals(BoardConstants.WHITE, board.get(8, 8))
    }

    @Test
    fun `loads handles empty string`() {
        board.reset()
        board.put(0, 0, BoardConstants.BLACK)
        board.loads("")
        assertEquals(BoardConstants.EMPTY, board.get(0, 0))
    }

    @Test
    fun `board method returns internal array reference`() {
        board.reset()
        board.put(7, 7, BoardConstants.BLACK)
        val arr = board.board()
        assertEquals(BoardConstants.BLACK, arr[7 * 15 + 7])
    }

    @Test
    fun `copyBoard returns independent copy`() {
        board.reset()
        board.put(7, 7, BoardConstants.BLACK)
        val copy = board.copyBoard()
        copy[7 * 15 + 7] = BoardConstants.WHITE
        assertEquals(BoardConstants.BLACK, board.get(7, 7))
    }
}