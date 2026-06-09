package com.gobang.engine

class GobangBoard {
    private val board = IntArray(BoardConstants.BOARD_SIZE * BoardConstants.BOARD_SIZE)
    var won: Set<Pair<Int, Int>> = emptySet()
        private set

    companion object {
        val DIRS = listOf(1 to -1, 1 to 0, 1 to 1, 0 to 1)
    }

    fun reset() {
        board.fill(BoardConstants.EMPTY)
        won = emptySet()
    }

    fun get(row: Int, col: Int): Int {
        if (row < 0 || row >= BoardConstants.BOARD_SIZE || col < 0 || col >= BoardConstants.BOARD_SIZE) {
            return 0
        }
        return board[row * BoardConstants.BOARD_SIZE + col]
    }

    fun put(row: Int, col: Int, stone: Int) {
        if (row >= 0 && row < BoardConstants.BOARD_SIZE && col >= 0 && col < BoardConstants.BOARD_SIZE) {
            board[row * BoardConstants.BOARD_SIZE + col] = stone
        }
    }

    fun check(): Int {
        for (i in 0 until BoardConstants.BOARD_SIZE) {
            for (j in 0 until BoardConstants.BOARD_SIZE) {
                if (board[i * BoardConstants.BOARD_SIZE + j] == BoardConstants.EMPTY) continue
                val id = board[i * BoardConstants.BOARD_SIZE + j]
                for ((dr, dc) in DIRS) {
                    var count = 0
                    var r = i
                    var c = j
                    for (k in 0 until 5) {
                        if (get(r, c) != id) break
                        r += dr
                        c += dc
                        count++
                    }
                    if (count == 5) {
                        val wonSet = mutableSetOf<Pair<Int, Int>>()
                        r = i
                        c = j
                        for (z in 0 until 5) {
                            wonSet.add(r to c)
                            r += dr
                            c += dc
                        }
                        won = wonSet
                        return id
                    }
                }
            }
        }
        return BoardConstants.EMPTY
    }

    fun dumps(): String {
        val sb = StringBuilder()
        for (i in 0 until BoardConstants.BOARD_SIZE) {
            for (j in 0 until BoardConstants.BOARD_SIZE) {
                val stone = board[i * BoardConstants.BOARD_SIZE + j]
                if (stone != BoardConstants.EMPTY) {
                    sb.append("$stone:${('A'.code + i).toChar()}${('A'.code + j).toChar()} ")
                }
            }
        }
        return sb.toString()
    }

    fun loads(text: String) {
        reset()
        for (item in text.trim().replace(',', ' ').split(' ')) {
            val n = item.trim()
            if (n.isEmpty()) continue
            val parts = n.split(':')
            val stone = parts[0].toInt()
            val row = parts[1][0].uppercaseChar() - 'A'
            val col = parts[1][1].uppercaseChar() - 'A'
            put(row, col, stone)
        }
    }

    fun board(): IntArray = board

    fun copyBoard(): IntArray = board.copyOf()
}