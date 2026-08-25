package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class AlphaZeroRandomPositionTest {
    private class UniformPredictor : PolicyValuePredictor {
        override val modelId: String = "random-position-test"

        override fun predict(canonicalBoard: FloatArray): PolicyValue {
            assertTrue(canonicalBoard.size == AlphaZeroBoard.ACTION_SIZE)
            return PolicyValue(FloatArray(AlphaZeroBoard.ACTION_SIZE) { 1f }, 0f)
        }
    }

    @Test
    fun oneThousandDeterministicPositionsReturnLegalActions() {
        val predictor = UniformPredictor()
        var seed = 0x13579BDF

        repeat(1_000) {
            val board = GobangBoard()
            val occupied = BooleanArray(AlphaZeroBoard.ACTION_SIZE)
            var turn = BoardConstants.BLACK
            seed = nextInt(seed)
            val moveCount = 8 + ((seed and Int.MAX_VALUE) % 33)

            repeat(moveCount) {
                var action: Int
                do {
                    seed = nextInt(seed)
                    action = (seed and Int.MAX_VALUE) % AlphaZeroBoard.ACTION_SIZE
                } while (occupied[action])
                occupied[action] = true
                board.put(AlphaZeroBoard.rowOf(action), AlphaZeroBoard.colOf(action), turn)
                turn = AlphaZeroBoard.opponent(turn)
            }

            val result = AlphaZeroSearcher(
                predictor = predictor,
                config = AlphaZeroSearchConfig(simulations = 0, candidateLimit = 16),
            ).search(board, turn)

            if (result.row >= 0 && result.col >= 0) {
                assertTrue(board.get(result.row, result.col) == BoardConstants.EMPTY)
            }
        }
    }

    private fun nextInt(value: Int): Int = value * 1_664_525 + 1_013_904_223
}
