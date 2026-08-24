package com.gobang.engine

data class TacticalAnalysis(
    val immediateWins: IntArray,
    val forcedBlocks: IntArray,
)

/** Model-independent tactical checks used before and during MCTS expansion. */
class TacticalSolver {
    fun analyze(board: AlphaZeroBoard): TacticalAnalysis = TacticalAnalysis(
        immediateWins = immediateWins(board),
        forcedBlocks = forcedBlocks(board),
    )

    fun immediateWins(board: AlphaZeroBoard, player: Int = board.toPlay): IntArray {
        val wins = ArrayList<Int>()
        for (action in board.legalActions()) {
            val move = board.makeMove(action, player)
            if (board.winnerAfterLastMove() == player) wins.add(action)
            board.unmakeMove(move)
        }
        return wins.toIntArray()
    }

    /** Empty cells that would immediately win for the opponent. */
    fun forcedBlocks(board: AlphaZeroBoard): IntArray = immediateWins(board, AlphaZeroBoard.opponent(board.toPlay))

    /** A small deterministic threat score for ordering otherwise equal candidates. */
    fun threatScore(board: AlphaZeroBoard, action: Int, player: Int = board.toPlay): Int {
        require(board.isLegal(action)) { "Action must be legal: $action" }
        val move = board.makeMove(action, player)
        if (board.winnerAfterLastMove() == player) {
            board.unmakeMove(move)
            return 100_000
        }

        var score = 0
        val row = AlphaZeroBoard.rowOf(action)
        val col = AlphaZeroBoard.colOf(action)
        for ((dr, dc) in DIRECTIONS) {
            var own = 1
            own += count(board, row, col, dr, dc, player)
            own += count(board, row, col, -dr, -dc, player)
            val openEnds = openEndCount(board, row, col, dr, dc, player)
            score += when {
                own >= 4 -> 10_000 + openEnds * 100
                own == 3 -> 1_000 + openEnds * 50
                own == 2 -> 100 + openEnds * 10
                else -> openEnds
            }
        }
        board.unmakeMove(move)
        return score
    }

    private fun count(board: AlphaZeroBoard, row: Int, col: Int, dr: Int, dc: Int, player: Int): Int {
        var r = row + dr
        var c = col + dc
        var count = 0
        while (r in 0 until BoardConstants.BOARD_SIZE && c in 0 until BoardConstants.BOARD_SIZE) {
            if (board.get(r, c) != player) break
            count++
            r += dr
            c += dc
        }
        return count
    }

    private fun openEndCount(board: AlphaZeroBoard, row: Int, col: Int, dr: Int, dc: Int, player: Int): Int {
        var open = 0
        for (sign in intArrayOf(-1, 1)) {
            var r = row + sign * dr
            var c = col + sign * dc
            while (r in 0 until BoardConstants.BOARD_SIZE && c in 0 until BoardConstants.BOARD_SIZE && board.get(r, c) == player) {
                r += sign * dr
                c += sign * dc
            }
            if (r in 0 until BoardConstants.BOARD_SIZE && c in 0 until BoardConstants.BOARD_SIZE && board.get(r, c) == BoardConstants.EMPTY) {
                open++
            }
        }
        return open
    }

    private companion object {
        val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
    }
}

/** Generates a small, deterministic action set while retaining tactical moves. */
class CandidatePruner(
    private val tacticalSolver: TacticalSolver = TacticalSolver(),
) {
    fun candidates(
        board: AlphaZeroBoard,
        policy: FloatArray? = null,
        maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    ): IntArray {
        require(maxCandidates > 0) { "maxCandidates must be positive" }
        val legal = board.legalActions()
        if (legal.isEmpty()) return intArrayOf()

        val immediateWins = tacticalSolver.immediateWins(board)
        val forcedBlocks = tacticalSolver.forcedBlocks(board)
        val protected = LinkedHashSet<Int>()
        immediateWins.forEach(protected::add)
        forcedBlocks.forEach(protected::add)

        val occupied = (0 until AlphaZeroBoard.ACTION_SIZE).filter { board.valueAt(it) != BoardConstants.EMPTY }
        val local = if (occupied.isEmpty()) {
            listOf(AlphaZeroBoard.actionOf(BoardConstants.BOARD_SIZE / 2, BoardConstants.BOARD_SIZE / 2))
        } else {
            legal.filter { action ->
                val row = AlphaZeroBoard.rowOf(action)
                val col = AlphaZeroBoard.colOf(action)
                occupied.any { occupiedAction ->
                    kotlin.math.abs(row - AlphaZeroBoard.rowOf(occupiedAction)) <= SEARCH_RADIUS &&
                        kotlin.math.abs(col - AlphaZeroBoard.colOf(occupiedAction)) <= SEARCH_RADIUS
                }
            }
        }

        val candidateSet = LinkedHashSet<Int>()
        candidateSet.addAll(protected)
        candidateSet.addAll(local)
        if (candidateSet.isEmpty()) candidateSet.addAll(legal.toList())

        val ordered = candidateSet.toList().sortedWith(
            compareByDescending<Int> { if (it in immediateWins) 3 else if (it in forcedBlocks) 2 else 0 }
                .thenByDescending { tacticalSolver.threatScore(board, it) }
                .thenByDescending { policyScore(policy, it) }
                .thenBy { it },
        )
        val selected = if (protected.size >= maxCandidates) {
            ordered.filter { it in protected }
        } else {
            ordered.take(maxCandidates).toMutableList().also { result ->
                protected.forEach { action -> if (action !in result) result.add(action) }
            }
        }
        return selected.toIntArray()
    }

    private fun policyScore(policy: FloatArray?, action: Int): Float {
        if (policy == null || action !in policy.indices) return 0f
        return policy[action].takeIf { it.isFinite() } ?: 0f
    }

    private companion object {
        const val SEARCH_RADIUS = 2
        const val DEFAULT_MAX_CANDIDATES = 32
    }
}
