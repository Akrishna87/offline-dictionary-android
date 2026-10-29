package io.github.akrishna87.dictionary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dictionary.DictionaryViewModel
import io.github.akrishna87.dictionary.LibraryPage
import io.github.akrishna87.dictionary.Shelf
import io.github.akrishna87.dictionary.UserData
import io.github.akrishna87.dictionary.WordEntry
import kotlinx.coroutines.launch

/** The Library tab: shelves of saved words, the dictionary browsable A–Z, and word review. */
@Composable
fun LibraryScreen(vm: DictionaryViewModel) {
    val page = vm.libraryPage
    BackHandler(enabled = page != LibraryPage.Home) {
        vm.libraryPage = if (page is LibraryPage.Letter) LibraryPage.Browse else LibraryPage.Home
    }
    when (page) {
        LibraryPage.Home -> LibraryHome(vm)
        is LibraryPage.OfShelf -> ShelfPage(vm, page.name)
        LibraryPage.Browse -> BrowsePage(vm)
        is LibraryPage.Letter -> LetterPage(vm, page.letter)
        LibraryPage.Review -> ReviewPage(vm)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else {
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
private fun LibraryHome(vm: DictionaryViewModel) {
    var newShelf by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Shelf?>(null) }
    val toReview = vm.shelves.flatMap { it.words }.distinctBy { it.lowercase() }.size

    Column(Modifier.fillMaxSize()) {
        PageTitle("Library", "${vm.shelves.sumOf { it.words.size }} words on your shelves")
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigTile(Icons.Default.SortByAlpha, "Browse A–Z", "The whole dictionary", Modifier.weight(1f)) {
                        vm.libraryPage = LibraryPage.Browse
                    }
                    BigTile(
                        Icons.Default.Style, "Review",
                        if (toReview == 0) "Shelve words first" else "Flashcards for $toReview words",
                        Modifier.weight(1f), enabled = toReview > 0,
                    ) { vm.libraryPage = LibraryPage.Review }
                }
            }
            item {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Shelves", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { newShelf = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.size(4.dp))
                        Text("New shelf")
                    }
                }
            }
            items(vm.shelves, key = { it.name }) { shelf ->
                Surface(
                    onClick = { vm.libraryPage = LibraryPage.OfShelf(shelf.name) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(shelf.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (shelf.words.isEmpty()) "Empty" else shelf.words.take(4).joinToString(", ") + if (shelf.words.size > 4) "…" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("${shelf.words.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (shelf.name != UserData.DEFAULT_SHELF) {
                            IconButton(onClick = { deleting = shelf }) { Icon(Icons.Default.Delete, "Delete shelf") }
                        } else {
                            Spacer(Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
    if (newShelf) NewShelfDialog(vm, onDone = { newShelf = false })
    deleting?.let { shelf ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${shelf.name}”?") },
            text = { Text("The shelf goes away. The words stay in the dictionary, and on any other shelf they are on.") },
            confirmButton = { TextButton(onClick = { vm.deleteShelf(shelf.name); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BigTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = if (enabled) Palette.Coral else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NewShelfDialog(vm: DictionaryViewModel, onDone: () -> Unit, onCreated: (String) -> Unit = {}) {
    var name by remember { mutableStateOf("") }
    var taken by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("New shelf") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; taken = false },
                label = { Text("Name, e.g. “Exam words”") },
                singleLine = true,
                isError = taken,
                supportingText = if (taken) ({ Text("You already have a shelf with that name") }) else null,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (vm.createShelf(name)) {
                        onCreated(name.trim().replace(Regex("\\s+"), " "))
                        onDone()
                    } else {
                        taken = true
                    }
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** "Add to shelf": tick the shelves a word should be on. */
@Composable
fun ShelfPickerDialog(vm: DictionaryViewModel, word: String, onDone: () -> Unit) {
    var newShelf by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Shelve “$word”") },
        text = {
            LazyColumn {
                items(vm.shelves, key = { it.name }) { shelf ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleOnShelf(shelf.name, word) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = vm.isOnShelf(shelf.name, word), onCheckedChange = { vm.toggleOnShelf(shelf.name, word) })
                        Text(shelf.name, Modifier.weight(1f))
                        Text("${shelf.words.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    TextButton(onClick = { newShelf = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.size(4.dp))
                        Text("New shelf")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
    if (newShelf) {
        NewShelfDialog(vm, onDone = { newShelf = false }, onCreated = { vm.toggleOnShelf(it, word) })
    }
}

@Composable
private fun ShelfPage(vm: DictionaryViewModel, name: String) {
    val shelf = vm.shelves.firstOrNull { it.name == name }
    Column(Modifier.fillMaxSize()) {
        PageTitle(name, "${shelf?.words?.size ?: 0} words", onBack = { vm.libraryPage = LibraryPage.Home })
        if (shelf == null || shelf.words.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing here yet. Open a word and tap the shelf button to add it.",
                    Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shelf.words, key = { it }) { word ->
                Row(Modifier.fillMaxWidth().clickable { vm.open(word) }.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(word, style = MaterialTheme.typography.titleMedium)
                        val note = vm.notes[word.lowercase()]
                        if (note != null) {
                            Text(note, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LevelDots(vm.levelOf(word))
                    IconButton(onClick = { vm.toggleOnShelf(name, word) }) { Icon(Icons.Default.Close, "Remove from shelf") }
                }
            }
        }
    }
}

/** Five dots: how well the word is known from review. */
@Composable
private fun LevelDots(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(UserData.MAX_LEVEL) { i ->
            Surface(
                shape = RoundedCornerShape(50),
                color = if (i < level) Palette.Violet else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(6.dp),
            ) {}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowsePage(vm: DictionaryViewModel) {
    Column(Modifier.fillMaxSize()) {
        PageTitle("Browse A–Z", "Pick a letter", onBack = { vm.libraryPage = LibraryPage.Home })
        FlowRow(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ('A'..'Z').forEach { c ->
                FilledTonalButton(
                    onClick = { vm.libraryPage = LibraryPage.Letter(c.toString()) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp),
                ) { Text("$c", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

private const val PAGE_SIZE = 200

/** Every headword under one letter, loading more as you scroll. */
@Composable
private fun LetterPage(vm: DictionaryViewModel, letter: String) {
    val words = remember(letter) { mutableStateListOf<String>() }
    var finished by remember(letter) { mutableStateOf(false) }
    var loading by remember(letter) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (loading || finished) return
        loading = true
        scope.launch {
            val more = vm.lettersPage(letter, words.size, PAGE_SIZE)
            words.addAll(more)
            if (more.size < PAGE_SIZE) finished = true
            loading = false
        }
    }
    LaunchedEffect(letter) { loadMore() }

    Column(Modifier.fillMaxSize()) {
        PageTitle(letter, if (finished) "${words.size} words" else "Browsing", onBack = { vm.libraryPage = LibraryPage.Browse })
        LazyColumn {
            itemsIndexed(words, key = { _, w -> w }) { i, w ->
                if (i >= words.size - 30) LaunchedEffect(words.size) { loadMore() }
                WordRow(w, onClick = { vm.open(w) })
            }
        }
    }
}

/** A short flashcard session over the words on your shelves. */
@Composable
private fun ReviewPage(vm: DictionaryViewModel) {
    val queue = remember { vm.reviewQueue() }
    var index by remember { mutableIntStateOf(0) }
    var known by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<WordEntry?>(null) }
    val word = queue.getOrNull(index)
    LaunchedEffect(word) {
        entry = null
        if (word != null) entry = vm.preview(word)
    }

    Column(Modifier.fillMaxSize()) {
        PageTitle("Review", if (word != null) "Card ${index + 1} of ${queue.size}" else null, onBack = { vm.libraryPage = LibraryPage.Home })
        if (word == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (queue.isEmpty()) "Nothing to review yet." else "Done! You knew $known of ${queue.size}.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.libraryPage = LibraryPage.Home }) { Text("Back to library") }
            }
            return
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { revealed = true },
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    entry?.pronunciation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(24.dp))
                    if (revealed) {
                        entry?.definitions?.take(2)?.forEach {
                            Text(
                                (if (it.partOfSpeech.isNotEmpty()) "${it.partOfSpeech}: " else "") + it.text,
                                Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        vm.notes[word.lowercase()]?.let {
                            Text("My note: $it", color = Palette.Coral)
                        }
                    } else {
                        Text("Tap to show the meaning", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (revealed) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { vm.recordReview(word, false); index++; revealed = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("Again") }
                    Button(
                        onClick = { vm.recordReview(word, true); known++; index++; revealed = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("Got it") }
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
