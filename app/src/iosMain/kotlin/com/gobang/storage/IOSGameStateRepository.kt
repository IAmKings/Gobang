package com.gobang.storage

import com.gobang.storage.json
import platform.Foundation.NSUserDefaults

class IOSGameStateRepository : GameStateRepository {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "gobang_saved_game"

    override suspend fun saveGame(savedGame: SavedGame) {
        val jsonString = json.encodeToString(SavedGame.serializer(), savedGame)
        defaults.setObject(jsonString, forKey = key)
    }

    override suspend fun loadGame(): SavedGame? {
        val jsonString = defaults.stringForKey(key) ?: return null
        return try {
            json.decodeFromString(SavedGame.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSave() {
        defaults.removeObjectForKey(key)
    }
}