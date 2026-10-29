package io.github.akrishna87.dictionary

import android.content.Context
import androidx.core.content.edit

/** Saved words and search history, kept in the app's SharedPreferences (newest first). */
class UserData(context: Context) {
    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    fun favourites(): List<String> = read(FAVOURITES)
    fun history(): List<String> = read(HISTORY)

    fun toggleFavourite(word: String): List<String> {
        val list = favourites().toMutableList()
        if (!list.removeAll { it.equals(word, ignoreCase = true) }) list.add(0, word)
        write(FAVOURITES, list)
        return list
    }

    fun addHistory(word: String): List<String> {
        val list = history().toMutableList()
        list.removeAll { it.equals(word, ignoreCase = true) }
        list.add(0, word)
        val trimmed = list.take(MAX_HISTORY)
        write(HISTORY, trimmed)
        return trimmed
    }

    fun clearHistory() = write(HISTORY, emptyList())

    private fun read(key: String): List<String> =
        prefs.getString(key, "")!!.split('\n').filter { it.isNotBlank() }

    private fun write(key: String, list: List<String>) = prefs.edit { putString(key, list.joinToString("\n")) }

    private companion object {
        const val FAVOURITES = "favourites"
        const val HISTORY = "history"
        const val MAX_HISTORY = 100
    }
}
