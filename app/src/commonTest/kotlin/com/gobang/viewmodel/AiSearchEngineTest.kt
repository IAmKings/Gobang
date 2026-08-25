package com.gobang.viewmodel

import com.gobang.engine.AlphaZeroSearchConfig
import com.gobang.engine.GobangBoard
import com.gobang.engine.SearchResult
import com.gobang.model.Difficulty
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AiSearchEngineTest {
    @Test
    fun difficultyMapsToBoundedMobileBudgets() {
        assertEquals(32, AiDifficultyConfig.forDifficulty(Difficulty.Easy).simulations)
        assertEquals(96, AiDifficultyConfig.forDifficulty(Difficulty.Medium).simulations)
        assertEquals(256, AiDifficultyConfig.forDifficulty(Difficulty.Hard).simulations)
        assertEquals(12, AiDifficultyConfig.forDifficulty(Difficulty.Easy).candidateLimit)
        assertEquals(3_000L, AiDifficultyConfig.forDifficulty(Difficulty.Hard).timeBudgetMillis)
    }

    @Test
    fun fallbackEngineReturnsLegacyResultWhenPrimaryFails() {
        val expected = SearchResult(score = 7, row = 3, col = 4)
        val fallback = object : AiSearchEngine {
            override suspend fun search(
                board: GobangBoard,
                turn: Int,
                config: AlphaZeroSearchConfig,
            ): SearchResult = expected
        }
        val primary = object : AiSearchEngine {
            override suspend fun search(
                board: GobangBoard,
                turn: Int,
                config: AlphaZeroSearchConfig,
            ): SearchResult = error("model unavailable")
        }

        val result = runSuspend { FallbackAiSearchEngine(primary, fallback).search(GobangBoard(), 1, AlphaZeroSearchConfig()) }

        assertEquals(expected, result)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
        override val context = kotlin.coroutines.EmptyCoroutineContext

        override fun resumeWith(value: Result<T>) {
            result = value
        }
    })
    return result!!.getOrThrow()
}
