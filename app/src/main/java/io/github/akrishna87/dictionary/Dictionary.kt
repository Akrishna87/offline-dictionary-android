package io.github.akrishna87.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where a definition came from. [code] is what `tools/build_db.py` stores in the database. */
enum class Source(val code: String, val label: String, val blurb: String) {
    WordNet("wn", "WordNet", "Princeton WordNet 3.0, modern definitions and synonyms"),
    Wiktionary("wikt", "Wiktionary", "Wiktionary, the free dictionary (CC BY-SA)"),
    Webster("web", "Webster's 1913", "Webster's Unabridged Dictionary, 1913 (public domain)"),
    ;

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: WordNet
    }
}

data class Definition(
    val source: Source,
    val partOfSpeech: String,
    val text: String,
    val example: String,
    val synonyms: List<String>,
    val antonyms: List<String>,
    /** Words with a similar (but not the same) meaning: WordNet's "similar to" links. */
    val similar: List<String>,
    /** Hypernyms: more general terms (dog → canine). */
    val broader: List<String>,
    /** Hyponyms: more specific terms (dog → puppy). */
    val narrower: List<String>,
)

data class WordEntry(
    val word: String,
    val pronunciation: String?,
    val definitions: List<Definition>,
    /** The form the user typed when it was an inflection (e.g. "mice" → "mouse"). */
    val redirectedFrom: String?,
) {
    /** All synonyms across every sense, the ones from the most common senses first. */
    val allSynonyms: List<String> get() = definitions.flatMap { it.synonyms }.distinct()
    val allAntonyms: List<String> get() = definitions.flatMap { it.antonyms }.distinct()
}

/** A word in the search results; [common] ones are listed first. */
data class Suggestion(val word: String, val common: Boolean)

/**
 * The offline dictionary: one SQLite file shipped in the APK's assets and copied to
 * private storage on first launch (and again when the app updates, since the data may have).
 */
class DictionaryRepository(private val context: Context) {
    private val db: SQLiteDatabase by lazy { open() }

    private fun open(): SQLiteDatabase {
        val file = context.getDatabasePath(DB_NAME)
        val version = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        )
        val stamp = File(file.parentFile, "$DB_NAME.version")
        if (!file.exists() || stamp.takeIf { it.exists() }?.readText() != version.toString()) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$DB_NAME.part")
            context.assets.open(DB_NAME).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
            stamp.writeText(version.toString())
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /** Words that start with [query], common words first, then shorter ones. */
    suspend fun prefix(query: String, limit: Int = 50): List<Suggestion> = withContext(Dispatchers.IO) {
        val q = normalize(query)
        if (q.isEmpty()) return@withContext emptyList()
        db.rawQuery(
            "SELECT word, common FROM words WHERE lc >= ? AND lc < ? ORDER BY common DESC, length(lc), lc LIMIT ?",
            arrayOf(q, q + '￿', limit.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Suggestion(c.getString(0), c.getInt(1) == 1)) }
        }
    }

    suspend fun lookup(query: String): WordEntry? = withContext(Dispatchers.IO) {
        val typed = normalize(query)
        if (typed.isEmpty()) return@withContext null
        val direct = find(typed)
        if (direct != null) return@withContext entryFor(direct, null)
        val lemma = scalar("SELECT lemma FROM forms WHERE form = ?", typed)
            ?: guessLemma(typed)
        lemma?.let { find(it) }?.let { entryFor(it, typed) }
    }

    /** Close spellings for a word that isn't in the dictionary (edit distance up to 2). */
    suspend fun similar(query: String, limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val q = normalize(query)
        if (q.length < 2) return@withContext emptyList()
        val first = q.substring(0, 1)
        val scored = ArrayList<Triple<Int, Int, String>>()
        db.rawQuery(
            "SELECT word, lc, common FROM words WHERE lc >= ? AND lc < ? AND length(lc) BETWEEN ? AND ?",
            arrayOf(first, first + '￿', (q.length - 2).toString(), (q.length + 2).toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val d = editDistance(q, c.getString(1), 2)
                if (d <= 2) scored += Triple(d, 1 - c.getInt(2), c.getString(0))
            }
        }
        scored.sortedWith(compareBy({ it.first }, { it.second }, { it.third })).take(limit).map { it.third }
    }

    /** A different common word every day; the same all day. */
    suspend fun wordOfTheDay(epochDay: Long): String? = withContext(Dispatchers.IO) {
        val count = scalar("SELECT COUNT(*) FROM words WHERE common = 1 AND length(lc) >= 5 AND lc NOT LIKE '% %'")
            ?.toLongOrNull() ?: return@withContext null
        if (count == 0L) return@withContext null
        val index = Math.floorMod(epochDay * 2654435761L, count)
        scalar("SELECT word FROM words WHERE common = 1 AND length(lc) >= 5 AND lc NOT LIKE '% %' LIMIT 1 OFFSET ?", index.toString())
    }

    suspend fun randomWord(): String? = withContext(Dispatchers.IO) {
        scalar("SELECT word FROM words WHERE common = 1 AND lc NOT LIKE '% %' ORDER BY RANDOM() LIMIT 1")
    }

    private fun find(lc: String): Long? = scalar("SELECT id FROM words WHERE lc = ?", lc)?.toLong()

    private fun entryFor(id: Long, redirectedFrom: String?): WordEntry? {
        var word = ""
        var ipa: String? = null
        db.rawQuery("SELECT word, ipa FROM words WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            word = c.getString(0)
            ipa = c.getString(1)
        }
        val defs = db.rawQuery(
            "SELECT source, pos, text, example, syns, ants, similar, broader, narrower FROM defs WHERE word_id = ? ORDER BY ord",
            arrayOf(id.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Definition(
                            source = Source.of(c.getString(0)),
                            partOfSpeech = c.getString(1),
                            text = c.getString(2),
                            example = c.getString(3),
                            synonyms = c.words(4),
                            antonyms = c.words(5),
                            similar = c.words(6),
                            broader = c.words(7),
                            narrower = c.words(8),
                        ),
                    )
                }
            }
        }
        return WordEntry(word, ipa, defs, redirectedFrom)
    }

    /** For regular inflections that WordNet's exception list doesn't cover: walked, boxes, cities, running. */
    private fun guessLemma(w: String): String? {
        val candidates = buildList {
            if (w.endsWith("ies")) add(w.dropLast(3) + "y")
            if (w.endsWith("es")) add(w.dropLast(2))
            if (w.endsWith("s")) add(w.dropLast(1))
            if (w.endsWith("ied")) add(w.dropLast(3) + "y")
            if (w.endsWith("ed")) { add(w.dropLast(2)); add(w.dropLast(1)) }
            if (w.endsWith("ing")) {
                val stem = w.dropLast(3)
                add(stem); add(stem + "e")
                if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
            }
            if (w.endsWith("er")) { add(w.dropLast(2)); add(w.dropLast(1)) }
            if (w.endsWith("est")) { add(w.dropLast(3)); add(w.dropLast(2)) }
            if (w.endsWith("ly")) add(w.dropLast(2))
        }
        return candidates.firstOrNull { it.length > 1 && find(it) != null }
    }

    private fun android.database.Cursor.words(column: Int) =
        getString(column).split(", ").filter { it.isNotBlank() }

    private fun scalar(sql: String, vararg args: String): String? =
        db.rawQuery(sql, args.takeIf { it.isNotEmpty() }).use { if (it.moveToFirst()) it.getString(0) else null }

    companion object {
        private const val DB_NAME = "dictionary.db"

        fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

        /** Levenshtein distance, giving up (returning max+1) once it must exceed [max]. */
        fun editDistance(a: String, b: String, max: Int): Int {
            if (kotlin.math.abs(a.length - b.length) > max) return max + 1
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin > max) return max + 1
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
