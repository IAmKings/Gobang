package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 复合威胁评分测试：验证评估器对"双活三 / 冲四+活三（四三）/ 双冲四"等
 * 必胜组合给出接近必胜的高分档（> 单活三/单冲四，< 活四），并保留防守视角负分。
 * 数值断言采用范围（避免与实现细节过度耦合），fixture 与既有断言不修改。
 */
class EvaluatorThreatTest {

    private val evaluator = GobangEvaluator()

    @Test
    fun `own double open three scores near-win above single open three`() {
        // 黑横活三 (7,3..5) + 竖活三 (5..7,10)，两端均空，轮黑
        val b = GobangBoard()
        b.loads("1:HD 1:HE 1:HF 1:FK 1:GK 1:HK 2:AA 2:BO 2:NB")
        val double = evaluator.evaluate(b, BoardConstants.BLACK)

        val single = GobangBoard().apply { loads("1:HD 1:HE 1:HF 2:AA 2:BO 2:NB") }
        val singleScore = evaluator.evaluate(single, BoardConstants.BLACK)

        assertTrue(double > singleScore, "双活三($double) 应高于单活三($singleScore)")
        assertTrue(double >= 9985 && double < 10000,
            "双活三应为接近必胜档（≥9985；允许折算为活四 9990），实际 $double")
        assertTrue(singleScore < 9985, "单活三不应达到双活三档，实际 $singleScore")
    }

    @Test
    fun `opponent double open three forces defensive negative score`() {
        // 白双活三、轮黑且黑无即时威胁 → 黑视角应大幅为负（必须防守）
        val b = GobangBoard()
        b.loads("2:HD 2:HE 2:HF 2:FK 2:GK 2:HK 1:AA 1:BO 1:NB")
        val score = evaluator.evaluate(b, BoardConstants.BLACK)
        assertTrue(score < -9000, "对方双活三应给防守负分，实际 $score")
    }

    @Test
    fun `own four-three scores near-win above plain open three`() {
        // 黑冲四 (7,3..6，c7 白堵) + 黑活三 (4..6,12)，轮黑 → 四三应显著高于单活三
        val combo = GobangBoard()
        combo.loads("1:HD 1:HE 1:HF 1:HG 1:EM 1:FM 1:GM 2:HH 2:AA")
        val comboScore = evaluator.evaluate(combo, BoardConstants.BLACK)

        val openThree = GobangBoard().apply { loads("1:HD 1:HE 1:HF 2:AA") }
        val openThreeScore = evaluator.evaluate(openThree, BoardConstants.BLACK)

        assertTrue(comboScore > openThreeScore, "四三($comboScore) 应显著高于单活三($openThreeScore)")
        assertTrue(comboScore >= 9980 && comboScore < 10000,
            "四三应为接近必胜档（≥9980），实际 $comboScore")
    }

    @Test
    fun `own double four folds to live-four score`() {
        // 黑两个独立冲四（各自一端被堵）→ 双冲四折算活四 = 9990
        val b = GobangBoard()
        b.loads("1:HD 1:HE 1:HF 1:HG 1:DI 1:EI 1:FI 1:GI 2:HH 2:HI")
        val score = evaluator.evaluate(b, BoardConstants.BLACK)
        assertTrue(score == 9990 || (score > 9980 && score < 10000),
            "双冲四应达到活四档 9990，实际 $score")
    }
}
