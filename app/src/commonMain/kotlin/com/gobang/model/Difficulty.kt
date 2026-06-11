package com.gobang.model

import kotlinx.serialization.Serializable

/** 难度等级，depth 对应搜索深度 */
@Serializable
enum class Difficulty(val depth: Int) {
    Easy(1),      // 简单：搜索深度 1
    Medium(2),    // 中等：搜索深度 2
    Hard(3)       // 困难：搜索深度 3
}