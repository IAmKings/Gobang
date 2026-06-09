package com.gobang.storage

import com.gobang.storage.json
import kotlinx.browser.window

class WasmGameStateRepository : GameStateRepository {

    private val key = "gobang_saved_game"

    override suspend fun saveGame(savedGame: SavedGame) {
        val jsonString = json.encodeToString(SavedGame.serializer(), savedGame)
        window.localStorage.setItem(key, jsonString)
    }

    override suspend fun loadGame(): SavedGame? {
        val jsonString = window.localStorage.getItem(key) ?: return null
        return try {
            json.decodeFromString(SavedGame.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSave() {
        window.localStorage.removeItem(key)
    }
}