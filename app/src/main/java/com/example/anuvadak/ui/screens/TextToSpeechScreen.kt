package com.example.anuvadak.ui.screens

import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import androidx.lifecycle.ViewModelProvider

class TextToSpeechViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(TextToSpeechState())
    val uiState: StateFlow<TextToSpeechState> = _uiState.asStateFlow()

    private var translator: Translator? = null
    private var tts: TextToSpeech? = null
    private val languageIdentifier = LanguageIdentification.getClient()

    init {
        initializeTextToSpeech()
    }

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize Text-to-Speech"
                )
            }
        }
    }

    fun updateText(newText: String) {
        _uiState.value = _uiState.value.copy(text = newText)
        if (newText.isNotEmpty()) {
            detectLanguage(newText)
        }
    }

    private fun detectLanguage(text: String) {
        viewModelScope.launch {
            try {
                val detectedLanguage = languageIdentifier.identifyLanguage(text).await()
                _uiState.value = _uiState.value.copy(
                    detectedLanguage = getLanguageName(detectedLanguage),
                    translatedText = ""  // Clear previous translation
                )
                // Automatically translate if languages are different
                if (_uiState.value.detectedLanguage != _uiState.value.selectedLanguage) {
                    translate()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Language detection failed: ${e.localizedMessage}"
                )
            }
        }
    }

    suspend fun updateSelectedLanguage(language: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
        translator?.close()
        translator = null
        if (_uiState.value.text.isNotEmpty()) {
            translate()
        }
    }

    private fun getLanguageCode(language: String): String {
        return when (language) {
            "English" -> TranslateLanguage.ENGLISH
            "Hindi" -> TranslateLanguage.HINDI
//            "Spanish" -> TranslateLanguage.SPANISH
//            "French" -> TranslateLanguage.FRENCH
//            "German" -> TranslateLanguage.GERMAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    private fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "hi" -> "Hindi"
//            "es" -> "Spanish"
//            "fr" -> "French"
//            "de" -> "German"
            else -> "English"
        }
    }

    suspend fun translate() {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            if (_uiState.value.detectedLanguage == _uiState.value.selectedLanguage) {
                _uiState.value = _uiState.value.copy(
                    translatedText = _uiState.value.text,
                    isLoading = false
                )
                return
            }

            if (translator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(getLanguageCode(_uiState.value.detectedLanguage))
                    .setTargetLanguage(getLanguageCode(_uiState.value.selectedLanguage))
                    .build()
                translator = Translation.getClient(options)
                translator?.downloadModelIfNeeded()?.await()
            }

            val translatedText = translator?.translate(_uiState.value.text)?.await()
            _uiState.value = _uiState.value.copy(
                translatedText = translatedText ?: "",
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Translation failed: ${e.localizedMessage}",
                isLoading = false
            )
        }
    }

    fun togglePlayback() {
        val textToSpeak = _uiState.value.translatedText.ifEmpty { _uiState.value.text }
        when {
            _uiState.value.isPlaying -> {
                tts?.stop()
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
            textToSpeak.isNotEmpty() -> {
                val locale = when (_uiState.value.selectedLanguage) {
                    "English" -> Locale.US
                    "Hindi" -> Locale("hi")
                    "Spanish" -> Locale("es")
                    "French" -> Locale.FRENCH
                    "German" -> Locale.GERMAN
                    else -> Locale.US
                }
                tts?.language = locale
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
                _uiState.value = _uiState.value.copy(isPlaying = true)
            }
        }
    }

    fun updateSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    override fun onCleared() {
        super.onCleared()
        translator?.close()
        tts?.stop()
        tts?.shutdown()
        languageIdentifier.close()
    }
}

data class TextToSpeechState(
    val text: String = "",
    val translatedText: String = "",
    val detectedLanguage: String = "English",
    val selectedLanguage: String = "English",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class TextToSpeechViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TextToSpeechViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TextToSpeechViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun TextToSpeechScreen() {
    val context = LocalContext.current
    val viewModel: TextToSpeechViewModel = viewModel(
        factory = TextToSpeechViewModelFactory(context)
    )
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var showLanguageMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Text to Speech",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Language Selection
        Box {
            OutlinedButton(
                onClick = { showLanguageMenu = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = "Language",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(state.selectedLanguage)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Select Language",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false }
            ) {
                listOf("English", "Hindi",/* "Spanish", "French", "German"*/).forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = {
                            scope.launch {  // Use the already defined scope
                                viewModel.updateSelectedLanguage(language)
                            }
                            showLanguageMenu = false
                        },
                        leadingIcon = {
                            if (language == state.selectedLanguage) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }

        // Text Input Field
        OutlinedTextField(
            value = state.text,
            onValueChange = { viewModel.updateText(it) },
            label = { Text("Enter text to speak") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(bottom = 16.dp),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        // Detected Language
        if (state.text.isNotEmpty() && !state.isLoading) {
            Text(
                text = "Detected Language: ${state.detectedLanguage}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Error Message
        state.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Playback Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Playback Controls",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stop Button
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        enabled = state.isPlaying
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = if (state.isPlaying)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Play/Pause Button
                    FilledTonalButton(
                        onClick = { viewModel.togglePlayback() },
                        enabled = state.text.isNotEmpty() && !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Speed Control
                    IconButton(
                        onClick = {
                            speed = if (speed >= 2f) 0.5f else speed + 0.5f
                            viewModel.updateSpeed(speed)
                        }
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "Playback Speed: ${speed}x"
                        )
                    }
                }

                // Speed indicator
                Text(
                    text = "Speed: ${speed}x",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
            }
        }

        // Translation Result (if different from input)
        if (state.translatedText.isNotEmpty() && state.translatedText != state.text) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = state.translatedText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Text to Speech Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Text to Speech Preview - Dark"
)
@Composable
fun TextToSpeechScreenPreview() {
    MaterialTheme {
        Surface {
            TextToSpeechScreen()
        }
    }
}