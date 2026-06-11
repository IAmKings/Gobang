package com.gobang.ui.screen

/** 设置界面：语言切换和难度选择 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gobang.i18n.LocaleManager
import com.gobang.model.Difficulty

@Composable
fun SettingsScreen(
    currentDifficulty: Difficulty,
    onDifficultyChange: (Difficulty) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(LocaleManager.t("settings"), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text(LocaleManager.t("language"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = LocaleManager.currentLanguage == "zh",
                onClick = { LocaleManager.setLanguage("zh") },
                label = { Text(LocaleManager.t("lang_zh")) }
            )
            FilterChip(
                selected = LocaleManager.currentLanguage == "en",
                onClick = { LocaleManager.setLanguage("en") },
                label = { Text(LocaleManager.t("lang_en")) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(LocaleManager.t("difficulty"), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Difficulty.entries.forEach { diff ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = currentDifficulty == diff,
                            onClick = { onDifficultyChange(diff) }
                        )
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentDifficulty == diff, onClick = { onDifficultyChange(diff) })
                    Column {
                        Text(
                            when (diff) {
                                Difficulty.Easy -> LocaleManager.t("diff_easy")
                                Difficulty.Medium -> LocaleManager.t("diff_medium")
                                Difficulty.Hard -> LocaleManager.t("diff_hard")
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            when (diff) {
                                Difficulty.Easy -> LocaleManager.t("diff_easy_desc")
                                Difficulty.Medium -> LocaleManager.t("diff_medium_desc")
                                Difficulty.Hard -> LocaleManager.t("diff_hard_desc")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(LocaleManager.t("back"))
        }
    }
}