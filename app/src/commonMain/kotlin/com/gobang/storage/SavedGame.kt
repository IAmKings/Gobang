package com.gobang.storage

import com.gobang.model.Difficulty
import com.gobang.model.GameMode
import com.gobang.model.Move
import kotlinx.serialization.Serializable

/** 游戏存档数据类，用于序列化存储和恢复游戏状态 */
@Serializable
data class SavedGame(
    val boardState: String,       // 棋盘状态，逗号分隔的 0/1/2 字符串
    val currentTurn: Int,          // 当前轮次
    val moveHistory: List<Move>,  // 已下棋步
    val difficulty: Difficulty,   // 难度
    val gameMode: GameMode,        // 游戏模式
    val savedAt: Long,             // 保存时间戳
)