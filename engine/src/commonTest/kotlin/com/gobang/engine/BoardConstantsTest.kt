package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardConstantsTest {

    @Test
    fun `board size is 15`() {
        assertEquals(15, BoardConstants.BOARD_SIZE)
    }

    @Test
    fun `stone values are correct`() {
        assertEquals(0, BoardConstants.EMPTY)
        assertEquals(1, BoardConstants.BLACK)
        assertEquals(2, BoardConstants.WHITE)
    }
}