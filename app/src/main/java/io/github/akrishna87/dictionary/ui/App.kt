package io.github.akrishna87.dictionary.ui

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dictionary.Definition
import io.github.akrishna87.dictionary.Detail
import io.github.akrishna87.dictionary.DictionaryViewModel
import io.github.akrishna87.dictionary.Source
import io.github.akrishna87.dictionary.WordEntry
import java.util.Locale

@Composable
fun DictionaryApp(vm: DictionaryViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val detail = vm.detail

    BackHandler(enabled = detail != null) { vm.close() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (detail == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Bookmarks, null) }, label = { Text("Saved") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                detail != null -> DetailScreen(vm, detail)
                tab == 0 -> SearchScreen(vm)
                else -> SavedScreen(vm)
            }
        }
    }
}

@Composable
private fun SearchScreen(vm: DictionaryViewModel) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
            placeholder = { Text("Search the dictionary") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
        )
        if (vm.query.isBlank()) {
            Home(vm)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(vm.suggestions, key = { it.word }) { s ->
                    WordRow(s.word, onClick = { vm.open(s.word) })
                }
                if (vm.suggestions.isEmpty()) {
                    item {
                        Text(
                            "No word starts with “${vm.query.trim()}”. Press search to look for close spellings.",
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(vm: DictionaryViewModel) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        vm.wordOfTheDay?.let { word ->
            item {
                Surface(
                    onClick = { vm.open(word) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("WORD OF THE DAY", style = MaterialTheme.typography.labelSmall, color = Palette.Coral)
                        Spacer(Modifier.height(6.dp))
                        Text(word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            TextButton(onClick = vm::openRandom, Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Shuffle, null)
                Spacer(Modifier.padding(4.dp))
                Text("Random word")
            }
        }
        if (vm.history.isNotEmpty()) {
            item { SectionHeader("Recent", action = "Clear", onAction = vm::clearHistory) }
            items(vm.history.take(15), key = { "h$it" }) { w ->
                WordRow(w, icon = Icons.Default.History, onClick = { vm.open(w) })
            }
        }
    }
}

@Composable
private fun SavedScreen(vm: DictionaryViewModel) {
    if (vm.favourites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Tap the star on a word to save it here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item { SectionHeader("Saved words") }
        items(vm.favourites, key = { it }) { w ->
            WordRow(w, icon = Icons.Default.Star, onClick = { vm.open(w) })
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 8.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun WordRow(word: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(6.dp))
        }
        Text(word, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(vm: DictionaryViewModel, detail: Detail) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (detail is Detail.Found) {
                val entry = detail.entry
                val context = LocalContext.current
                val speak = rememberSpeaker()
                IconButton(onClick = { speak(entry.word) }) { Icon(Icons.Default.VolumeUp, "Pronounce") }
                IconButton(onClick = { share(context, entry) }) { Icon(Icons.Default.Share, "Share") }
                val saved = vm.isFavourite(entry.word)
                IconButton(onClick = { vm.toggleFavourite(entry.word) }) {
                    Icon(
                        if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                        if (saved) "Remove from saved" else "Save",
                        tint = if (saved) Palette.Coral else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        when (detail) {
            Detail.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Detail.Missing -> Missing(vm, detail)
            is Detail.Found -> Entry(vm, detail.entry)
        }
    }
}

@Composable
private fun Missing(vm: DictionaryViewModel, detail: Detail.Missing) {
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Text("“${detail.word}” isn't in the dictionary.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (detail.similar.isNotEmpty()) Text("Did you mean", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(detail.similar, key = { it }) { w -> WordRow(w, onClick = { vm.open(w) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Entry(vm: DictionaryViewModel, entry: WordEntry) {
    val present = Source.entries.filter { s -> entry.definitions.any { it.source == s } }
    val shown = entry.definitions.filter { it.source !in vm.hiddenSources }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            Text(entry.word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            entry.pronunciation?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
            entry.redirectedFrom?.let {
                Text("Showing the base form of “$it”", color = Palette.Coral, style = MaterialTheme.typography.bodyMedium)
            }
            if (present.size > 1) {
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    present.forEach { s ->
                        FilterChip(
                            selected = s !in vm.hiddenSources,
                            onClick = { vm.toggleSource(s) },
                            label = { Text(s.label) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text("All sources are hidden.", Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        shown.groupBy { it.source }.forEach { (source, defs) ->
            item(key = "src-${source.code}") {
                Column(Modifier.padding(top = 24.dp)) {
                    Text(source.label.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
                    Text(source.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            var lastPos: String? = null
            defs.forEachIndexed { i, d ->
                if (d.partOfSpeech.isNotEmpty() && d.partOfSpeech != lastPos) {
                    item(key = "pos-${source.code}-$i") {
                        Text(
                            d.partOfSpeech,
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontStyle = FontStyle.Italic,
                            color = Palette.Coral,
                        )
                    }
                }
                lastPos = d.partOfSpeech
                item(key = "def-${source.code}-$i") { DefinitionRow(vm, i + 1, d) }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefinitionRow(vm: DictionaryViewModel, number: Int, d: Definition) {
    Row(Modifier.padding(top = 10.dp)) {
        Text("$number.", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(d.text, style = MaterialTheme.typography.bodyLarge)
            if (d.example.isNotEmpty()) {
                Text(
                    "“${d.example}”",
                    Modifier.padding(top = 4.dp),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (d.synonyms.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.synonyms.forEach { s ->
                        AssistChip(onClick = { vm.open(s) }, label = { Text(s) })
                    }
                }
            }
        }
    }
}

/** Reads a word aloud with the phone's own (offline) text-to-speech voice. */
@Composable
private fun rememberSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
        tts = engine
        onDispose { engine.shutdown() }
    }
    return { word -> tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word") }
}

private fun share(context: Context, entry: WordEntry) {
    val first = entry.definitions.firstOrNull()
    val text = buildString {
        append(entry.word)
        if (first != null) append(": ").append(first.text)
    }
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
    )
}
