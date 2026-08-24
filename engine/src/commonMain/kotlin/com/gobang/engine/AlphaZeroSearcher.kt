package com.gobang.engine

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.TimeSource

data class PolicyValue(
    val policy: FloatArray,
    val value: Float,
)

/** Pure model adapter; ONNX Runtime is intentionally outside the common engine. */
interface PolicyValuePredictor {
    val modelId: String
        get() = "unknown"

    fun predict(canonicalBoard: FloatArray): PolicyValue
}

data class AlphaZeroSearchConfig(
    val simulations: Int = 64,
    val maxNodes: Int = 2_048,
    val candidateLimit: Int = 32,
    val cPuct: Float = 1.25f,
    val timeBudgetMillis: Long? = null,
    val ruleVersion: String = "gomoku-15-exact-five-v1",
) {
    init {
        require(simulations >= 0) { "simulations must be non-negative" }
        require(maxNodes > 0) { "maxNodes must be positive" }
        require(candidateLimit > 0) { "candidateLimit must be positive" }
        require(cPuct >= 0f) { "cPuct must be non-negative" }
        require(timeBudgetMillis == null || timeBudgetMillis >= 0) { "timeBudgetMillis must be non-negative" }
    }
}

/** Deterministic, single-threaded PUCT search with a reusable root. */
class AlphaZeroSearcher(
    private val predictor: PolicyValuePredictor,
    private val config: AlphaZeroSearchConfig = AlphaZeroSearchConfig(),
    private val tacticalSolver: TacticalSolver = TacticalSolver(),
    private val candidatePruner: CandidatePruner = CandidatePruner(tacticalSolver),
) {
    private class Node(
        val actionFromParent: Int?,
        val positionHash: Long,
        val playerToMove: Int,
        val prior: Float = 0f,
    ) {
        var visits: Int = 0
        var valueSum: Float = 0f
        var expanded: Boolean = false
        val children = ArrayList<Node>()

        fun meanValue(): Float = if (visits == 0) 0f else valueSum / visits
    }

    private var root: Node? = null
    private var rootModelId: String? = null
    private var rootRuleVersion: String? = null
    private var createdNodes = 0

    var lastSearchReusedTree: Boolean = false
        private set

    val nodeCount: Int
        get() = createdNodes

    val rootVisitCount: Int
        get() = root?.visits ?: 0

    fun clearTree() {
        root = null
        rootModelId = null
        rootRuleVersion = null
        createdNodes = 0
    }

    /** Rebase the retained tree after the caller applies the selected move. */
    fun advanceRoot(action: Int): Boolean {
        val currentRoot = root ?: return false
        val child = currentRoot.children.firstOrNull { it.actionFromParent == action } ?: run {
            clearTree()
            return false
        }
        root = child
        createdNodes = countNodes(child)
        return true
    }

    fun search(board: GobangBoard, turn: Int): SearchResult = search(AlphaZeroBoard(board, turn))

    fun search(board: AlphaZeroBoard): SearchResult {
        val searchBoard = board.copy()
        require(searchBoard.toPlay == BoardConstants.BLACK || searchBoard.toPlay == BoardConstants.WHITE) {
            "Invalid side to play: ${searchBoard.toPlay}"
        }
        lastSearchReusedTree = false

        val immediateWins = tacticalSolver.immediateWins(searchBoard)
        if (immediateWins.isNotEmpty()) return resultFor(immediateWins[0], 10_000)

        val forcedBlocks = tacticalSolver.forcedBlocks(searchBoard)
        if (forcedBlocks.size == 1) return resultFor(forcedBlocks[0], 9_000)

        if (searchBoard.isFull) return SearchResult(0, -1, -1)

        val searchRoot = ensureRoot(searchBoard)
        val start = config.timeBudgetMillis?.let { TimeSource.Monotonic.markNow() }
        if (!searchRoot.expanded) expand(searchRoot, searchBoard)

        var simulations = 0
        while (simulations < config.simulations && createdNodes < config.maxNodes) {
            if (start != null && start.elapsedNow().inWholeMilliseconds >= config.timeBudgetMillis!!) break
            simulate(searchRoot, searchBoard)
            simulations++
        }

        val selected = searchRoot.children.maxWithOrNull(
            compareBy<Node> { it.visits }
                .thenByDescending { -it.actionFromParent!! },
        )?.actionFromParent
            ?: searchBoard.legalActions().firstOrNull()
            ?: return SearchResult(0, -1, -1)
        return resultFor(selected, (searchRoot.meanValue() * 10_000f).roundToInt())
    }

    private fun resultFor(action: Int, score: Int): SearchResult = SearchResult(
        score = score,
        row = AlphaZeroBoard.rowOf(action),
        col = AlphaZeroBoard.colOf(action),
    )

    private fun ensureRoot(board: AlphaZeroBoard): Node {
        val existing = root
        if (existing != null && existing.positionHash == board.stableHash() &&
            rootModelId == predictor.modelId && rootRuleVersion == config.ruleVersion
        ) {
            lastSearchReusedTree = true
            return existing
        }
        clearTree()
        rootModelId = predictor.modelId
        rootRuleVersion = config.ruleVersion
        return Node(null, board.stableHash(), board.toPlay).also {
            root = it
            createdNodes = 1
        }
    }

    private fun simulate(node: Node, board: AlphaZeroBoard): Float {
        val winner = board.winnerAfterLastMove()
        if (winner != BoardConstants.EMPTY) {
            val value = if (winner == board.toPlay) -1f else 1f
            node.visits++
            node.valueSum += value
            return value
        }
        if (board.isFull) {
            node.visits++
            return 0f
        }
        if (!node.expanded) {
            val value = expand(node, board)
            node.visits++
            node.valueSum += value
            return value
        }
        if (node.children.isEmpty()) {
            node.visits++
            return 0f
        }

        val child = selectChild(node)
        val move = board.makeMove(child.actionFromParent!!)
        val childValue = simulate(child, board)
        board.unmakeMove(move)
        val value = -childValue
        node.visits++
        node.valueSum += value
        return value
    }

    private fun expand(node: Node, board: AlphaZeroBoard): Float {
        if (node.expanded) return node.meanValue()
        val prediction = predictor.predict(board.canonicalValues())
        require(prediction.policy.size == AlphaZeroBoard.ACTION_SIZE) {
            "Policy must contain ${AlphaZeroBoard.ACTION_SIZE} actions"
        }
        val candidates = candidatePruner.candidates(board, prediction.policy, config.candidateLimit)
        val priors = normalisedPriors(prediction.policy, candidates)
        node.expanded = true
        for (index in candidates.indices) {
            if (createdNodes >= config.maxNodes) break
            val action = candidates[index]
            val move = board.makeMove(action)
            val child = Node(action, board.stableHash(), board.toPlay, priors[index])
            board.unmakeMove(move)
            node.children.add(child)
            createdNodes++
        }
        return prediction.value.coerceIn(-1f, 1f)
    }

    private fun selectChild(node: Node): Node = node.children.maxWithOrNull(
        compareBy<Node> {
            val q = if (it.visits == 0) 0f else -it.meanValue()
            val u = config.cPuct * it.prior * sqrt(node.visits.toFloat().coerceAtLeast(1f)) / (1f + it.visits)
            q + u
        }.thenByDescending { -it.actionFromParent!! },
    )!!

    private fun normalisedPriors(policy: FloatArray, actions: IntArray): FloatArray {
        val priors = FloatArray(actions.size)
        var sum = 0f
        for (index in actions.indices) {
            val value = policy.getOrNull(actions[index])?.takeIf { it.isFinite() && it > 0f } ?: 0f
            priors[index] = value
            sum += value
        }
        if (sum <= 0f) {
            val uniform = 1f / actions.size.coerceAtLeast(1)
            priors.fill(uniform)
        } else {
            for (index in priors.indices) priors[index] /= sum
        }
        return priors
    }

    private fun countNodes(node: Node): Int = 1 + node.children.sumOf(::countNodes)
}

/** Uses AlphaZero when a valid model is available and preserves the old AI as fallback. */
class CompositeSearcher(
    private val alphaZero: AlphaZeroSearcher?,
    private val fallback: GobangSearcher = GobangSearcher(),
) {
    fun search(board: GobangBoard, turn: Int): SearchResult {
        return try {
            alphaZero?.search(board, turn) ?: fallback.search(board, turn, 1)
        } catch (_: RuntimeException) {
            fallback.search(board, turn, 1)
        }
    }
}
