package com.example.anuvadak.ui.screens

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class TextTranslationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TextTranslationState())
    val uiState: StateFlow<TextTranslationState> = _uiState.asStateFlow()

    private var translator: Translator? = null

    fun updateSourceText(text: String) {
        _uiState.value = _uiState.value.copy(sourceText = text)
    }

    fun updateTranslatedText(text: String) {
        _uiState.value = _uiState.value.copy(translatedText = text)
    }

    fun updateSourceLanguage(language: String) {
        _uiState.value = _uiState.value.copy(sourceLanguage = language)
        translator?.close()
        translator = null
    }

    fun updateTargetLanguage(language: String) {
        _uiState.value = _uiState.value.copy(targetLanguage = language)
        translator?.close()
        translator = null
    }

    fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    fun setError(error: String?) {
        _uiState.value = _uiState.value.copy(error = error)
    }

    private fun getLanguageCode(language: String): String {
        return when (language) {
            "English" -> TranslateLanguage.ENGLISH
            "Hindi" -> TranslateLanguage.HINDI
//            "Spanish" -> TranslateLanguage.SPANISH
//            "French" -> TranslateLanguage.FRENCH
//            "German" -> TranslateLanguage.GERMAN
//            "Japanese" -> TranslateLanguage.JAPANESE
//            "Chinese" -> TranslateLanguage.CHINESE
            else -> TranslateLanguage.ENGLISH
        }
    }

    suspend fun translate() {
        try {
            setLoading(true)
            setError(null)

            if (translator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(getLanguageCode(_uiState.value.sourceLanguage))
                    .setTargetLanguage(getLanguageCode(_uiState.value.targetLanguage))
                    .build()
                translator = Translation.getClient(options)
                translator?.downloadModelIfNeeded()?.await()
            }

            val translatedText = translator?.translate(_uiState.value.sourceText)?.await()
            translatedText?.let { updateTranslatedText(it) }
        } catch (e: Exception) {
            setError("Translation failed: ${e.localizedMessage}")
        } finally {
            setLoading(false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        translator?.close()
    }
}

data class TextTranslationState(
    val sourceText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = "English",
    val targetLanguage: String = "Hindi",
    val isLoading: Boolean = false,
    val error: String? = null
)

@Composable
fun TextTranslationScreen(
    viewModel: TextTranslationViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var showSourceLanguageMenu by remember { mutableStateOf(false) }
    var showTargetLanguageMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Text Translation",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Language Selection Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source Language
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showSourceLanguageMenu = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                ) {
                    Text(state.sourceLanguage)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Source Language",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                LanguageDropdownMenu(
                    expanded = showSourceLanguageMenu,
                    onDismissRequest = { showSourceLanguageMenu = false },
                    selectedLanguage = state.sourceLanguage,
                    onLanguageSelected = {
                        viewModel.updateSourceLanguage(it)
                        showSourceLanguageMenu = false
                    }
                )
            }

            // Swap Languages Button
            IconButton(
                onClick = {
                    val tempLang = state.sourceLanguage
                    viewModel.updateSourceLanguage(state.targetLanguage)
                    viewModel.updateTargetLanguage(tempLang)
                    viewModel.updateSourceText(state.translatedText)
                    viewModel.updateTranslatedText("")
                }
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Swap Languages"
                )
            }

            // Target Language
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showTargetLanguageMenu = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                ) {
                    Text(state.targetLanguage)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Target Language",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                LanguageDropdownMenu(
                    expanded = showTargetLanguageMenu,
                    onDismissRequest = { showTargetLanguageMenu = false },
                    selectedLanguage = state.targetLanguage,
                    onLanguageSelected = {
                        viewModel.updateTargetLanguage(it)
                        showTargetLanguageMenu = false
                    }
                )
            }
        }

        // Source Text Input
        OutlinedTextField(
            value = state.sourceText,
            onValueChange = { viewModel.updateSourceText(it) },
            label = { Text("Enter text to translate") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        // Translate Button
        Button(
            onClick = {
                scope.launch {
                    viewModel.translate()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            enabled = state.sourceText.isNotEmpty() && !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = "Translate",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Translate")
            }
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

        // Translation Result
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
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

                if (state.translatedText.isNotEmpty()) {
                    Text(
                        text = state.translatedText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = "Translation will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        listOf("English", "Hindi", /*"Spanish", "French", "German", "Japanese", "Chinese"*/).forEach { language ->
            DropdownMenuItem(
                text = { Text(language) },
                onClick = { onLanguageSelected(language) },
                leadingIcon = {
                    if (language == selectedLanguage) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true, name = "Text Translation Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Text Translation Preview - Dark"
)
@Composable
fun TextTranslationScreenPreview() {
    MaterialTheme {
        Surface {
            TextTranslationScreen()
        }
    }
}