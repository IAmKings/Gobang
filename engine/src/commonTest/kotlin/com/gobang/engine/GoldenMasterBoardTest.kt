package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenMasterBoardTest {

    @Test
    fun `dumps and loads roundtrip preserves board state`() {
        val testCases = listOf(
            "",
            "1:HH",
            "1:HH 2:II",
            "1:HD 1:HE 1:HF 1:HG 1:HH",
            "2:DH 2:EH 2:FH 2:GH 2:HH",
            "1:DD 1:EE 1:FF 1:GG 1:HH",
            "2:DH 2:EG 2:FF 2:GE 2:HD"
        )

        for (testCase in testCases) {
            val board = GobangBoard()
            board.loads(testCase)
            val dumped = board.dumps().trim()
            val board2 = GobangBoard()
            board2.loads(dumped)

            for (i in 0 until BoardConstants.BOARD_SIZE) {
                for (j in 0 until BoardConstants.BOARD_SIZE) {
                    assertEquals(
                        board.get(i, j), board2.get(i, j),
                        "Mismatch at ($i,$j) for input '$testCase'"
                    )
                }
            }
        }
    }

    @Test
    fun `check produces correct winner for known positions`() {
        data class TestCase(val dumps: String, val expectedWinner: Int, val description: String)

        val testCases = listOf(
            TestCase("", 0, "empty board"),
            TestCase("1:HD 1:HE 1:HF 1:HG 1:HH", 1, "black horizontal five"),
            TestCase("2:DH 2:EH 2:FH 2:GH 2:HH", 2, "white vertical five"),
            TestCase("1:DD 1:EE 1:FF 1:GG 1:HH", 1, "black diagonal right five"),
            TestCase("2:DH 2:EG 2:FF 2:GE 2:HD", 2, "white diagonal left five"),
            TestCase("1:HH 2:II", 0, "opening position"),
            TestCase("1:AA 1:BA 1:CA 1:DA 1:EA", 1, "black wins on edge row"),
            TestCase("2:OA 2:OB 2:OC 2:OD 2:OE", 2, "white wins on bottom row"),
        )

        for ((dumps, expectedWinner, description) in testCases) {
            val board = GobangBoard()
            if (dumps.isNotEmpty()) board.loads(dumps)
            val result = board.check()
            assertEquals(expectedWinner, result, "Failed for: $description")
        }
    }

    @Test
    fun `won positions are set correctly for horizontal five`() {
        val board = GobangBoard()
        board.loads("1:HD 1:HE 1:HF 1:HG 1:HH")
        board.check()
        assertEquals(5, board.won.size)
        val expectedPositions = setOf(
            Pair(7, 3), Pair(7, 4), Pair(7, 5), Pair(7, 6), Pair(7, 7)
        )
        assertEquals(expectedPositions, board.won)
    }

    @Test
    fun `constructor coordinate mapping is correct`() {
        val board = GobangBoard()
        board.loads("1:HH 2:II")
        assertEquals(BoardConstants.BLACK, board.get(7, 7), "H=7, so (7,7) should be black")
        assertEquals(BoardConstants.WHITE, board.get(8, 8), "I=8, so (8,8) should be white")
    }
}