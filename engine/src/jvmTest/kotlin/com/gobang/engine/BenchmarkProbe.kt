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
 * 输出行格式：name(stones=..,turn=..) | d1=<ms>ms->(r,c) | d2=... | d3=... | t9..t12=<ms>ms（3000ms 预算）
 */
@Ignore("3000ms 深度探测已采集；复测时临时移除本注解")
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
            // 预算 3000ms 下逐层探测：耗时 < 3000ms 即该层完整完成；≈3000ms 为被预算截断
            for (d in 9..12) {
                val t = System.nanoTime()
                val r = searcher.searchTimed(board, pos.turn, d, 3000L)
                val ms = (System.nanoTime() - t) / 1_000_000
                sb.append(" | t$d=${ms}ms->(${r.row},${r.col})")
            }
            println(sb.toString())
        }
    }
}
