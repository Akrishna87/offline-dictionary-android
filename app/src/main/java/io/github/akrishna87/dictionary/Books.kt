package io.github.akrishna87.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val chapters: Int,
    val words: Int,
    /** The chapter a new reader opens on: the first real chapter, past any preface. */
    val start: Int,
) {
    /** Reading time at an easy 230 words a minute. */
    val minutes: Int get() = (words / 230).coerceAtLeast(1)

    /** Each book gets its own cover colour, always the same one. */
    val hue: Float get() = ((id * 47) % 360).toFloat()
}

data class ChapterInfo(val index: Int, val title: String)

/** Where the reader stopped: [percent] is the whole book, [paragraph] is the first one on screen. */
data class Progress(val chapter: Int, val paragraph: Int, val percent: Int, val at: Long)

enum class ReaderTheme(val label: String) { Night("Night"), Sepia("Sepia"), Paper("Paper") }

data class ReaderSettings(val fontSize: Int = 19, val theme: ReaderTheme = ReaderTheme.Night) {
    companion object {
        const val MIN_SIZE = 14
        const val MAX_SIZE = 30
    }
}

/** The pocket library: public-domain books from `library.db`, built by `tools/build_books.py`. */
class BookRepository(private val context: Context) {
    // A build without the library file just shows no books.
    private val db: SQLiteDatabase? by lazy { runCatching { openAssetDatabase(context, "library.db") }.getOrNull() }

    suspend fun books(): List<Book> = withContext(Dispatchers.IO) {
        db?.rawQuery("SELECT id, title, author, chapters, words, start FROM books ORDER BY title", null)?.use { c ->
            buildList { while (c.moveToNext()) add(Book(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5))) }
        } ?: emptyList()
    }

    suspend fun chapters(bookId: Int): List<ChapterInfo> = withContext(Dispatchers.IO) {
        db?.rawQuery("SELECT idx, title FROM chapters WHERE book_id = ? ORDER BY idx", arrayOf(bookId.toString()))?.use { c ->
            buildList { while (c.moveToNext()) add(ChapterInfo(c.getInt(0), c.getString(1))) }
        } ?: emptyList()
    }

    suspend fun paragraphs(bookId: Int, chapter: Int): List<String> = withContext(Dispatchers.IO) {
        db?.rawQuery("SELECT body FROM chapters WHERE book_id = ? AND idx = ?", arrayOf(bookId.toString(), chapter.toString()))
            ?.use { c -> if (c.moveToFirst()) c.getString(0).split("\n\n").filter { it.isNotBlank() } else emptyList() }
            ?: emptyList()
    }
}
