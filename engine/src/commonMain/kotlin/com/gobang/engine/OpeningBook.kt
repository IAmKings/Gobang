package com.gobang.engine

data class Opening(val moves: List<Move>) {
    companion object {
        fun fromNotation(vararg notations: String): Opening {
            val moves = notations.map { notation ->
                val parts = notation.split(":")
                val stone = parts[0].toInt()
                val pos = parts[1]
                val row = pos[0] - 'A'
                val col = pos[1] - 'A'
                Move(row, col, stone)
            }
            return Opening(moves)
        }
    }
}

object OpeningBook {
    val openings: List<Opening> = listOf(
        Opening.fromNotation("1:HH", "2:II"),
        Opening.fromNotation("1:IH", "2:GI"),
        Opening.fromNotation("1:HG", "2:HI"),
    )

    fun randomOpening(): Opening = openings.random()
}