package com.gobang.storage

import android.content.Context
import com.gobang.storage.json

class AndroidGameStateRepository private constructor(context: Context) : GameStateRepository {

    companion object {
        private const val PREFS_NAME = "gobang_save"
        private const val KEY_SAVED_GAME = "saved_game_json"

        fun create(context: Context): AndroidGameStateRepository {
            return AndroidGameStateRepository(context.applicationContext)
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun saveGame(savedGame: SavedGame) {
        val jsonString = json.encodeToString(SavedGame.serializer(), savedGame)
        prefs.edit().putString(KEY_SAVED_GAME, jsonString).apply()
    }

    override suspend fun loadGame(): SavedGame? {
        val jsonString = prefs.getString(KEY_SAVED_GAME, null) ?: return null
        return try {
            json.decodeFromString(SavedGame.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSave() {
        prefs.edit().remove(KEY_SAVED_GAME).apply()
    }
}