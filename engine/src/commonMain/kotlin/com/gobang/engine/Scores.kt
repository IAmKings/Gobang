package com.gobang.engine

// 棋型分数常量（当前未使用，评估器内部使用独立的分数逻辑）
object Scores {
    const val FIVE = 100000      // 连五
    const val FOUR = 10000       // 活四
    const val SFOUR = 1000       // 冲四
    const val THREE = 1000       // 活三
    const val STHREE = 100       // 眠三
    const val TWO = 100          // 活二
    const val STWO = 10          // 眠二
    const val WIN = 9999         // 胜利分数
    const val LOSE = -9999       // 失败分数
}