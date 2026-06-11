package com.gobang.model

import kotlinx.serialization.Serializable

/** 游戏结果 */
@Serializable
enum class GameResult {
    BlackWins,  // 黑棋胜
    WhiteWins,  // 白棋胜
    Draw         // 平局
}