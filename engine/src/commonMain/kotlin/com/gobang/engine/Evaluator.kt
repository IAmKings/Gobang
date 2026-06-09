package com.gobang.engine

class GobangEvaluator {

    val POS: Array<IntArray> = Array(BoardConstants.BOARD_SIZE) { i ->
        IntArray(BoardConstants.BOARD_SIZE) { j ->
            (7 - maxOf(kotlin.math.abs(i - 7), kotlin.math.abs(j - 7))).coerceAtLeast(0)
        }
    }

    companion object {
        const val STWO = 1
        const val STHREE = 2
        const val SFOUR = 3
        const val TWO = 4
        const val THREE = 5
        const val FOUR = 6
        const val FIVE = 7
        const val DFOUR = 8
        const val FOURT = 9
        const val DTHREE = 10
        const val NOTYPE = 11
        const val ANALYSED = 255
        const val TODO = 0

        const val BLACK = 1
        const val WHITE = 2
    }

    private val result = IntArray(30)
    private val line = IntArray(30)
    private val record = Array(BoardConstants.BOARD_SIZE) { i ->
        Array(BoardConstants.BOARD_SIZE) { j ->
            IntArray(4)
        }
    }
    private val count = Array(3) { IntArray(20) }

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

    fun evaluate(board: GobangBoard, turn: Int): Int {
        return evaluateFromBoard(board.board(), turn)
    }

    fun evaluateFromBoard(boardArr: IntArray, turn: Int): Int {
        val score = evaluateInternal(boardArr, turn)
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

    private fun evaluateInternal(boardArr: IntArray, turn: Int): Int {
        reset()
        val SIZE = BoardConstants.BOARD_SIZE
        for (i in 0 until SIZE) {
            for (j in 0 until SIZE) {
                if (boardArr[i * SIZE + j] != 0) {
                    if (record[i][j][0] == TODO) {
                        analysisHorizontal(boardArr, i, j)
                    }
                    if (record[i][j][1] == TODO) {
                        analysisVertical(boardArr, i, j)
                    }
                    if (record[i][j][2] == TODO) {
                        analysisLeft(boardArr, i, j)
                    }
                    if (record[i][j][3] == TODO) {
                        analysisRight(boardArr, i, j)
                    }
                }
            }
        }

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

        if (turn == WHITE) {
            if (count[BLACK][FIVE] > 0) return -9999
            if (count[WHITE][FIVE] > 0) return 9999
        } else {
            if (count[WHITE][FIVE] > 0) return -9999
            if (count[BLACK][FIVE] > 0) return 9999
        }

        if (count[WHITE][SFOUR] >= 2) {
            count[WHITE][FOUR]++
        }
        if (count[BLACK][SFOUR] >= 2) {
            count[BLACK][FOUR]++
        }

        var wvalue = 0
        var bvalue = 0

        if (turn == WHITE) {
            if (count[WHITE][FOUR] > 0) return 9990
            if (count[WHITE][SFOUR] > 0) return 9980
            if (count[BLACK][FOUR] > 0) return -9970
            if (count[BLACK][SFOUR] > 0 && count[BLACK][THREE] > 0) return -9960
            if (count[WHITE][THREE] > 0 && count[BLACK][SFOUR] == 0) return 9950
            if (count[BLACK][THREE] > 1 && count[WHITE][SFOUR] == 0 && count[WHITE][THREE] == 0 && count[WHITE][STHREE] == 0) return -9940
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

    fun analysisLine(line: IntArray, record: IntArray, num: Int, pos: Int): Int {
        for (i in num until 30) {
            line[i] = 0xf
        }
        for (i in 0 until num) {
            record[i] = TODO
        }
        if (num < 5) {
            for (i in 0 until num) {
                record[i] = ANALYSED
            }
            return 0
        }

        val stone = line[pos]
        val inverse = if (stone == 1) 2 else 1
        val num1 = num - 1

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

        if (rightRange - leftRange < 4) {
            for (k in leftRange..rightRange) {
                record[k] = ANALYSED
            }
            return 0
        }

        for (k in xl..xr) {
            record[k] = ANALYSED
        }

        val srange = xr - xl

        if (srange >= 4) {
            record[pos] = FIVE
            return FIVE
        }

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
                        record[pos] = FOUR
                    } else {
                        record[pos] = SFOUR
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

        if (srange == 2) {
            var left3 = false
            if (xl > 0) {
                if (line[xl - 1] == 0) {
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
                    if (xr < num1 - 1 && line[xr + 2] == stone) {
                        record[xr] = SFOUR
                        record[xr + 2] = ANALYSED
                    } else if (left3) {
                        record[xr] = THREE
                    } else {
                        record[xr] = STHREE
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

        if (srange == 1) {
            var left2 = false
            if (xl > 2) {
                if (line[xl - 1] == 0) {
                    if (line[xl - 2] == stone) {
                        if (line[xl - 3] == stone) {
                            record[xl - 3] = ANALYSED
                            record[xl - 2] = ANALYSED
                            record[xl] = SFOUR
                        } else if (line[xl - 3] == 0) {
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
                            record[pos] = TWO
                        } else {
                            record[pos] = STWO
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