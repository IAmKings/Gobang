package com.gobang.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameMode {
    PvAI,
    AIvP,
    PvP,
    AIvAI
}