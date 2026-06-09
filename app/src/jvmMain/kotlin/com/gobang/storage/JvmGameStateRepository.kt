package com.gobang.storage

import com.gobang.storage.json
import java.io.File

class JvmGameStateRepository : GameStateRepository {

    private val saveFile = File(System.getProperty("user.home"), ".gobang/save.json")

    override suspend fun saveGame(savedGame: SavedGame) {
        saveFile.parentFile?.mkdirs()
        val jsonString = json.encodeToString(SavedGame.serializer(), savedGame)
        saveFile.writeText(jsonString)
    }

    override suspend fun loadGame(): SavedGame? {
        if (!saveFile.exists()) return null
        val jsonString = saveFile.readText()
        return try {
            json.decodeFromString(SavedGame.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clearSave() {
        saveFile.delete()
    }
}