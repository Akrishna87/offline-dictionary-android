package io.github.akrishna87.dictionary

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A named collection of words in the user's library. Newest words first. */
data class Shelf(val name: String, val words: List<String>)

/**
 * Everything personal, kept in the app's SharedPreferences: search history, the library's
 * shelves, notes on words, and how well each word is known (for review).
 */
class UserData(context: Context) {
    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    // --- Search history -------------------------------------------------------

    fun history(): List<String> = readLines(HISTORY)

    fun addHistory(word: String): List<String> {
        val list = history().toMutableList()
        list.removeAll { it.equals(word, ignoreCase = true) }
        list.add(0, word)
        val trimmed = list.take(MAX_HISTORY)
        prefs.edit { putString(HISTORY, trimmed.joinToString("\n")) }
        return trimmed
    }

    fun clearHistory() = prefs.edit { putString(HISTORY, "") }

    // --- Shelves --------------------------------------------------------------

    /** The default shelf ([DEFAULT_SHELF]) is always first. Words starred before shelves existed move into it. */
    fun shelves(): List<Shelf> {
        val raw = prefs.getString(SHELVES, null)
        val list = if (raw != null) {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Shelf(o.getString("name"), o.getJSONArray("words").let { w -> (0 until w.length()).map(w::getString) })
            }
        } else {
            listOf(Shelf(DEFAULT_SHELF, readLines("favourites")))
        }
        return if (list.any { it.name == DEFAULT_SHELF }) list else listOf(Shelf(DEFAULT_SHELF, emptyList())) + list
    }

    fun toggleInShelf(shelf: String, word: String): List<Shelf> = update { list ->
        list.map { s ->
            if (s.name != shelf) s
            else if (s.words.any { it.equals(word, ignoreCase = true) }) s.copy(words = s.words.filterNot { it.equals(word, ignoreCase = true) })
            else s.copy(words = listOf(word) + s.words)
        }
    }

    /** Returns null (and changes nothing) when the name is blank or already used. */
    fun createShelf(name: String): List<Shelf>? {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty() || shelves().any { it.name.equals(clean, ignoreCase = true) }) return null
        return update { it + Shelf(clean, emptyList()) }
    }

    fun deleteShelf(name: String): List<Shelf> = update { list -> list.filterNot { it.name == name && name != DEFAULT_SHELF } }

    private fun update(change: (List<Shelf>) -> List<Shelf>): List<Shelf> {
        val list = change(shelves())
        val array = JSONArray()
        list.forEach { s ->
            array.put(JSONObject().put("name", s.name).put("words", JSONArray(s.words)))
        }
        prefs.edit { putString(SHELVES, array.toString()) }
        return list
    }

    // --- Notes ----------------------------------------------------------------

    fun notes(): Map<String, String> = readMap(NOTES) { it.toString() }

    fun setNote(word: String, text: String): Map<String, String> {
        val map = notes().toMutableMap()
        if (text.isBlank()) map.remove(word.lowercase()) else map[word.lowercase()] = text
        prefs.edit { putString(NOTES, JSONObject(map as Map<*, *>).toString()) }
        return map
    }

    // --- Review ---------------------------------------------------------------

    /** 0 = new or forgotten … [MAX_LEVEL] = well known. */
    fun levels(): Map<String, Int> = readMap(LEVELS) { (it as Number).toInt() }

    fun recordReview(word: String, known: Boolean): Map<String, Int> {
        val map = levels().toMutableMap()
        val key = word.lowercase()
        map[key] = if (known) minOf((map[key] ?: 0) + 1, MAX_LEVEL) else 0
        prefs.edit { putString(LEVELS, JSONObject(map as Map<*, *>).toString()) }
        return map
    }

    // --- Books -----------------------------------------------------------------

    fun progress(): Map<Int, Progress> = readMap(PROGRESS) { it.toString() }.mapNotNull { (id, v) ->
        val p = v.split(",")
        if (p.size == 4) id.toInt() to Progress(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toLong()) else null
    }.toMap()

    fun saveProgress(bookId: Int, p: Progress): Map<Int, Progress> {
        val map = progress().toMutableMap()
        map[bookId] = p
        val o = JSONObject()
        map.forEach { (id, v) -> o.put(id.toString(), "${v.chapter},${v.paragraph},${v.percent},${v.at}") }
        prefs.edit { putString(PROGRESS, o.toString()) }
        return map
    }

    fun readerSettings() = ReaderSettings(
        fontSize = prefs.getInt(READER_SIZE, ReaderSettings().fontSize),
        theme = ReaderTheme.entries.firstOrNull { it.name == prefs.getString(READER_THEME, null) } ?: ReaderTheme.Night,
    )

    fun saveReaderSettings(s: ReaderSettings) = prefs.edit {
        putInt(READER_SIZE, s.fontSize)
        putString(READER_THEME, s.theme.name)
    }

    // --- Helpers --------------------------------------------------------------

    private fun readLines(key: String): List<String> =
        prefs.getString(key, "")!!.split('\n').filter { it.isNotBlank() }

    private fun <T> readMap(key: String, convert: (Any) -> T): Map<String, T> {
        val o = JSONObject(prefs.getString(key, null) ?: "{}")
        return o.keys().asSequence().associateWith { convert(o.get(it)) }
    }

    companion object {
        const val DEFAULT_SHELF = "Saved"
        const val MAX_LEVEL = 5
        private const val HISTORY = "history"
        private const val SHELVES = "shelves"
        private const val NOTES = "notes"
        private const val LEVELS = "levels"
        private const val PROGRESS = "progress"
        private const val READER_SIZE = "reader_size"
        private const val READER_THEME = "reader_theme"
        private const val MAX_HISTORY = 100
    }
}
