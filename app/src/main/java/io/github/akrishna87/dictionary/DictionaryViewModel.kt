package io.github.akrishna87.dictionary

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Detail {
    data object Loading : Detail
    data class Found(val entry: WordEntry) : Detail
    data class Missing(val word: String, val similar: List<String>) : Detail
}

/** Which page of the Library tab is showing. Kept here so it survives opening a word and coming back. */
sealed interface LibraryPage {
    data object Home : LibraryPage
    data class OfShelf(val name: String) : LibraryPage
    data object Browse : LibraryPage
    data class Letter(val letter: String) : LibraryPage
    data object Review : LibraryPage
    data object Books : LibraryPage
    data class Reader(val bookId: Int) : LibraryPage
}

class DictionaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DictionaryRepository(app)
    private val user = UserData(app)
    private val bookRepo = BookRepository(app)

    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var detail by mutableStateOf<Detail?>(null)
        private set
    var shelves by mutableStateOf(user.shelves())
        private set
    var notes by mutableStateOf(user.notes())
        private set
    var levels by mutableStateOf(user.levels())
        private set
    var libraryPage by mutableStateOf<LibraryPage>(LibraryPage.Home)
    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var progress by mutableStateOf(user.progress())
        private set
    var reader by mutableStateOf(user.readerSettings())
        private set
    var history by mutableStateOf(user.history())
        private set
    var wordOfTheDay by mutableStateOf<String?>(null)
        private set
    /** Sources hidden by the user on the word page; empty means show everything. */
    var hiddenSources by mutableStateOf<Set<Source>>(emptySet())
        private set

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { books = bookRepo.books() }
        viewModelScope.launch {
            wordOfTheDay = runCatching { repo.wordOfTheDay(LocalDate.now().toEpochDay()) }.getOrNull()
        }
    }

    fun onQueryChange(text: String) {
        query = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(60) // lets fast typing settle; each keystroke would otherwise run a query
            suggestions = repo.prefix(text)
        }
    }

    fun open(word: String) {
        detail = Detail.Loading
        viewModelScope.launch {
            val entry = repo.lookup(word)
            if (entry == null) {
                detail = Detail.Missing(word.trim(), repo.similar(word))
            } else {
                detail = Detail.Found(entry)
                history = user.addHistory(entry.word)
            }
        }
    }

    fun submit() {
        if (query.isNotBlank()) open(query)
    }

    fun openRandom() {
        viewModelScope.launch { repo.randomWord()?.let { open(it) } }
    }

    fun close() {
        detail = null
    }

    fun isSaved(word: String) = isOnShelf(UserData.DEFAULT_SHELF, word)

    fun toggleSaved(word: String) = toggleOnShelf(UserData.DEFAULT_SHELF, word)

    fun isOnShelf(shelf: String, word: String) =
        shelves.firstOrNull { it.name == shelf }?.words?.any { it.equals(word, ignoreCase = true) } == true

    fun toggleOnShelf(shelf: String, word: String) {
        shelves = user.toggleInShelf(shelf, word)
    }

    /** Returns false when the name is blank or taken. */
    fun createShelf(name: String): Boolean {
        shelves = user.createShelf(name) ?: return false
        return true
    }

    fun deleteShelf(name: String) {
        shelves = user.deleteShelf(name)
    }

    fun setNote(word: String, text: String) {
        notes = user.setNote(word, text)
    }

    /** Level of a word for review: 0 (new) to [UserData.MAX_LEVEL]. */
    fun levelOf(word: String) = levels[word.lowercase()] ?: 0

    /** Up to [size] shelved words to review: the least known first, in random order within a level. */
    fun reviewQueue(size: Int = 10): List<String> =
        shelves.flatMap { it.words }.distinctBy { it.lowercase() }.shuffled().sortedBy { levelOf(it) }.take(size)

    fun recordReview(word: String, known: Boolean) {
        levels = user.recordReview(word, known)
    }

    /** The book read most recently, if any. */
    val continueReading: Book?
        get() = progress.maxByOrNull { it.value.at }?.key?.let { id -> books.firstOrNull { it.id == id } }

    fun saveProgress(bookId: Int, chapter: Int, paragraph: Int, percent: Int) {
        progress = user.saveProgress(bookId, Progress(chapter, paragraph, percent, System.currentTimeMillis()))
    }

    fun setReader(settings: ReaderSettings) {
        reader = settings
        user.saveReaderSettings(settings)
    }

    suspend fun chapters(bookId: Int) = bookRepo.chapters(bookId)

    suspend fun paragraphs(bookId: Int, chapter: Int) = bookRepo.paragraphs(bookId, chapter)

    suspend fun preview(word: String) = repo.lookup(word)

    suspend fun lettersPage(letter: String, offset: Int, limit: Int) = repo.page(letter, offset, limit)

    fun toggleSource(source: Source) {
        hiddenSources = if (source in hiddenSources) hiddenSources - source else hiddenSources + source
    }

    fun clearHistory() {
        user.clearHistory()
        history = emptyList()
    }
}
