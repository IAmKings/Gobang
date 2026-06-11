package com.gobang.ui.screen

/** 主菜单界面：选择游戏模式、难度，开始/继续游戏 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gobang.i18n.LocaleManager
import com.gobang.model.Difficulty
import com.gobang.model.GameMode

@Composable
fun MainMenuScreen(
    onNewGame: (GameMode, Difficulty) -> Unit,
    onContinue: () -> Unit,
    hasSavedGame: Boolean,
    modifier: Modifier = Modifier,
) {
    val lang by LocaleManager.language.collectAsState()
    var selectedMode by remember { mutableStateOf(GameMode.PvAI) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.Medium) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            currentDifficulty = selectedDifficulty,
            onDifficultyChange = { selectedDifficulty = it },
            onBack = { showSettings = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = LocaleManager.t("app_title"),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = LocaleManager.t("app_subtitle"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { LocaleManager.toggleLanguage() }) {
            Text(if (LocaleManager.currentLanguage == "zh") "中/EN" else "EN/中")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(LocaleManager.t("game_mode"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GameMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                    Text(
                        text = when (mode) {
                            GameMode.PvAI -> LocaleManager.t("mode_pvai")
                            GameMode.AIvP -> LocaleManager.t("mode_aivp")
                            GameMode.PvP -> LocaleManager.t("mode_pvp")
                            GameMode.AIvAI -> LocaleManager.t("mode_aivai")
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(LocaleManager.t("difficulty"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { diff ->
                FilterChip(
                    selected = selectedDifficulty == diff,
                    onClick = { selectedDifficulty = diff },
                    enabled = selectedMode != GameMode.PvP,
                    label = {
                        Text(when (diff) {
                            Difficulty.Easy -> LocaleManager.t("diff_easy")
                            Difficulty.Medium -> LocaleManager.t("diff_medium")
                            Difficulty.Hard -> LocaleManager.t("diff_hard")
                        })
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onNewGame(selectedMode, selectedDifficulty) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(LocaleManager.t("start_game"))
        }

        if (hasSavedGame) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(LocaleManager.t("continue_game"))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(LocaleManager.t("settings"))
        }
    }
}