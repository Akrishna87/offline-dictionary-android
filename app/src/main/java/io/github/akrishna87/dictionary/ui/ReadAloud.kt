package io.github.akrishna87.dictionary.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.github.akrishna87.dictionary.Definition
import io.github.akrishna87.dictionary.WordEntry
import java.util.Locale

/**
 * The phone's own (offline) text-to-speech voice. Says one word, or reads a list of passages
 * one after another, and knows whether it is still talking so the screen can offer Stop.
 */
class Speaker(context: Context) {
    var speaking by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: List<String>? = null
    private var lastId: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine().language = Locale.US
                pending?.let { say(it) }
            }
            pending = null
        }
    }

    private fun engine() = tts

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post { speaking = true }
            }

            override fun onDone(utteranceId: String?) {
                main.post { if (utteranceId == lastId) speaking = false }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { if (utteranceId == lastId) speaking = false }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { if (utteranceId == lastId) speaking = false }
            }
        })
    }

    /** Reads [passages] in order, with a short pause between them. Replaces anything being read now. */
    fun say(passages: List<String>) {
        val parts = passages.map { it.trim() }.filter { it.isNotEmpty() }.flatMap(::chunk)
        if (parts.isEmpty()) return
        if (!ready) {
            pending = parts
            return
        }
        tts.stop()
        val batch = System.nanoTime()
        parts.forEachIndexed { i, text ->
            val id = "read-$batch-$i"
            if (i == parts.lastIndex) lastId = id
            tts.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            if (i != parts.lastIndex) tts.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_ADD, "$id-pause")
        }
        speaking = true
    }

    fun say(text: String) = say(listOf(text))

    fun stop() {
        pending = null
        tts.stop()
        speaking = false
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    /** The engine refuses text longer than its limit, so long passages are split at sentence ends. */
    private fun chunk(text: String): List<String> {
        val limit = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(200)
        if (text.length <= limit) return listOf(text)
        val out = ArrayList<String>()
        var rest = text
        while (rest.length > limit) {
            val cut = rest.lastIndexOf(". ", limit).takeIf { it > limit / 2 }?.plus(1)
                ?: rest.lastIndexOf(' ', limit).takeIf { it > 0 }
                ?: limit
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private companion object {
        const val PAUSE_MS = 350L
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

/** The parts of a word page the user can choose to have read aloud. */
enum class ReadPart(val label: String, val default: Boolean) {
    Word("The word", true),
    PartsOfSpeech("Parts of speech (noun, verb…)", true),
    Definitions("Definitions", true),
    Examples("Example sentences", true),
    Synonyms("Synonyms", false),
    Antonyms("Antonyms", false),
    Note("My note", false),
}

/** Which parts the user last chose, kept between launches. */
class ReadAloudChoices(context: Context) {
    private val prefs = context.getSharedPreferences("read_aloud", Context.MODE_PRIVATE)

    fun load(): Set<ReadPart> = ReadPart.entries.filter { prefs.getBoolean(it.name, it.default) }.toSet()

    fun save(parts: Set<ReadPart>) = prefs.edit { ReadPart.entries.forEach { putBoolean(it.name, it in parts) } }
}

/** Turns the chosen parts of a word page into passages for [Speaker.say]. */
fun readAloudScript(entry: WordEntry, shown: List<Definition>, note: String, parts: Set<ReadPart>): List<String> {
    val out = ArrayList<String>()
    if (ReadPart.Word in parts) out += entry.word
    if (ReadPart.PartsOfSpeech in parts) {
        val pos = shown.map { it.partOfSpeech }.filter { it.isNotEmpty() }.distinct()
        if (pos.isNotEmpty()) out += pos.joinToString(", ") + "."
    }
    val perDefinition = parts.intersect(setOf(ReadPart.Definitions, ReadPart.Examples, ReadPart.Synonyms, ReadPart.Antonyms))
    if (perDefinition.isNotEmpty()) {
        var lastPos: String? = null
        shown.forEachIndexed { i, d ->
            val text = definitionScript(d, perDefinition, number = i + 1, saySpeech = ReadPart.PartsOfSpeech in parts && d.partOfSpeech != lastPos)
            lastPos = d.partOfSpeech
            if (text.isNotEmpty()) out += text
        }
    }
    if (ReadPart.Note in parts && note.isNotBlank()) out += "My note: ${note.trim()}"
    return out
}

/** One definition as a sentence or two, saying only the chosen parts. */
fun definitionScript(d: Definition, parts: Set<ReadPart>, number: Int? = null, saySpeech: Boolean = false): String = buildString {
    fun sentence(s: String) {
        if (s.isBlank()) return
        if (isNotEmpty()) append(' ')
        append(s.trim())
        if (!s.trimEnd().endsWith('.') && !s.trimEnd().endsWith('?') && !s.trimEnd().endsWith('!')) append('.')
    }
    if (saySpeech && d.partOfSpeech.isNotEmpty()) sentence(d.partOfSpeech.replaceFirstChar { it.titlecase(Locale.US) })
    if (ReadPart.Definitions in parts) sentence(if (number != null) "$number. ${d.text}" else d.text)
    if (ReadPart.Examples in parts && d.example.isNotEmpty()) sentence("For example: ${d.example}")
    if (ReadPart.Synonyms in parts && d.synonyms.isNotEmpty()) sentence("Synonyms: ${d.synonyms.joinToString(", ")}")
    if (ReadPart.Antonyms in parts && d.antonyms.isNotEmpty()) sentence("Antonyms: ${d.antonyms.joinToString(", ")}")
}

/** Lets the user tick what to hear, then reads it. Their ticks are remembered for next time. */
@Composable
fun ReadAloudDialog(onRead: (Set<ReadPart>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ReadAloudChoices(context) }
    var chosen by remember { mutableStateOf(store.load()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read aloud") },
        text = {
            Column {
                Text(
                    "Choose what to hear. Hidden sources are skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReadPart.entries.forEach { part ->
                    val on = part in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - part else chosen + part }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { chosen = if (it) chosen + part else chosen - part })
                        Text(part.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                store.save(chosen)
                onRead(chosen)
            }) { Text("Read") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
