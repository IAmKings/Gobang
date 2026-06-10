package com.gobang.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gobang.i18n.LocaleManager
import com.gobang.model.*
import com.gobang.ui.component.Board

@Composable
fun GameScreen(
    state: GameState,
    onCellClick: (row: Int, col: Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang by LocaleManager.language.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("\u2190")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = LocaleManager.t("round", "n" to (state.moveHistory.size + 1)),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (state.difficulty) {
                        Difficulty.Easy -> LocaleManager.t("diff_easy")
                        Difficulty.Medium -> LocaleManager.t("diff_medium")
                        Difficulty.Hard -> LocaleManager.t("diff_hard")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    state.gameResult != null -> when (state.gameResult) {
                        GameResult.BlackWins -> LocaleManager.t("black_wins")
                        GameResult.WhiteWins -> LocaleManager.t("white_wins")
                        GameResult.Draw -> LocaleManager.t("draw")
                    }
                    state.isAiThinking -> LocaleManager.t("ai_thinking")
                    state.currentTurn == 1 -> LocaleManager.t("black_turn")
                    else -> LocaleManager.t("white_turn")
                },
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(4.dp).defaultMinSize(minHeight = 4.dp))

            Board(
                boardState = state.board,
                wonPositions = state.wonPositions,
                lastMove = state.moveHistory.lastOrNull(),
                onCellClick = onCellClick,
                enabled = !state.isAiThinking && state.gameResult == null,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onUndo, enabled = state.moveHistory.isNotEmpty() && state.gameResult == null && !state.isAiThinking) {
                    Text(LocaleManager.t("undo"))
                }
                OutlinedButton(onClick = onRedo, enabled = state.undoStack.isNotEmpty() && state.gameResult == null && !state.isAiThinking) {
                    Text(LocaleManager.t("redo"))
                }
                OutlinedButton(onClick = onNewGame) {
                    Text(LocaleManager.t("menu"))
                }
            }
        }

        AnimatedVisibility(
            visible = state.isAiThinking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}