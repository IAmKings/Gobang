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
 * 输出行格式：name(stones=..,turn=..) | d1=<ms>ms->(r,c) | d2=... | d3=... | timed6=<ms>ms->(r,c)
 */
@Ignore("P1 复测数据已采集；后续阶段复测时临时移除本注解")
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
            // Hard 语义：searchTimed(maxDepth=6, 1000ms) —— 若实测耗时 < 1000ms 说明完整完成 6 层
            val t6 = System.nanoTime()
            val r6 = searcher.searchTimed(board, pos.turn, 6, 1000L)
            val ms6 = (System.nanoTime() - t6) / 1_000_000
            sb.append(" | timed6=${ms6}ms->(${r6.row},${r6.col})")
            println(sb.toString())
        }
    }
}
