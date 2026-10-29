package io.github.akrishna87.dictionary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.akrishna87.dictionary.ui.DictionaryApp
import io.github.akrishna87.dictionary.ui.DictionaryTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DictionaryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { DictionaryTheme { DictionaryApp(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Define" from another app's text-selection menu. */
    private fun handle(intent: Intent?) {
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            viewModel.onQueryChange(text.trim())
            viewModel.open(text)
        }
    }
}
