package com.gobang.engine

// 一步棋：行、列、棋子类型（1=黑，2=白）
data class Move(val row: Int, val col: Int, val stone: Int)