package com.gobang.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gobang.model.Move
import com.gobang.ui.theme.*

private val COL_LABELS = (1..15).map { it.toString() }
private val ROW_LABELS = ('A'..'O').map { it.toString() }

private val STAR_POINTS = setOf(
    Pair(3, 3),
    Pair(3, 11),
    Pair(11, 3),
    Pair(11, 11),
    Pair(7, 7),
)

@Composable
fun Board(
    boardState: IntArray,
    wonPositions: Set<Pair<Int, Int>>,
    lastMove: Move?,
    onCellClick: (row: Int, col: Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "winGlow")
    val winAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "winAlpha"
    )

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val isDark = !MaterialTheme.colorScheme.onBackground.equals(Color(0xFF1A1A1A))

    val leftMargin = with(density) { 32.dp.toPx() }
    val topMargin = with(density) { 28.dp.toPx() }

    val boardBgColor = BoardLight
    val gridLineColor = GridLine
    val starDotColor = StarDot
    val stoneBlackColor = StoneBlack
    val stoneWhiteColor = StoneWhite
    val lastMoveColor = LastMoveMarker

    BoxWithConstraints(modifier = modifier) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val cellPx = minOf(maxW - leftMargin, maxH - topMargin) / 14f
        val labelFontSize = with(density) { (cellPx * 0.38f).toSp() }
        val labelColor = MaterialTheme.colorScheme.onBackground
        val labelStyle = TextStyle(fontSize = labelFontSize, color = labelColor)
        val totalW = leftMargin + cellPx * 14f
        val totalH = topMargin + cellPx * 14f
        val canvasW = with(density) { totalW.toDp() }
        val canvasH = with(density) { totalH.toDp() }

        Canvas(
            modifier = Modifier
                .width(canvasW)
                .height(canvasH)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val col = ((offset.x - leftMargin) / cellPx + 0.5f).toInt()
                                val row = ((offset.y - topMargin) / cellPx + 0.5f).toInt()
                                if (row in 0..14 && col in 0..14) {
                                    onCellClick(row, col)
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            drawRect(color = boardBgColor, topLeft = Offset(leftMargin, topMargin), size = Size(cellPx * 14, cellPx * 14))

            for (i in 0..14) {
                drawLine(
                    color = gridLineColor,
                    start = Offset(leftMargin + i * cellPx, topMargin),
                    end = Offset(leftMargin + i * cellPx, topMargin + 14f * cellPx),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = gridLineColor,
                    start = Offset(leftMargin, topMargin + i * cellPx),
                    end = Offset(leftMargin + 14f * cellPx, topMargin + i * cellPx),
                    strokeWidth = 1.5f
                )
            }

            for ((row, col) in STAR_POINTS) {
                drawCircle(
                    color = starDotColor,
                    radius = cellPx * 0.1f,
                    center = Offset(leftMargin + col * cellPx, topMargin + row * cellPx)
                )
            }

            for (row in 0..14) {
                val textResult = textMeasurer.measure(ROW_LABELS[row], labelStyle)
                drawText(
                    textResult,
                    topLeft = Offset(
                        (leftMargin - textResult.size.width) / 2f,
                        topMargin + row * cellPx - textResult.size.height / 2f
                    )
                )
            }
            for (col in 0..14) {
                val textResult = textMeasurer.measure(COL_LABELS[col], labelStyle)
                drawText(
                    textResult,
                    topLeft = Offset(
                        leftMargin + col * cellPx - textResult.size.width / 2f,
                        (topMargin - textResult.size.height) / 2f
                    )
                )
            }

            for (row in 0..14) {
                for (col in 0..14) {
                    val stone = boardState[row * 15 + col]
                    val x = leftMargin + col * cellPx
                    val y = topMargin + row * cellPx
                    val isWon = Pair(row, col) in wonPositions
                    val isLastMove = lastMove != null && lastMove.row == row && lastMove.col == col

                    if (stone != 0) {
                        val alpha = if (isWon) winAlpha else 1f
                        if (stone == 1) {
                            drawCircle(
                                color = stoneBlackColor.copy(alpha = alpha),
                                radius = cellPx * 0.42f,
                                center = Offset(x, y)
                            )
                        } else {
                            drawCircle(
                                color = stoneWhiteColor.copy(alpha = alpha),
                                radius = cellPx * 0.42f,
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = Color(0xFFBDBDBD).copy(alpha = alpha),
                                radius = cellPx * 0.42f,
                                center = Offset(x, y),
                                style = Stroke(width = cellPx * 0.04f)
                            )
                        }
                    }

                    if (isLastMove) {
                        drawCircle(
                            color = lastMoveColor,
                            radius = cellPx * 0.12f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}