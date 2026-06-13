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
        assertEquals(28, actual)
    }

    @Test
    fun `first opening is Hanshu HH-HI`() {
        val opening = OpeningBook.openings[0]
        assertEquals("寒星", opening.name)
        assertEquals(2, opening.moves.size)
        assertEquals(1, opening.moves[0].stone)
        assertEquals(7, opening.moves[0].row)
        assertEquals(7, opening.moves[0].col)
        assertEquals(2, opening.moves[1].stone)
        assertEquals(7, opening.moves[1].row)
        assertEquals(8, opening.moves[1].col)
    }

    @Test
    fun `randomOpening returns one of the openings`() {
        val opening = OpeningBook.randomOpening()
        assertTrue(opening in OpeningBook.openings)
    }

    @Test
    fun `fromNotation creates correct opening`() {
        val opening = Opening.fromNotation("测试", "1:HH", "2:II")
        assertEquals("测试", opening.name)
        assertEquals(2, opening.moves.size)
        assertEquals(Move(7, 7, 1), opening.moves[0])
        assertEquals(Move(8, 8, 2), opening.moves[1])
    }

    @Test
    fun `all openings have exactly two moves`() {
        for (opening in OpeningBook.openings) {
            assertEquals(2, opening.moves.size, "Opening '${opening.name}' should have 2 moves")
        }
    }

    @Test
    fun `all opening names are unique`() {
        val names = OpeningBook.openings.map { it.name }
        assertEquals(names.size, names.toSet().size, "All opening names should be unique")
    }

    @Test
    fun `all black stones are stone 1`() {
        for (opening in OpeningBook.openings) {
            assertEquals(1, opening.moves[0].stone, "Opening '${opening.name}': black should be stone 1")
        }
    }

    @Test
    fun `all white stones are stone 2`() {
        for (opening in OpeningBook.openings) {
            assertEquals(2, opening.moves[1].stone, "Opening '${opening.name}': white should be stone 2")
        }
    }
}