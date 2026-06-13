package com.gobang

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.gobang.engine.Opening
import com.gobang.model.*
import com.gobang.ui.screen.GameScreen
import com.gobang.ui.screen.MainMenuScreen
import com.gobang.ui.screen.OpeningChoice
import com.gobang.viewmodel.GameViewModel
import com.gobang.storage.GameStateRepository
import kotlinx.coroutines.launch

/** 共享的屏幕导航和 ViewModel 连接逻辑（各平台复用） */
@Composable
fun AppContentImpl(modifier: Modifier = Modifier, repository: GameStateRepository? = null) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    var gameMode by remember { mutableStateOf(GameMode.PvAI) }
    var difficulty by remember { mutableStateOf(Difficulty.Medium) }
    var selectedOpening by remember { mutableStateOf<OpeningChoice>(OpeningChoice.None) }
    var hasSavedGame by remember { mutableStateOf(false) }
    val viewModel = remember { GameViewModel(repository = repository) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        hasSavedGame = repository != null && repository.loadGame() != null
    }

    when (val screen = currentScreen) {
        is Screen.MainMenu -> {
            MainMenuScreen(
                selectedMode = gameMode,
                selectedDifficulty = difficulty,
                selectedOpening = selectedOpening,
                onModeChange = { gameMode = it },
                onDifficultyChange = { difficulty = it },
                onOpeningChange = { selectedOpening = it },
                onNewGame = { mode, diff, opening ->
                    gameMode = mode
                    difficulty = diff
                    viewModel.newGame(mode, diff, opening)
                    currentScreen = Screen.Game(mode, diff)
                },
                onContinue = {
                    scope.launch {
                        viewModel.loadGame()
                        currentScreen = Screen.Game(gameMode, difficulty)
                    }
                },
                hasSavedGame = hasSavedGame,
                modifier = modifier,
            )
        }
        is Screen.Game -> {
            val state by viewModel.state.collectAsState()

            // 监听 AI 思考状态，触发计算
            LaunchedEffect(state.isAiThinking) {
                if (state.isAiThinking) {
                    viewModel.computeAiMove()
                }
            }

            GameScreen(
                state = state,
                onCellClick = { row, col -> viewModel.handleUserMove(row, col) },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onNewGame = { currentScreen = Screen.MainMenu },
                onBack = { currentScreen = Screen.MainMenu },
                modifier = modifier,
            )
        }
    }
}