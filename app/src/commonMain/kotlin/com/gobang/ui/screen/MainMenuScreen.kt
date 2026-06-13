@file:OptIn(ExperimentalMaterial3Api::class)

package com.gobang.ui.screen

/** 主菜单界面：选择游戏模式、难度、开局，开始/继续游戏 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gobang.engine.Opening
import com.gobang.engine.OpeningBook
import com.gobang.i18n.LocaleManager
import com.gobang.model.Difficulty
import com.gobang.model.GameMode

/** 开局选项：null=无，"random"=随机，其他=具体开局名 */
sealed class OpeningChoice {
    data object None : OpeningChoice()
    data object Random : OpeningChoice()
    data class Named(val opening: Opening) : OpeningChoice()
}

@Composable
fun MainMenuScreen(
    selectedMode: GameMode,
    selectedDifficulty: Difficulty,
    selectedOpening: OpeningChoice,
    onModeChange: (GameMode) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
    onOpeningChange: (OpeningChoice) -> Unit,
    onNewGame: (GameMode, Difficulty, Opening?) -> Unit,
    onContinue: () -> Unit,
    hasSavedGame: Boolean,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var expandedOpening by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            currentDifficulty = selectedDifficulty,
            onDifficultyChange = onDifficultyChange,
            onBack = { showSettings = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                            onClick = { onModeChange(mode) }
                        )
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedMode == mode, onClick = { onModeChange(mode) })
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

        Text(LocaleManager.t("difficulty_always"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { diff ->
                FilterChip(
                    selected = selectedDifficulty == diff,
                    onClick = { onDifficultyChange(diff) },
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

        Text(LocaleManager.t("opening"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expandedOpening,
            onExpandedChange = { expandedOpening = !expandedOpening }
        ) {
            OutlinedTextField(
                value = when (selectedOpening) {
                    is OpeningChoice.None -> LocaleManager.t("opening_none")
                    is OpeningChoice.Random -> LocaleManager.t("opening_random")
                    is OpeningChoice.Named -> (selectedOpening as OpeningChoice.Named).opening.name
                },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                label = { Text(LocaleManager.t("opening")) }
            )
            ExposedDropdownMenu(
                expanded = expandedOpening,
                onDismissRequest = { expandedOpening = false }
            ) {
                DropdownMenuItem(
                    text = { Text(LocaleManager.t("opening_none")) },
                    onClick = {
                        onOpeningChange(OpeningChoice.None)
                        expandedOpening = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(LocaleManager.t("opening_random")) },
                    onClick = {
                        onOpeningChange(OpeningChoice.Random)
                        expandedOpening = false
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(LocaleManager.t("opening_direct"), style = MaterialTheme.typography.labelSmall) },
                    enabled = false,
                    onClick = {}
                )
                val directNames = setOf("寒星", "溪月", "疏星", "花月", "残月", "雨月", "金星", "松月", "丘月", "新月", "瑞星", "山月", "游星")
                OpeningBook.openings.filter { it.name in directNames }.forEach { opening ->
                    DropdownMenuItem(
                        text = { Text(opening.name) },
                        onClick = {
                            onOpeningChange(OpeningChoice.Named(opening))
                            expandedOpening = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(LocaleManager.t("opening_indirect"), style = MaterialTheme.typography.labelSmall) },
                    enabled = false,
                    onClick = {}
                )
                val indirectNames = setOf("长星", "峡月", "恒星", "水月", "流星", "云月", "浦月", "岚月", "银月", "明星", "斜月", "名月", "彗星")
                OpeningBook.openings.filter { it.name in indirectNames }.forEach { opening ->
                    DropdownMenuItem(
                        text = { Text(opening.name) },
                        onClick = {
                            onOpeningChange(OpeningChoice.Named(opening))
                            expandedOpening = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val opening = when (selectedOpening) {
                    is OpeningChoice.None -> null
                    is OpeningChoice.Random -> OpeningBook.randomOpening()
                    is OpeningChoice.Named -> (selectedOpening as OpeningChoice.Named).opening
                }
                onNewGame(selectedMode, selectedDifficulty, opening)
            },
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