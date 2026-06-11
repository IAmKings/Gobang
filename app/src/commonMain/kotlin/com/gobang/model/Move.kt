package com.gobang.model

import kotlinx.serialization.Serializable

/** 一步棋：行、列、棋子类型（1=黑，2=白） */
@Serializable
data class Move(val row: Int, val col: Int, val stone: Int)