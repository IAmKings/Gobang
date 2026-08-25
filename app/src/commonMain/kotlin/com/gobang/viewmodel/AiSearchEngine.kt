package com.gobang.viewmodel

import com.gobang.engine.AlphaZeroSearchConfig
import com.gobang.engine.AlphaZeroSearcher
import com.gobang.engine.GobangBoard
import com.gobang.engine.GobangSearcher
import com.gobang.engine.PolicyValuePredictor
import com.gobang.engine.SearchResult
import com.gobang.model.Difficulty

/** The small app-layer seam used by GameViewModel. */
interface AiSearchEngine {
    suspend fun search(board: GobangBoard, turn: Int, config: AlphaZeroSearchConfig): SearchResult

    fun clearTree() = Unit

    fun advanceRoot(action: Int) = false
}

/** Legacy engine adapter kept as the safe default until a model is available. */
class LegacyAiSearchEngine(
    private val searcher: GobangSearcher = GobangSearcher(),
) : AiSearchEngine {
    override suspend fun search(board: GobangBoard, turn: Int, config: AlphaZeroSearchConfig): SearchResult {
        return searcher.search(board, turn, config.legacyDepth)
    }
}

/** Reuses one AlphaZero tree for each search budget/model configuration. */
class AlphaZeroAiSearchEngine(
    private val predictor: PolicyValuePredictor,
) : AiSearchEngine {
    private val searchers = mutableMapOf<AlphaZeroSearchConfig, AlphaZeroSearcher>()

    override suspend fun search(
        board: GobangBoard,
        turn: Int,
        config: AlphaZeroSearchConfig,
    ): SearchResult {
        val searcher = searchers.getOrPut(config) { AlphaZeroSearcher(predictor, config) }
        return searcher.search(board, turn)
    }

    override fun clearTree() {
        searchers.values.forEach(AlphaZeroSearcher::clearTree)
    }

    override fun advanceRoot(action: Int): Boolean {
        return searchers.values.any { it.advanceRoot(action) }
    }
}

/** Keeps model/runtime failures inside the engine boundary. */
class FallbackAiSearchEngine(
    private val primary: AiSearchEngine?,
    private val fallback: AiSearchEngine = LegacyAiSearchEngine(),
) : AiSearchEngine {
    override suspend fun search(board: GobangBoard, turn: Int, config: AlphaZeroSearchConfig): SearchResult {
        return try {
            primary?.search(board, turn, config) ?: fallback.search(board, turn, config)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            fallback.search(board, turn, config)
        }
    }

    override fun clearTree() {
        primary?.clearTree()
        fallback.clearTree()
    }

    override fun advanceRoot(action: Int): Boolean {
        return primary?.advanceRoot(action) ?: false
    }
}

/** Product budgets. The Expert/512 preset remains reserved for a later UI level. */
object AiDifficultyConfig {
    fun forDifficulty(difficulty: Difficulty): AlphaZeroSearchConfig = when (difficulty) {
        Difficulty.Easy -> AlphaZeroSearchConfig(
            simulations = 32,
            maxNodes = 768,
            candidateLimit = 12,
            timeBudgetMillis = 500,
        )
        Difficulty.Medium -> AlphaZeroSearchConfig(
            simulations = 96,
            maxNodes = 2_048,
            candidateLimit = 20,
            timeBudgetMillis = 1_500,
        )
        Difficulty.Hard -> AlphaZeroSearchConfig(
            simulations = 256,
            maxNodes = 4_096,
            candidateLimit = 32,
            timeBudgetMillis = 3_000,
        )
    }
}

private val AlphaZeroSearchConfig.legacyDepth: Int
    get() = when {
        simulations <= 32 -> 1
        simulations <= 96 -> 2
        else -> 3
    }
