package io.github.akrishna87.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.dictionary.Book
import io.github.akrishna87.dictionary.ChapterInfo
import io.github.akrishna87.dictionary.DictionaryViewModel
import io.github.akrishna87.dictionary.LibraryPage
import io.github.akrishna87.dictionary.ReaderSettings
import io.github.akrishna87.dictionary.ReaderTheme
import io.github.akrishna87.dictionary.WordEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

/** A book cover drawn from the title alone: every book has its own colour. */
@Composable
fun BookCover(book: Book, percent: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val top = Color.hsv(book.hue, 0.5f, 0.45f)
    val bottom = Color.hsv((book.hue + 35f) % 360f, 0.6f, 0.25f)
    Surface(onClick = onClick, modifier = modifier.aspectRatio(0.68f), shape = RoundedCornerShape(8.dp)) {
        Box(Modifier.background(Brush.verticalGradient(listOf(top, bottom)))) {
            Box(Modifier.width(7.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.28f)))
            Column(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.title,
                    color = Color(0xFFF5EBD7),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Column {
                    Text(
                        book.author,
                        color = Color(0xFFF5EBD7).copy(alpha = 0.8f),
                        fontFamily = FontFamily.Serif,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (percent > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF5EBD7),
                            trackColor = Color.Black.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}

/** The "Books" row on the library home: pick up where you left off, or browse the shelf. */
@Composable
fun BooksShelfSection(vm: DictionaryViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Books", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { vm.libraryPage = LibraryPage.Books }) { Text("See all ${vm.books.size}") }
        }
        vm.continueReading?.let { book ->
            val p = vm.progress[book.id]
            Surface(
                onClick = { vm.libraryPage = LibraryPage.Reader(book.id) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCover(book, 0, Modifier.width(56.dp)) { vm.libraryPage = LibraryPage.Reader(book.id) }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("CONTINUE READING", style = MaterialTheme.typography.labelSmall, color = Palette.Coral)
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p?.percent ?: 0}% · chapter ${(p?.chapter ?: 0) + 1} of ${book.chapters}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.books.size, key = { vm.books[it].id }) { i ->
                val book = vm.books[i]
                BookCover(book, vm.progress[book.id]?.percent ?: 0, Modifier.width(118.dp)) {
                    vm.libraryPage = LibraryPage.Reader(book.id)
                }
            }
        }
    }
}

@Composable
fun BooksPage(vm: DictionaryViewModel) {
    var query by remember { mutableStateOf("") }
    val shown = vm.books.filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.author.contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize()) {
        PageTitle("Books", "${vm.books.size} public-domain classics, all offline", onBack = { vm.libraryPage = LibraryPage.Home })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
            placeholder = { Text("Find a title or author") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shown, key = { it.id }) { book ->
                Column {
                    BookCover(book, vm.progress[book.id]?.percent ?: 0) { vm.libraryPage = LibraryPage.Reader(book.id) }
                    Text(
                        "${book.chapters} chapters · ${formatMinutes(book.minutes)}",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatMinutes(m: Int) = if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"

private class ReaderColors(val background: Color, val text: Color, val dim: Color)

private fun colorsFor(theme: ReaderTheme) = when (theme) {
    ReaderTheme.Night -> ReaderColors(Color(0xFF101014), Color(0xFFDAD6CE), Color(0xFF8B8880))
    ReaderTheme.Sepia -> ReaderColors(Color(0xFF2A2018), Color(0xFFE8DAC0), Color(0xFF9C8D74))
    ReaderTheme.Paper -> ReaderColors(Color(0xFFF7F1E3), Color(0xFF2A2620), Color(0xFF7A7467))
}

/** The reader. Tap any word to see its meaning; the page you stopped on is remembered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPage(vm: DictionaryViewModel, bookId: Int) {
    val book = vm.books.firstOrNull { it.id == bookId }
    if (book == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val settings = vm.reader
    val colors = colorsFor(settings.theme)
    var restore by remember(bookId) { mutableStateOf(vm.progress[bookId]) } // used once, to reopen the saved page
    var chapter by remember(bookId) { mutableIntStateOf(restore?.chapter ?: 0) }
    var chapters by remember(bookId) { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var paragraphs by remember(bookId, chapter) { mutableStateOf<List<String>?>(null) }
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var defining by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookId) { chapters = vm.chapters(bookId) }
    LaunchedEffect(bookId, chapter) { paragraphs = vm.paragraphs(bookId, chapter) }

    fun goTo(index: Int) {
        restore = null
        chapter = index
    }
    val chapterTitle = chapters.getOrNull(chapter)?.title ?: ""

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.libraryPage = LibraryPage.Home }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = colors.text)
            }
            Column(Modifier.weight(1f)) {
                Text(book.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(chapterTitle, color = colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showChapters = true }) { Icon(Icons.Default.FormatListBulleted, "Chapters", tint = colors.text) }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.TextFields, "Text size and theme", tint = colors.text) }
        }

        val ps = paragraphs
        if (ps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            key(bookId, chapter) {
                val start = restore?.takeIf { it.chapter == chapter }?.paragraph?.plus(1)?.coerceAtMost(ps.size) ?: 0
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
                LaunchedEffect(listState, chapters.size) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { first ->
                        delay(500) // wait for the scrolling to settle before saving
                        val inChapter = first.toFloat() / (ps.size + 1)
                        val percent = (((chapter + inChapter) / maxOf(chapters.size, 1)) * 100).toInt().coerceIn(0, 100)
                        vm.saveProgress(bookId, chapter, (first - 1).coerceAtLeast(0), percent)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    item(key = "title") {
                        Text(
                            chapterTitle,
                            Modifier.padding(top = 8.dp, bottom = 20.dp),
                            color = colors.text,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = (settings.fontSize + 7).sp,
                            lineHeight = (settings.fontSize + 12).sp,
                        )
                    }
                    itemsIndexed(ps, key = { i, _ -> i }) { _, text ->
                        ParagraphText(text, settings.fontSize, colors.text) { defining = it }
                    }
                    item(key = "nav") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (chapter > 0) OutlinedButton(onClick = { goTo(chapter - 1) }) { Text("Previous") }
                            if (chapter < chapters.size - 1) {
                                Button(onClick = { goTo(chapter + 1) }) { Text("Next chapter") }
                            } else {
                                Text("The end", color = colors.dim, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChapters) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Chapters") },
            text = {
                LazyColumn {
                    itemsIndexed(chapters, key = { _, c -> c.index }) { _, c ->
                        Text(
                            c.title,
                            Modifier.fillMaxWidth().clickable { goTo(c.index); showChapters = false }.padding(vertical = 12.dp),
                            color = if (c.index == chapter) Palette.Coral else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (c.index == chapter) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Close") } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Reading") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", Modifier.weight(1f))
                        OutlinedButton(
                            enabled = settings.fontSize > ReaderSettings.MIN_SIZE,
                            onClick = { vm.setReader(settings.copy(fontSize = settings.fontSize - 1)) },
                        ) { Text("A−") }
                        Text("${settings.fontSize}", Modifier.padding(horizontal = 12.dp))
                        OutlinedButton(
                            enabled = settings.fontSize < ReaderSettings.MAX_SIZE,
                            onClick = { vm.setReader(settings.copy(fontSize = settings.fontSize + 1)) },
                        ) { Text("A+") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTheme.entries.forEach { t ->
                            FilterChip(
                                selected = settings.theme == t,
                                onClick = { vm.setReader(settings.copy(theme = t)) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
        )
    }
    defining?.let { word ->
        ModalBottomSheet(onDismissRequest = { defining = null }) {
            DefineSheet(vm, word, onFullEntry = { defining = null; vm.open(word) })
        }
    }
}

@Composable
private fun ParagraphText(text: String, size: Int, color: Color, onWord: (String) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = (size * 0.8f).dp)
            .pointerInput(text) {
                detectTapGestures { pos ->
                    layout?.let { l -> wordAt(text, l.getOffsetForPosition(pos))?.let(onWord) }
                }
            },
        color = color,
        fontFamily = FontFamily.Serif,
        fontSize = size.sp,
        lineHeight = (size * 1.65f).sp,
        onTextLayout = { layout = it },
    )
}

/** The word under [offset], without surrounding quotes or a trailing 's. */
internal fun wordAt(text: String, offset: Int): String? {
    if (text.isEmpty()) return null
    fun inWord(c: Char) = c.isLetter() || c == '\'' || c == '’' || c == '-'
    var i = offset.coerceIn(0, text.length - 1)
    if (!inWord(text[i])) {
        if (i > 0 && inWord(text[i - 1])) i-- else return null
    }
    var start = i
    var end = i
    while (start > 0 && inWord(text[start - 1])) start--
    while (end < text.length - 1 && inWord(text[end + 1])) end++
    val word = text.substring(start, end + 1).trim('\'', '’', '-').removeSuffix("'s").removeSuffix("’s")
    return word.takeIf { w -> w.any { it.isLetter() } }
}

/** What a tapped word means, in a sheet over the page. */
@Composable
private fun DefineSheet(vm: DictionaryViewModel, word: String, onFullEntry: () -> Unit) {
    var entry by remember(word) { mutableStateOf<WordEntry?>(null) }
    var loaded by remember(word) { mutableStateOf(false) }
    LaunchedEffect(word) {
        entry = vm.preview(word)
        loaded = true
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry?.word ?: word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                entry?.pronunciation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            entry?.let { e ->
                val saved = vm.isSaved(e.word)
                IconButton(onClick = { vm.toggleSaved(e.word) }) {
                    Icon(
                        if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                        if (saved) "Remove from saved" else "Save this word",
                        tint = if (saved) Palette.Coral else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val e = entry
        when {
            !loaded -> CircularProgressIndicator()
            e == null -> Text("“$word” isn't in the dictionary.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                e.redirectedFrom?.let { Text("Base form of “$it”", color = Palette.Coral) }
                e.definitions.take(3).forEach { d ->
                    Text(
                        (if (d.partOfSpeech.isNotEmpty()) "${d.partOfSpeech}: " else "") + d.text,
                        Modifier.padding(top = 8.dp),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onFullEntry, Modifier.padding(top = 8.dp)) { Text("Full entry") }
            }
        }
    }
}
