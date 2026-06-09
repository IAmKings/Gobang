package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpeningBookTest {

    @Test
    fun `opening book has correct number of openings`() {
        assertEquals(3, OpeningBook.openings.size)
    }

    @Test
    fun `first opening is HH-II`() {
        val opening = OpeningBook.openings[0]
        assertEquals(2, opening.moves.size)
        assertEquals(1, opening.moves[0].stone)
        assertEquals(7, opening.moves[0].row)
        assertEquals(7, opening.moves[0].col)
        assertEquals(2, opening.moves[1].stone)
        assertEquals(8, opening.moves[1].row)
        assertEquals(8, opening.moves[1].col)
    }

    @Test
    fun `second opening is IH-GI`() {
        val opening = OpeningBook.openings[1]
        assertEquals(2, opening.moves.size)
        assertEquals(1, opening.moves[0].stone)
        assertEquals(8, opening.moves[0].row)
        assertEquals(7, opening.moves[0].col)
        assertEquals(2, opening.moves[1].stone)
        assertEquals(6, opening.moves[1].row)
        assertEquals(8, opening.moves[1].col)
    }

    @Test
    fun `third opening is HG-HI`() {
        val opening = OpeningBook.openings[2]
        assertEquals(2, opening.moves.size)
        assertEquals(1, opening.moves[0].stone)
        assertEquals(7, opening.moves[0].row)
        assertEquals(6, opening.moves[0].col)
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
        val opening = Opening.fromNotation("1:HH", "2:II")
        assertEquals(2, opening.moves.size)
        assertEquals(Move(7, 7, 1), opening.moves[0])
        assertEquals(Move(8, 8, 2), opening.moves[1])
    }
}