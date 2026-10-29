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

class DictionaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DictionaryRepository(app)
    private val user = UserData(app)

    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var detail by mutableStateOf<Detail?>(null)
        private set
    var favourites by mutableStateOf(user.favourites())
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

    fun toggleFavourite(word: String) {
        favourites = user.toggleFavourite(word)
    }

    fun isFavourite(word: String) = favourites.any { it.equals(word, ignoreCase = true) }

    fun toggleSource(source: Source) {
        hiddenSources = if (source in hiddenSources) hiddenSources - source else hiddenSources + source
    }

    fun clearHistory() {
        user.clearHistory()
        history = emptyList()
    }
}
