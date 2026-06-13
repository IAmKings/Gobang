package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningBookTest {

    @Test
    fun `opening book has correct number of openings`() {
        val actual = OpeningBook.openings.size
        println("Opening count: $actual")
        OpeningBook.openings.forEachIndexed { i, o ->
            println("$i: ${o.name} - ${o.moves}")
        }
        assertEquals(26, actual)
    }

    @Test
    fun `first opening is Kansei with 3 moves black-white-black`() {
        val opening = OpeningBook.openings[0]
        assertEquals("寒星", opening.name)
        assertEquals(3, opening.moves.size)
        assertEquals(1, opening.moves[0].stone) // 黑棋
        assertEquals(7, opening.moves[0].row)   // 天元 HH
        assertEquals(7, opening.moves[0].col)
        assertEquals(2, opening.moves[1].stone) // 白棋
        assertEquals(6, opening.moves[1].row)   // GH
        assertEquals(7, opening.moves[1].col)
        assertEquals(1, opening.moves[2].stone) // 黑棋（第三手）
        assertEquals(5, opening.moves[2].row)   // FH
        assertEquals(7, opening.moves[2].col)
    }

    @Test
    fun `randomOpening returns one of the openings`() {
        val opening = OpeningBook.randomOpening()
        assertTrue(opening in OpeningBook.openings)
    }

    @Test
    fun `fromNotation creates correct opening`() {
        val opening = Opening.fromNotation("测试", "1:HH", "2:GH", "1:GI")
        assertEquals("测试", opening.name)
        assertEquals(3, opening.moves.size)
        assertEquals(Move(7, 7, 1), opening.moves[0])
        assertEquals(Move(6, 7, 2), opening.moves[1])
        assertEquals(Move(6, 8, 1), opening.moves[2]) // 黑棋，编号1
    }

    @Test
    fun `all openings have exactly three moves`() {
        for (opening in OpeningBook.openings) {
            assertEquals(3, opening.moves.size, "Opening '${opening.name}' should have 3 moves")
        }
    }

    @Test
    fun `all opening names are unique`() {
        val names = OpeningBook.openings.map { it.name }
        assertEquals(names.size, names.toSet().size, "All opening names should be unique")
    }

    @Test
    fun `all openings follow black-white-black pattern`() {
        for (opening in OpeningBook.openings) {
            assertEquals(1, opening.moves[0].stone, "Opening '${opening.name}': first move should be black(1)")
            assertEquals(2, opening.moves[1].stone, "Opening '${opening.name}': second move should be white(2)")
            assertEquals(1, opening.moves[2].stone, "Opening '${opening.name}': third move should be black(1)")
        }
    }

    @Test
    fun `all openings start from center HH`() {
        for (opening in OpeningBook.openings) {
            assertEquals(7, opening.moves[0].row, "Opening '${opening.name}': first move row should be 7 (center)")
            assertEquals(7, opening.moves[0].col, "Opening '${opening.name}': first move col should be 7 (center)")
        }
    }

    @Test
    fun `direct openings have white at GH`() {
        val directNames = listOf("寒星", "溪月", "疏星", "花月", "残月", "雨月", "金星", "松月", "丘月", "新月", "瑞星", "山月", "游星")
        for (opening in OpeningBook.openings) {
            if (opening.name in directNames) {
                assertEquals(6, opening.moves[1].row, "Direct opening '${opening.name}': white should be at row 6 (G)")
                assertEquals(7, opening.moves[1].col, "Direct opening '${opening.name}': white should be at col 7 (H)")
            }
        }
    }

    @Test
    fun `indirect openings have white at GI`() {
        val indirectNames = listOf("长星", "峡月", "恒星", "水月", "流星", "云月", "浦月", "岚月", "银月", "明星", "斜月", "名月", "彗星")
        for (opening in OpeningBook.openings) {
            if (opening.name in indirectNames) {
                assertEquals(6, opening.moves[1].row, "Indirect opening '${opening.name}': white should be at row 6 (G)")
                assertEquals(8, opening.moves[1].col, "Indirect opening '${opening.name}': white should be at col 8 (I)")
            }
        }
    }

    @Test
    fun `no duplicate complete opening patterns`() {
        val patterns = mutableSetOf<String>()
        for (opening in OpeningBook.openings) {
            val pattern = opening.moves.joinToString("-") { "${it.row},${it.col},${it.stone}" }
            assertTrue(pattern !in patterns, "Duplicate opening pattern $pattern in opening '${opening.name}'")
            patterns.add(pattern)
        }
    }
}