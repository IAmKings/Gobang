package com.gobang.engine

import kotlin.random.Random

/** 开局着法序列 */
data class Opening(val moves: List<Move>, val name: String) {
    companion object {
        /** 从记谱法字符串创建开局，格式如 "1:HH" 表示黑棋下在 H行H列(天元) */
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
 * 开局库：基于连珠标准26开局体系，每局包含前3步（黑-白-黑）。
 *
 * 坐标系：行和列用 A(0)~O(14)，天元中心 = HH(7,7)。
 * 棋子编号：1=黑棋，2=白棋（第三手为黑棋，编号1）。
 *
 * 直止打法（13种）：白2位于天元上方一步 GH(6,7)。
 * 斜止打法（13种）：白2位于天元右上方对角一步 GI(6,8)。
 *
 * 数据来源：https://github.com/yutokure/New-Renju/blob/master/joseki.json
 * 参考：https://zh.wikipedia.org/wiki/连珠开局
 */
object OpeningBook {
    val openings: List<Opening> = listOf(
        // ===== 直止打法（白2 = GH，天元上方一步）=====

        // D1: 寒星 — 黑3在天元正上方两步
        Opening.fromNotation("寒星", "1:HH", "2:GH", "1:FH"),

        // D2: 溪月 — 黑3在白子右上方
        Opening.fromNotation("溪月", "1:HH", "2:GH", "1:FI"),

        // D3: 疏星 — 黑3在白子右方远处
        Opening.fromNotation("疏星", "1:HH", "2:GH", "1:FJ"),

        // D4: 花月 — 黑3在白子右斜一步
        Opening.fromNotation("花月", "1:HH", "2:GH", "1:GI"),

        // D5: 残月 — 黑3在白子右侧
        Opening.fromNotation("残月", "1:HH", "2:GH", "1:GJ"),

        // D6: 雨月 — 黑3在天元正右方一步
        Opening.fromNotation("雨月", "1:HH", "2:GH", "1:HI"),

        // D7: 金星 — 黑3在天元右方两步
        Opening.fromNotation("金星", "1:HH", "2:GH", "1:HJ"),

        // D8: 松月 — 黑3在天元正下方一步
        Opening.fromNotation("松月", "1:HH", "2:GH", "1:IH"),

        // D9: 丘月 — 黑3在天元右下方一步
        Opening.fromNotation("丘月", "1:HH", "2:GH", "1:II"),

        // D10: 新月 — 黑3在天元下方偏右
        Opening.fromNotation("新月", "1:HH", "2:GH", "1:IJ"),

        // D11: 瑞星 — 黑3在天元正下方两步
        Opening.fromNotation("瑞星", "1:HH", "2:GH", "1:JH"),

        // D12: 山月 — 黑3在天元下方偏右远处
        Opening.fromNotation("山月", "1:HH", "2:GH", "1:JI"),

        // D13: 游星 — 黑3在天元右下方远处
        Opening.fromNotation("游星", "1:HH", "2:GH", "1:JJ"),

        // ===== 斜止打法（白2 = GI，天元右上方对角一步）=====

        // I1: 长星 — 黑3在白子右方远处
        Opening.fromNotation("长星", "1:HH", "2:GI", "1:FJ"),

        // I2: 峡月 — 黑3在白子右侧
        Opening.fromNotation("峡月", "1:HH", "2:GI", "1:GJ"),

        // I3: 恒星 — 黑3在天元右方两步
        Opening.fromNotation("恒星", "1:HH", "2:GI", "1:HJ"),

        // I4: 水月 — 黑3在天元下方偏右
        Opening.fromNotation("水月", "1:HH", "2:GI", "1:IJ"),

        // I5: 流星 — 黑3在天元右下方远处
        Opening.fromNotation("流星", "1:HH", "2:GI", "1:JJ"),

        // I6: 云月 — 黑3在天元正右方一步
        Opening.fromNotation("云月", "1:HH", "2:GI", "1:HI"),

        // I7: 浦月 — 黑3在天元右下方一步
        Opening.fromNotation("浦月", "1:HH", "2:GI", "1:II"),

        // I8: 岚月 — 黑3在天元下方偏右远处
        Opening.fromNotation("岚月", "1:HH", "2:GI", "1:JI"),

        // I9: 银月 — 黑3在天元正下方一步
        Opening.fromNotation("银月", "1:HH", "2:GI", "1:IH"),

        // I10: 明星 — 黑3在天元正下方两步
        Opening.fromNotation("明星", "1:HH", "2:GI", "1:JH"),

        // I11: 斜月 — 黑3在天元左下方一步
        Opening.fromNotation("斜月", "1:HH", "2:GI", "1:IG"),

        // I12: 名月 — 黑3在天元左下方远处
        Opening.fromNotation("名月", "1:HH", "2:GI", "1:JG"),

        // I13: 彗星 — 黑3在天元左下方更远处
        Opening.fromNotation("彗星", "1:HH", "2:GI", "1:JF"),
    )

    /** 随机返回一个开局 */
    fun randomOpening(): Opening = openings.random()
}