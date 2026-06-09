package com.gobang.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gobang.model.Move
import com.gobang.ui.theme.*

private val LABELS = ('A'..'O').map { it.toString() }

@Composable
fun Board(
    boardState: IntArray,
    wonPositions: Set<Pair<Int, Int>>,
    lastMove: Move?,
    onCellClick: (row: Int, col: Int) -> Unit,
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

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val labelWidthPx = with(density) { 24.dp.toPx() }
        val boardSizePx = minOf(maxWidthPx, maxHeightPx)
        val cellSizePx = (boardSizePx - labelWidthPx) / 15
        val cellSizeDp = with(density) { cellSizePx.toDp() }
        val labelSizeDp = with(density) { labelWidthPx.toDp() }
        val labelFontSize = with(density) { (cellSizePx * 0.45f).toSp() }
        val boardSizeDp = with(density) { boardSizePx.toDp() }

        Column(
            modifier = Modifier.width(boardSizeDp).height(boardSizeDp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(labelSizeDp))
                for (col in 0..14) {
                    Text(
                        text = LABELS[col],
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(cellSizeDp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            for (row in 0..14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = LABELS[row],
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(labelSizeDp).height(cellSizeDp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    for (col in 0..14) {
                        val stone = boardState[row * 15 + col]
                        val isWon = Pair(row, col) in wonPositions
                        val isLastMove = lastMove != null && lastMove.row == row && lastMove.col == col

                        Box(
                            modifier = Modifier
                                .width(cellSizeDp)
                                .height(cellSizeDp)
                                .background(if ((row + col) % 2 == 0) BoardLight else BoardDark)
                                .clickable { onCellClick(row, col) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (stone != 0) {
                                val alpha = if (isWon) winAlpha else 1f
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.85f)
                                        .clip(CircleShape)
                                        .background(
                                            if (stone == 1) StoneBlack.copy(alpha = alpha)
                                            else StoneWhite.copy(alpha = alpha)
                                        )
                                )
                                if (stone == 2) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.85f)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                    )
                                }
                            }
                            if (isLastMove) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(LastMoveMarker)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}