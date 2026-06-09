package com.gobang.storage

import kotlinx.serialization.json.Json

interface GameStateRepository {
    suspend fun saveGame(savedGame: SavedGame)
    suspend fun loadGame(): SavedGame?
    suspend fun clearSave()
}

val json = Json { ignoreUnknownKeys = true }