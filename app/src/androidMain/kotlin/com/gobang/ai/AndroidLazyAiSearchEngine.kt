package com.gobang.ai

import android.content.Context
import com.gobang.engine.AlphaZeroSearchConfig
import com.gobang.engine.GobangBoard
import com.gobang.engine.SearchResult
import com.gobang.viewmodel.AiSearchEngine
import com.gobang.viewmodel.AlphaZeroAiSearchEngine
import com.gobang.viewmodel.LegacyAiSearchEngine
import kotlinx.coroutines.CancellationException

/** Loads the model only from the first background search, then keeps the result for the process. */
class AndroidLazyAiSearchEngine(
    context: Context,
    private val fallback: AiSearchEngine = LegacyAiSearchEngine(),
) : AiSearchEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private var primary: AiSearchEngine? = null
    private var predictor: AndroidPolicyValuePredictor? = null
    private var loadAttempted = false

    override suspend fun search(
        board: GobangBoard,
        turn: Int,
        config: AlphaZeroSearchConfig,
    ): SearchResult {
        return try {
            primaryOrFallback().search(board, turn, config)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            disablePrimary()
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

    override fun close() {
        predictor?.close()
        predictor = null
        primary = null
    }

    private fun primaryOrFallback(): AiSearchEngine {
        primary?.let { return it }
        if (loadAttempted) return fallback

        loadAttempted = true
        val loaded = AndroidPolicyValuePredictor.load(appContext).getOrNull()
            ?: return fallback
        predictor = loaded
        return AlphaZeroAiSearchEngine(loaded).also { primary = it }
    }

    private fun disablePrimary() {
        predictor?.close()
        predictor = null
        primary = null
        loadAttempted = true
    }
}
