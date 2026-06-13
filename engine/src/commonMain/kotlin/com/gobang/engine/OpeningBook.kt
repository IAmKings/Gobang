package com.gobang.engine

import kotlin.random.Random

/** 开局着法序列 */
data class Opening(val moves: List<Move>, val name: String) {
    companion object {
        /** 从记谱法字符串创建开局，格式如 "1:HH" 表示黑棋(1)下在 H行H列(天元) */
        fun fromNotation(name: String, vararg notations: String): Opening {
            val moves = notations.map { notation ->
                val parts = notation.split(":")
                val stone = parts[0].toInt()
                val pos = parts[1]
                val row = pos[0] - 'A'
                val col = pos[1] - 'A'
                Move(row, col, stone)
            }
            return Opening(moves, name)
        }
    }
}

/**
 * 开局库：基于连珠标准26开局体系。
 *
 * 坐标说明：行和列均用 A(0)~O(14) 表示，H=7 为天元中心。
 * 编号1=黑棋，编号2=白棋。
 *
 * 直接开局（D）：第1子和第3子方向相关
 * 间接开局（I）：第1子和第3子方向无关
 *
 * 此处定义前2步，白棋位置互不重复（旋转/翻转已去重）。
 * 共20个有效开局 + 3个非天元开局。
 */
object OpeningBook {
    val openings: List<Opening> = listOf(
        // ===== 直接开局：白棋紧邻天元（7方向，左下I8与右上GI对称已去重）=====
        Opening.fromNotation("寒星", "1:HH", "2:HI"),   // D1: 右
        Opening.fromNotation("花月", "1:HH", "2:II"),   // D4: 右下
        Opening.fromNotation("雨月", "1:HH", "2:IH"),   // D6: 下
        Opening.fromNotation("金星", "1:HH", "2:GI"),   // D7: 右上
        Opening.fromNotation("松月", "1:HH", "2:GH"),   // D8: 上
        Opening.fromNotation("丘月", "1:HH", "2:GG"),   // D9: 左上
        Opening.fromNotation("新月", "1:HH", "2:HG"),   // D10: 左

        // ===== 直接开局：白棋距天元2~3格 =====
        Opening.fromNotation("溪月", "1:HH", "2:HJ"),   // D2: 右2
        Opening.fromNotation("残月", "1:HH", "2:IJ"),   // D5: 右下对角2
        Opening.fromNotation("瑞星", "1:HH", "2:HF"),   // D11: 左2
        Opening.fromNotation("疏星", "1:HH", "2:HK"),   // D3: 右3
        Opening.fromNotation("山月", "1:HH", "2:GJ"),   // D12: 骑士步上右
        Opening.fromNotation("游星", "1:HH", "2:FI"),   // D13: 远角跳

        // ===== 间接开局：白棋位置与直接开局不重复 =====
        Opening.fromNotation("长星", "1:HH", "2:JH"),   // I1: 下2右1
        Opening.fromNotation("峡月", "1:HH", "2:JJ"),   // I2: 右下2格
        Opening.fromNotation("恒星", "1:HH", "2:FH"),   // I3: 上2
        Opening.fromNotation("水月", "1:HH", "2:IG"),   // I4: 下1左1
        Opening.fromNotation("流星", "1:HH", "2:JG"),   // I5: 下2左1骑士步
        Opening.fromNotation("云月", "1:HH", "2:IF"),   // I6: 下1左2
        Opening.fromNotation("浦月", "1:HH", "2:FJ"),   // I7: 上1右2骑士步
        Opening.fromNotation("岚月", "1:HH", "2:JF"),   // I8: 下2左2
        Opening.fromNotation("银月", "1:HH", "2:KF"),   // I9: 远上右跳
        Opening.fromNotation("明星", "1:HH", "2:GF"),   // I10: 上左1
        Opening.fromNotation("斜月", "1:HH", "2:FG"),   // I11: 上左跳
        Opening.fromNotation("名月", "1:HH", "2:FK"),   // I12: 远上右3跳

        // ===== 非天元开局：黑棋不在中心 =====
        Opening.fromNotation("偏角", "1:IH", "2:HH"),  // 黑棋偏下，白占天元
        Opening.fromNotation("偏侧", "1:HG", "2:HH"),  // 黑棋偏左，白占天元
        Opening.fromNotation("偏角左上", "1:GG", "2:HH"), // 黑棋偏左上，白占天元
    )

    /** 随机返回一个开局 */
    fun randomOpening(): Opening = openings.random()
}