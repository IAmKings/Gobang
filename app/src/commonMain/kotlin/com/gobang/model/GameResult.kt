package com.gobang.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameResult {
    BlackWins,
    WhiteWins,
    Draw
}