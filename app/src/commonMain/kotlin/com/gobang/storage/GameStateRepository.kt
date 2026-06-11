package com.gobang.storage

import kotlinx.serialization.json.Json

/** 游戏存档仓库接口，各平台分别实现持久化 */
interface GameStateRepository {
    suspend fun saveGame(savedGame: SavedGame)
    suspend fun loadGame(): SavedGame?
    suspend fun clearSave()
}

/** 全局 JSON 配置，忽略未知字段以保证向前兼容 */
val json = Json { ignoreUnknownKeys = true }