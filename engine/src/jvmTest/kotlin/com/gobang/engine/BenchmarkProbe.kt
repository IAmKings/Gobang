package com.gobang.engine

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * 性能基准探针（S0 使用）。
 *
 * 收集 BenchmarkPositions 各局面在深度 1/2/3 下的搜索耗时与首着。
 * 默认 @Ignore；阶段验收复测时临时移除 @Ignore 再跑：
 *   JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
 *     ./gradlew :engine:jvmTest --tests "com.gobang.engine.BenchmarkProbe"
 *
 * 输出行格式：name(stones=..,turn=..) | d1=<ms>ms->(r,c) | d2=... | d3=... | timed8=<ms>ms->(r,c)
 */
@Ignore("Hard 参数已更新为 maxDepth=8/1000ms 并完成本地验证；复测时临时移除本注解")
class BenchmarkProbe {

    @Test
    fun probe() {
        val searcher = GobangSearcher()
        for (pos in BenchmarkPositions.all()) {
            val board = GobangBoard()
            board.loads(pos.boardText)
            val sb = StringBuilder("${pos.name}(stones=${pos.stones},turn=${pos.turn})")
            for (d in 1..3) {
                val t0 = System.nanoTime()
                val result = searcher.search(board, pos.turn, d)
                val ms = (System.nanoTime() - t0) / 1_000_000
                sb.append(" | d$d=${ms}ms->(${result.row},${result.col})")
            }
            // Hard 语义：searchTimed(maxDepth=8, 1000ms) —— 若实测耗时 < 1000ms 说明完整完成 8 层
            val t8 = System.nanoTime()
            val r8 = searcher.searchTimed(board, pos.turn, 8, 1000L)
            val ms8 = (System.nanoTime() - t8) / 1_000_000
            sb.append(" | timed8=${ms8}ms->(${r8.row},${r8.col})")
            println(sb.toString())
        }
    }
}
