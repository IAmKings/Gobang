package com.gobang.model

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(val depth: Int) {
    Easy(1),
    Medium(2),
    Hard(3)
}