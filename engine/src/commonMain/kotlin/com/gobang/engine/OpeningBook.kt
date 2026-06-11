package com.gobang.engine

import kotlin.random.Random

/** 开局着法序列 */
data class Opening(val moves: List<Move>) {
    companion object {
        /** 从记谱法字符串创建开局，格式如 "1:HH" 表示黑棋下在 (7,7) */
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

/** 开局库：从预设开局中随机选择一个 */
object OpeningBook {
    val openings: List<Opening> = listOf(
        Opening.fromNotation("1:HH", "2:II"),  // 中心开局 + 对角应
        Opening.fromNotation("1:IH", "2:GI"),  // 偏中心 + 应
        Opening.fromNotation("1:HG", "2:HI"),  // 偏侧 + 中心应
    )

    /** 随机返回一个开局 */
    fun randomOpening(): Opening = openings.random()
}