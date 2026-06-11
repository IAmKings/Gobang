package com.gobang.engine

/**
 * 棋局评估器，基于 Python 版移植。
 * 对四个方向（横、竖、左斜、右斜）逐一扫描线段，识别棋型（连五、活四、冲四、活三等），
 * 然后根据各方棋型数量加权计算局面评分。
 */
class GobangEvaluator {

    /** 位置权重矩阵：越靠近中心分值越高，中心=7，最边缘=0 */
    val POS: Array<IntArray> = Array(BoardConstants.BOARD_SIZE) { i ->
        IntArray(BoardConstants.BOARD_SIZE) { j ->
            (7 - maxOf(kotlin.math.abs(i - 7), kotlin.math.abs(j - 7))).coerceAtLeast(0)
        }
    }

    companion object {
        // 棋型常量
        const val STWO = 1       // 眠二
        const val STHREE = 2     // 眠三
        const val SFOUR = 3      // 冲四
        const val TWO = 4        // 活二
        const val THREE = 5     // 活三
        const val FOUR = 6       // 活四
        const val FIVE = 7       // 连五
        const val DFOUR = 8      // 双冲四（未直接使用，由评估逻辑隐含处理）
        const val FOURT = 9      // 四三（未直接使用）
        const val DTHREE = 10    // 双活三（未直接使用）
        const val NOTYPE = 11    // 未识别类型
        const val ANALYSED = 255 // 已分析标记
        const val TODO = 0       // 待分析标记

        const val BLACK = 1
        const val WHITE = 2
    }

    // 临时数组和记录矩阵（避免重复分配）
    private val result = IntArray(30)
    private val line = IntArray(30)
    private val record = Array(BoardConstants.BOARD_SIZE) { i ->
        Array(BoardConstants.BOARD_SIZE) { j ->
            IntArray(4) // 四个方向的棋型记录
        }
    }
    // count[stone][type] 统计各方各棋型数量
    private val count = Array(3) { IntArray(20) }

    /** 重置所有记录和计数 */
    fun reset() {
        for (i in 0 until BoardConstants.BOARD_SIZE) {
            for (j in 0 until BoardConstants.BOARD_SIZE) {
                for (k in 0 until 4) {
                    record[i][j][k] = TODO
                }
            }
        }
        for (i in 0 until 20) {
            count[0][i] = 0
            count[1][i] = 0
            count[2][i] = 0
        }
    }

    /** 评估指定棋盘和当前轮次 */
    fun evaluate(board: GobangBoard, turn: Int): Int {
        return evaluateFromBoard(board.board(), turn)
    }

    /** 基于一维数组评估，被搜索器调用 */
    fun evaluateFromBoard(boardArr: IntArray, turn: Int): Int {
        val score = evaluateInternal(boardArr, turn)
        // 高分/低分时附加位置权重调整
        if (score < -9000) {
            val stone = if (turn == BLACK) WHITE else BLACK
            var adjustedScore = score
            for (i in 0 until 20) {
                if (count[stone][i] > 0) {
                    adjustedScore -= i
                }
            }
            return adjustedScore
        } else if (score > 9000) {
            var adjustedScore = score
            for (i in 0 until 20) {
                if (count[turn][i] > 0) {
                    adjustedScore += i
                }
            }
            return adjustedScore
        }
        return score
    }

    /**
     * 核心评估逻辑：
     * 1. 重置记录
     * 2. 对每个有棋子的位置分析四个方向的棋型
     * 3. 统计各方各棋型数量
     * 4. 根据棋型优先级和数量计算评分
     */
    private fun evaluateInternal(boardArr: IntArray, turn: Int): Int {
        reset()
        val SIZE = BoardConstants.BOARD_SIZE
        // 逐位置分析四个方向
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                if (boardArr[i * SIZE + j] != 0) {
                    if (record[i][j][0] == TODO) analysisHorizontal(boardArr, i, j)
                    if (record[i][j][1] == TODO) analysisVertical(boardArr, i, j)
                    if (record[i][j][2] == TODO) analysisLeft(boardArr, i, j)
                    if (record[i][j][3] == TODO) analysisRight(boardArr, i, j)
                }
            }
        }

        // 统计各方棋型数量
        val checkSet = setOf(FIVE, FOUR, SFOUR, THREE, STHREE, TWO, STWO)
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                val stone = boardArr[i * SIZE + j]
                if (stone != 0) {
                    for (k in 0 until 4) {
                        val ch = record[i][j][k]
                        if (ch in checkSet) {
                            count[stone][ch]++
                        }
                    }
                }
            }
        }

        // 快速判定：连五必胜
        if (turn == WHITE) {
            if (count[BLACK][FIVE] > 0) return -9999
            if (count[WHITE][FIVE] > 0) return 9999
        } else {
            if (count[WHITE][FIVE] > 0) return -9999
            if (count[BLACK][FIVE] > 0) return 9999
        }

        // 双冲四视为活四
        if (count[WHITE][SFOUR] >= 2) count[WHITE][FOUR]++
        if (count[BLACK][SFOUR] >= 2) count[BLACK][FOUR]++

        var wvalue = 0
        var bvalue = 0

        // 根据轮次权重不同：当前方进攻权重更高
        if (turn == WHITE) {
            if (count[WHITE][FOUR] > 0) return 9990       // 活四必胜
            if (count[WHITE][SFOUR] > 0) return 9980       // 冲四
            if (count[BLACK][FOUR] > 0) return -9970       // 对方活四必须防
            if (count[BLACK][SFOUR] > 0 && count[BLACK][THREE] > 0) return -9960 // 对方冲四+活三
            if (count[WHITE][THREE] > 0 && count[BLACK][SFOUR] == 0) return 9950 // 我方活三
            if (count[BLACK][THREE] > 1 && count[WHITE][SFOUR] == 0 && count[WHITE][THREE] == 0 && count[WHITE][STHREE] == 0) return -9940
            // 加权计算
            if (count[WHITE][THREE] > 1) wvalue += 2000
            else if (count[WHITE][THREE] > 0) wvalue += 200
            if (count[BLACK][THREE] > 1) bvalue += 500
            else if (count[BLACK][THREE] > 0) bvalue += 100
            if (count[WHITE][STHREE] > 0) wvalue += count[WHITE][STHREE] * 10
            if (count[BLACK][STHREE] > 0) bvalue += count[BLACK][STHREE] * 10
            if (count[WHITE][TWO] > 0) wvalue += count[WHITE][TWO] * 4
            if (count[BLACK][TWO] > 0) bvalue += count[BLACK][TWO] * 4
            if (count[WHITE][STWO] > 0) wvalue += count[WHITE][STWO]
            if (count[BLACK][STWO] > 0) bvalue += count[BLACK][STWO]
        } else {
            if (count[BLACK][FOUR] > 0) return 9990
            if (count[BLACK][SFOUR] > 0) return 9980
            if (count[WHITE][FOUR] > 0) return -9970
            if (count[WHITE][SFOUR] > 0 && count[WHITE][THREE] > 0) return -9960
            if (count[BLACK][THREE] > 0 && count[WHITE][SFOUR] == 0) return 9950
            if (count[WHITE][THREE] > 1 && count[BLACK][SFOUR] == 0 && count[BLACK][THREE] == 0 && count[BLACK][STHREE] == 0) return -9940
            if (count[BLACK][THREE] > 1) bvalue += 2000
            else if (count[BLACK][THREE] > 0) bvalue += 200
            if (count[WHITE][THREE] > 1) wvalue += 500
            else if (count[WHITE][THREE] > 0) wvalue += 100
            if (count[BLACK][STHREE] > 0) bvalue += count[BLACK][STHREE] * 10
            if (count[WHITE][STHREE] > 0) wvalue += count[WHITE][STHREE] * 10
            if (count[BLACK][TWO] > 0) bvalue += count[BLACK][TWO] * 4
            if (count[WHITE][TWO] > 0) wvalue += count[WHITE][TWO] * 4
            if (count[BLACK][STWO] > 0) bvalue += count[BLACK][STWO]
            if (count[WHITE][STWO] > 0) wvalue += count[WHITE][STWO]
        }

        // 附加位置权重
        var wc = 0
        var bc = 0
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                val stone = boardArr[i * SIZE + j]
                if (stone != 0) {
                    if (stone == WHITE) wc += POS[i][j]
                    else bc += POS[i][j]
                }
            }
        }
        wvalue += wc
        bvalue += bc

        return if (turn == WHITE) wvalue - bvalue else bvalue - wvalue
    }

    /** 横向分析：提取第 i 行，分析该行棋型 */
    private fun analysisHorizontal(boardArr: IntArray, i: Int, j: Int) {
        val SIZE = BoardConstants.BOARD_SIZE
        for (x in 0 until SIZE) {
            line[x] = boardArr[i * SIZE + x]
        }
        analysisLine(line, result, SIZE, j)
        for (x in 0 until SIZE) {
            if (result[x] != TODO) {
                record[i][x][0] = result[x]
            }
        }
    }

    /** 纵向分析：提取第 j 列，分析该列棋型 */
    private fun analysisVertical(boardArr: IntArray, i: Int, j: Int) {
        val SIZE = BoardConstants.BOARD_SIZE
        for (x in 0 until SIZE) {
            line[x] = boardArr[x * SIZE + j]
        }
        analysisLine(line, result, SIZE, i)
        for (x in 0 until SIZE) {
            if (result[x] != TODO) {
                record[x][j][1] = result[x]
            }
        }
    }

    /** 左斜分析（从左下到右上） */
    private fun analysisLeft(boardArr: IntArray, i: Int, j: Int) {
        val SIZE = BoardConstants.BOARD_SIZE
        val startX: Int
        val startY: Int
        if (i < j) {
            startX = j - i
            startY = 0
        } else {
            startX = 0
            startY = i - j
        }
        var k = 0
        while (k < SIZE) {
            val x = startX + k
            val y = startY + k
            if (x >= SIZE || y >= SIZE) break
            line[k] = boardArr[(y) * SIZE + (x)]
            k++
        }
        analysisLine(line, result, k, j - startX)
        for (s in 0 until k) {
            if (result[s] != TODO) {
                record[startY + s][startX + s][2] = result[s]
            }
        }
    }

    /** 右斜分析（从左上到右下） */
    private fun analysisRight(boardArr: IntArray, i: Int, j: Int) {
        val SIZE = BoardConstants.BOARD_SIZE
        val startX: Int
        val val_startY: Int
        if (SIZE - 1 - i < j) {
            startX = j - SIZE + 1 + i
            val_startY = SIZE - 1
        } else {
            startX = 0
            val_startY = i + j
        }
        var k = 0
        while (k < SIZE) {
            val x = startX + k
            val y = val_startY - k
            if (x >= SIZE || y < 0) break
            line[k] = boardArr[y * SIZE + x]
            k++
        }
        analysisLine(line, result, k, j - startX)
        for (s in 0 until k) {
            if (result[s] != TODO) {
                record[val_startY - s][startX + s][3] = result[s]
            }
        }
    }

    /**
     * 分析一条线段上的棋型。
     * 从 pos 位置向两侧扩展，判断连子数和两端空位情况，
     * 识别出连五、活四、冲四、活三、眠三、活二、眠二等棋型。
     * @param line 线段数组
     * @param record 输出：每个位置的分析结果
     * @param num 线段长度
     * @param pos 当前分析位置
     * @return pos 位置识别出的棋型常量
     */
    fun analysisLine(line: IntArray, record: IntArray, num: Int, pos: Int): Int {
        // 尾部填充边界标记
        for (i in num until 30) {
            line[i] = 0xf
        }
        for (i in 0 until num) {
            record[i] = TODO
        }
        // 线段太短不可能形成五子
        if (num < 5) {
            for (i in 0 until num) {
                record[i] = ANALYSED
            }
            return 0
        }

        val stone = line[pos]
        val inverse = if (stone == 1) 2 else 1
        val num1 = num - 1

        // 找连续同色石子的范围 [xl, xr]
        var xl = pos
        var xr = pos
        while (xl > 0) {
            if (line[xl - 1] != stone) break
            xl--
        }
        while (xr < num1) {
            if (line[xr + 1] != stone) break
            xr++
        }

        // 扩展到被异色棋子阻挡的范围 [leftRange, rightRange]
        var leftRange = xl
        var rightRange = xr
        while (leftRange > 0) {
            if (line[leftRange - 1] == inverse) break
            leftRange--
        }
        while (rightRange < num1) {
            if (line[rightRange + 1] == inverse) break
            rightRange++
        }

        // 范围不够5格，不可能连五
        if (rightRange - leftRange < 4) {
            for (k in leftRange..rightRange) {
                record[k] = ANALYSED
            }
            return 0
        }

        // 标记连续同色区域为已分析
        for (k in xl..xr) {
            record[k] = ANALYSED
        }

        val srange = xr - xl

        // 连五
        if (srange >= 4) {
            record[pos] = FIVE
            return FIVE
        }

        // 冲四 / 活四（连4子，srange==3）
        if (srange == 3) {
            var leftFour = false
            if (xl > 0) {
                if (line[xl - 1] == 0) {
                    leftFour = true
                }
            }
            if (xr < num1) {
                if (line[xr + 1] == 0) {
                    if (leftFour) {
                        record[pos] = FOUR      // 活四：两端都有空位
                    } else {
                        record[pos] = SFOUR      // 冲四：只有一端有空位
                    }
                } else {
                    if (leftFour) {
                        record[pos] = SFOUR
                    }
                }
            } else {
                if (leftFour) {
                    record[pos] = SFOUR
                }
            }
            return record[pos]
        }

        // 活三 / 眠三（连3子，srange==2）
        if (srange == 2) {
            var left3 = false
            if (xl > 0) {
                if (line[xl - 1] == 0) {
                    // 跳三：空位+己方棋子 → 冲四
                    if (xl > 1 && line[xl - 2] == stone) {
                        record[xl] = SFOUR
                        record[xl - 2] = ANALYSED
                    } else {
                        left3 = true
                    }
                } else if (xr == num1 || line[xr + 1] != 0) {
                    return 0
                }
            }
            if (xr < num1) {
                if (line[xr + 1] == 0) {
                    // 跳三：空位+己方棋子 → 冲四
                    if (xr < num1 - 1 && line[xr + 2] == stone) {
                        record[xr] = SFOUR
                        record[xr + 2] = ANALYSED
                    } else if (left3) {
                        record[xr] = THREE     // 活三：两端都有空位
                    } else {
                        record[xr] = STHREE     // 眠三：只有一端有空位
                    }
                } else if (record[xl] == SFOUR) {
                    return record[xl]
                } else if (left3) {
                    record[pos] = STHREE
                }
            } else {
                if (record[xl] == SFOUR) {
                    return record[xl]
                }
                if (left3) {
                    record[pos] = STHREE
                }
            }
            return record[pos]
        }

        // 活二 / 眠二（连2子，srange==1）
        if (srange == 1) {
            var left2 = false
            if (xl > 2) {
                if (line[xl - 1] == 0) {
                    if (line[xl - 2] == stone) {
                        if (line[xl - 3] == stone) {
                            // 空位+两子 → 冲四
                            record[xl - 3] = ANALYSED
                            record[xl - 2] = ANALYSED
                            record[xl] = SFOUR
                        } else if (line[xl - 3] == 0) {
                            // 空位+一子 → 眠三
                            record[xl - 2] = ANALYSED
                            record[xl] = STHREE
                        }
                    } else {
                        left2 = true
                    }
                }
            }
            if (xr < num1) {
                if (line[xr + 1] == 0) {
                    if (xr < num1 - 2 && line[xr + 2] == stone) {
                        if (line[xr + 3] == stone) {
                            record[xr + 3] = ANALYSED
                            record[xr + 2] = ANALYSED
                            record[xr] = SFOUR
                        } else if (line[xr + 3] == 0) {
                            record[xr + 2] = ANALYSED
                            record[xr] = if (left2) THREE else STHREE
                        }
                    } else {
                        if (record[xl] == SFOUR) {
                            return record[xl]
                        }
                        if (record[xl] == STHREE) {
                            record[xl] = THREE
                            return record[xl]
                        }
                        if (left2) {
                            record[pos] = TWO       // 活二
                        } else {
                            record[pos] = STWO      // 眠二
                        }
                    }
                } else {
                    if (record[xl] == SFOUR) {
                        return record[xl]
                    }
                    if (left2) {
                        record[pos] = STWO
                    }
                }
            }
            return record[pos]
        }

        return 0
    }
}