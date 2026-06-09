package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class DifficultyTest {

    @Test
    fun `easy has depth 1`() {
        assertEquals(1, Difficulty.Easy.depth)
    }

    @Test
    fun `medium has depth 2`() {
        assertEquals(2, Difficulty.Medium.depth)
    }

    @Test
    fun `hard has depth 3`() {
        assertEquals(3, Difficulty.Hard.depth)
    }

    @Test
    fun `difficulty enum has three entries`() {
        assertEquals(3, Difficulty.entries.size)
    }
}