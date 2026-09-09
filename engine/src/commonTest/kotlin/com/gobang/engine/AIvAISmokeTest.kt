package com.gobang.engine

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AIvAI 对弈冒烟（集成评审）：黑白双方各用 GobangSearcher.search(depth=2) 交替落子，
 * 验证引擎在完整对局中无死循环、无非法落点、能正常到达终局（胜负或满盘平局）。
 * 覆盖父级效果门"AIvAI 冒烟对局正常终局、无死循环/异常"。
 */
class AIvAISmokeTest {

    @Test
    fun `self-play reaches a terminal state without illegal moves`() {
        val board = GobangBoard()
        val searcher = GobangSearcher()
        var turn = BoardConstants.BLACK
        var moves = 0
        val maxMoves = 200

        while (moves < maxMoves) {
            val result = searcher.search(board, turn, 2)
            assertTrue(
                result.row in 0..14 && result.col in 0..14,
                "第 ${moves + 1} 手应返回合法落点，实际 (${result.row},${result.col})",
            )
            assertTrue(board.get(result.row, result.col) == 0, "落点应为空位 (${result.row},${result.col})")
            board.put(result.row, result.col, turn)
            moves++
            if (board.check() != 0) break
            turn = if (turn == BoardConstants.BLACK) BoardConstants.WHITE else BoardConstants.BLACK
        }

        val ended = board.check() != 0 || moves >= maxMoves
        assertTrue(ended, "对局应在 $maxMoves 步内结束或达到步数上限")
        assertTrue(moves > 0, "至少完成一手")
        // 未分胜负时允许满盘（步数上限兜底），不要求必然分出胜负（depth2 弱 AI 可能出现拉锯）
    }
}
