package com.gobang.model

import kotlinx.serialization.Serializable

/** 游戏模式 */
@Serializable
enum class GameMode {
    PvAI,   // 人先手 vs AI
    AIvP,   // AI先手 vs 人
    PvP,    // 双人对战
    AIvAI   // AI vs AI（观战模式）
}