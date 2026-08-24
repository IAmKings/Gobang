package com.gobang.engine

/**
 * Immutable board data passed to a policy/value model.
 *
 * The values are the original engine values (0, black, white). Model input is
 * produced separately as 0/current-player/other-player using canonical form.
 */
class BoardSnapshot internal constructor(
    cells: IntArray,
    val toPlay: Int,
) {
    val cells: IntArray = cells.copyOf()
    val hash: Long = AlphaZeroBoard.hashOf(this.cells, toPlay)

    fun copyCells(): IntArray = cells.copyOf()

    fun canonicalValues(): FloatArray {
        val other = AlphaZeroBoard.opponent(toPlay)
        return FloatArray(cells.size) { index ->
                when (cells[index]) {
                    toPlay -> 1f
                    other -> -1f
                    else -> 0f
                }
            }
    }
}

/** Mutable, allocation-light board used only inside AlphaZero search. */
class AlphaZeroBoard private constructor(
    private val cells: IntArray,
    var toPlay: Int,
) {
    private val previousPlayers = ArrayList<Int>()
    private val moveHistory = ArrayList<Move>()

    constructor(board: GobangBoard, toPlay: Int) : this(board.copyBoard(), toPlay)

    constructor(snapshot: BoardSnapshot) : this(snapshot.copyCells(), snapshot.toPlay)

    init {
        require(toPlay == BoardConstants.BLACK || toPlay == BoardConstants.WHITE) {
            "Invalid side to play: $toPlay"
        }
        require(cells.size == ACTION_SIZE) { "Expected a 15x15 board" }
    }

    val playerToMove: Int
        get() = toPlay

    val moveCount: Int
        get() = cells.count { it != BoardConstants.EMPTY }

    val isFull: Boolean
        get() = cells.none { it == BoardConstants.EMPTY }

    fun get(row: Int, col: Int): Int {
        require(row in 0 until BoardConstants.BOARD_SIZE) { "Row out of bounds: $row" }
        require(col in 0 until BoardConstants.BOARD_SIZE) { "Column out of bounds: $col" }
        return cells[actionOf(row, col)]
    }

    fun valueAt(action: Int): Int {
        require(action in 0 until ACTION_SIZE) { "Action out of bounds: $action" }
        return cells[action]
    }

    fun isLegal(action: Int): Boolean = action in 0 until ACTION_SIZE && cells[action] == BoardConstants.EMPTY

    fun legalActions(): IntArray {
        val actions = ArrayList<Int>()
        for (action in 0 until ACTION_SIZE) {
            if (cells[action] == BoardConstants.EMPTY) actions.add(action)
        }
        return actions.toIntArray()
    }

    /** Makes a move and records enough state for an exact [unmakeMove]. */
    fun makeMove(action: Int, stone: Int = toPlay): Move {
        require(stone == BoardConstants.BLACK || stone == BoardConstants.WHITE) {
            "Invalid stone: $stone"
        }
        require(isLegal(action)) { "Illegal action: $action" }
        val move = Move(rowOf(action), colOf(action), stone)
        previousPlayers.add(toPlay)
        moveHistory.add(move)
        cells[action] = stone
        toPlay = opponent(stone)
        return move
    }

    fun unmakeMove(move: Move) {
        val action = actionOf(move.row, move.col)
        require(moveHistory.isNotEmpty()) { "No move to unmake" }
        require(moveHistory.last() == move) { "Moves must be unmade in reverse order" }
        require(cells[action] == move.stone) { "Board does not contain the move" }
        cells[action] = BoardConstants.EMPTY
        moveHistory.removeAt(moveHistory.lastIndex)
        toPlay = previousPlayers.removeAt(previousPlayers.lastIndex)
    }

    /** Checks the position after the last move without copying the board. */
    fun winnerAfterLastMove(): Int {
        val last = moveHistory.lastOrNull() ?: return winner()
        return if (hasFiveAt(actionOf(last.row, last.col), last.stone)) last.stone else BoardConstants.EMPTY
    }

    /** Returns the winner in an arbitrary snapshot, if one already exists. */
    fun winner(): Int {
        for (action in 0 until ACTION_SIZE) {
            val stone = cells[action]
            if (stone != BoardConstants.EMPTY && hasFiveAt(action, stone)) return stone
        }
        return BoardConstants.EMPTY
    }

    fun snapshot(): BoardSnapshot = BoardSnapshot(cells, toPlay)

    fun canonicalValues(): FloatArray = snapshot().canonicalValues()

    fun copy(): AlphaZeroBoard = AlphaZeroBoard(snapshot())

    fun stableHash(): Long = hashOf(cells, toPlay)

    private fun hasFiveAt(action: Int, stone: Int): Boolean {
        val row = rowOf(action)
        val col = colOf(action)
        for ((dr, dc) in DIRECTIONS) {
            var count = 1
            count += countDirection(row, col, dr, dc, stone)
            count += countDirection(row, col, -dr, -dc, stone)
            if (count >= 5) return true
        }
        return false
    }

    private fun countDirection(row: Int, col: Int, dr: Int, dc: Int, stone: Int): Int {
        var r = row + dr
        var c = col + dc
        var count = 0
        while (r in 0 until BoardConstants.BOARD_SIZE && c in 0 until BoardConstants.BOARD_SIZE) {
            if (cells[actionOf(r, c)] != stone) break
            count++
            r += dr
            c += dc
        }
        return count
    }

    companion object {
        const val ACTION_SIZE = BoardConstants.BOARD_SIZE * BoardConstants.BOARD_SIZE
        private val DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

        fun from(board: GobangBoard, toPlay: Int): AlphaZeroBoard = AlphaZeroBoard(board, toPlay)

        fun actionOf(row: Int, col: Int): Int {
            require(row in 0 until BoardConstants.BOARD_SIZE) { "Row out of bounds: $row" }
            require(col in 0 until BoardConstants.BOARD_SIZE) { "Column out of bounds: $col" }
            return row * BoardConstants.BOARD_SIZE + col
        }

        fun rowOf(action: Int): Int {
            require(action in 0 until ACTION_SIZE) { "Action out of bounds: $action" }
            return action / BoardConstants.BOARD_SIZE
        }

        fun colOf(action: Int): Int {
            require(action in 0 until ACTION_SIZE) { "Action out of bounds: $action" }
            return action % BoardConstants.BOARD_SIZE
        }

        fun opponent(stone: Int): Int = when (stone) {
            BoardConstants.BLACK -> BoardConstants.WHITE
            BoardConstants.WHITE -> BoardConstants.BLACK
            else -> error("Invalid stone: $stone")
        }

        internal fun hashOf(cells: IntArray, toPlay: Int): Long {
            var hash = -0x340d631b7bdddcdbL
            for (cell in cells) {
                hash = (hash xor (cell + 1).toLong()) * 0x100000001b3L
            }
            return (hash xor toPlay.toLong()) * 0x100000001b3L
        }
    }
}
