package com.example.anuvadak.ui.screens

import android.content.Context
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.provider.Settings.Global.putString
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            if (status == TextToSpeech.SUCCESS) {
                _uiState.value = _uiState.value.copy(
                    isTtsInitialized = true,
                    error = null
                )
                setupUtteranceProgressListener()
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize Text-to-Speech",
                    isTtsInitialized = false
                )
            }
        }
    }

    private fun setupUtteranceProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = true,
                    currentUtteranceId = utteranceId
                )
            }

            override fun onDone(utteranceId: String?) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    currentUtteranceId = null,
                    playbackProgress = 0f
                )
            }

            override fun onError(utteranceId: String?) {
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    currentUtteranceId = null,
                    error = "Speech playback failed"
                )
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Calculate progress based on character position
                val totalLength = _uiState.value.getTextToSpeak().length
                val progress = if (totalLength > 0) start.toFloat() / totalLength else 0f
                _uiState.value = _uiState.value.copy(playbackProgress = progress)
            }
        })
    }

    fun updateText(newText: String) {
        _uiState.value = _uiState.value.copy(
            text = newText,
            error = null
        )
        if (newText.isNotEmpty()) {
            detectLanguage(newText)
        } else {
            _uiState.value = _uiState.value.copy(
                detectedLanguage = "English",
                translatedText = ""
            )
        }
    }

    private fun detectLanguage(text: String) {
        viewModelScope.launch {
            try {
                val detectedLanguage = languageIdentifier.identifyLanguage(text).await()
                _uiState.value = _uiState.value.copy(
                    detectedLanguage = getLanguageName(detectedLanguage),
                    translatedText = "" // Clear previous translation
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
        _uiState.value = _uiState.value.copy(
            selectedLanguage = language,
            error = null
        )
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
            "Bengali" -> TranslateLanguage.BENGALI
            else -> TranslateLanguage.ENGLISH
        }
    }

    private fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "hi" -> "Hindi"
            "bn" -> "Bengali"
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
        if (!_uiState.value.isTtsInitialized) {
            _uiState.value = _uiState.value.copy(
                error = "Text-to-Speech is not initialized"
            )
            return
        }

        when {
            _uiState.value.isPlaying -> {
                stopPlayback()
            }
            _uiState.value.getTextToSpeak().isNotEmpty() -> {
                startPlayback()
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    error = "No text to speak"
                )
            }
        }
    }

    private fun startPlayback() {
        val textToSpeak = _uiState.value.getTextToSpeak()
        val locale = when (_uiState.value.selectedLanguage) {
            "English" -> Locale.US
            "Hindi" -> Locale("hi")
            "Bengali" -> Locale("bn")
            else -> Locale.US
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _uiState.value = _uiState.value.copy(
                error = "Language not supported for speech synthesis"
            )
            return
        }

        tts?.setSpeechRate(_uiState.value.speechRate)
        tts?.setPitch(_uiState.value.pitch)

        val utteranceId = "TTS_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val speakResult = tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (speakResult == TextToSpeech.ERROR) {
            _uiState.value = _uiState.value.copy(
                error = "Failed to start speech synthesis"
            )
        }
    }

    private fun stopPlayback() {
        tts?.stop()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            currentUtteranceId = null,
            playbackProgress = 0f
        )
    }

    fun updateSpeechRate(rate: Float) {
        _uiState.value = _uiState.value.copy(speechRate = rate)
        if (_uiState.value.isPlaying) {
            tts?.setSpeechRate(rate)
        }
    }

    fun updatePitch(pitch: Float) {
        _uiState.value = _uiState.value.copy(pitch = pitch)
        if (_uiState.value.isPlaying) {
            tts?.setPitch(pitch)
        }
    }

    fun clearText() {
        stopPlayback()
        _uiState.value = _uiState.value.copy(
            text = "",
            translatedText = "",
            detectedLanguage = "English",
            error = null,
            playbackProgress = 0f
        )
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
    val isTtsInitialized: Boolean = false,
    val error: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val playbackProgress: Float = 0f,
    val currentUtteranceId: String? = null
) {
    fun getTextToSpeak(): String = translatedText.ifEmpty { text }
    fun getDisplayLanguage(): String = if (translatedText.isNotEmpty()) selectedLanguage else detectedLanguage
}

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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        // Header Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔊",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Text to Speech",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Convert text to natural speech in multiple languages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Language Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Output Language",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Language",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Speak in: ${state.selectedLanguage}")
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select Language"
                        )
                    }

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        listOf("English", "Hindi", "Bengali").forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    scope.launch {
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
            }
        }

        // Text Input Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enter Text",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (state.text.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearText() }
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear Text",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.text,
                    onValueChange = { viewModel.updateText(it) },
                    placeholder = {
                        Text("Type or paste text here to convert to speech...")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = false,
                    maxLines = 6,
                    leadingIcon = {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = "Text Input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                // Character Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (state.text.isNotEmpty() && !state.isLoading) {
                        Text(
                            text = "Detected: ${state.detectedLanguage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "${state.text.length} characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Voice Controls Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Voice Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Speech Rate Control
                VoiceControlSlider(
                    label = "Speed",
                    value = state.speechRate,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { viewModel.updateSpeechRate(it) },
                    valueDisplay = "${String.format("%.1f", state.speechRate)}x",
                    icon = Icons.Default.Speed
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pitch Control
                VoiceControlSlider(
                    label = "Pitch",
                    value = state.pitch,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.updatePitch(it) },
                    valueDisplay = "${String.format("%.1f", state.pitch)}x",
                    icon = Icons.Default.GraphicEq
                )
            }
        }

        // Playback Controls Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isPlaying)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        state.isPlaying -> "Speaking..."
                        state.getTextToSpeak().isNotEmpty() -> "Ready to speak"
                        else -> "Enter text to get started"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.isPlaying)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Progress Bar
                if (state.isPlaying) {
                    LinearProgressIndicator(
                        progress = state.playbackProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )
                }

                // Play/Stop Button
                PlaybackButton(
                    isPlaying = state.isPlaying,
                    isEnabled = state.getTextToSpeak().isNotEmpty() && state.isTtsInitialized,
                    onTogglePlayback = { viewModel.togglePlayback() }
                )

                // Language Info
                if (state.getTextToSpeak().isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Speaking in ${state.getDisplayLanguage()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isPlaying)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Translation Result (if different from input)
        if (state.translatedText.isNotEmpty() && state.translatedText != state.text) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Translation",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Translation (${state.selectedLanguage}):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        text = state.translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Loading State
        if (state.isLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Translating text...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Error Message
        state.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Help Section
        if (state.text.isEmpty() && state.error == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TipsAndUpdates,
                        contentDescription = "Tips",
                        modifier = Modifier
                            .size(40.dp)
                            .padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Text-to-Speech Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val tips = listOf(
                        "• Type or paste text in any supported language",
                        "• Adjust speed and pitch for better listening",
                        "• Text will be auto-translated if needed",
                        "• Works offline for downloaded languages"
                    )

                    tips.forEach { tip ->
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VoiceControlSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun PlaybackButton(
    isPlaying: Boolean,
    isEnabled: Boolean,
    onTogglePlayback: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.1f else 1f,
        animationSpec = tween(200),
        label = "playback_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "button_color"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Pulse effect when playing
        if (isPlaying) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .background(
                        buttonColor.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }

        // Main play button
        FloatingActionButton(
            onClick = onTogglePlayback,
            modifier = Modifier
                .size(72.dp)
                .scale(animatedScale),
            shape = CircleShape,
            containerColor = buttonColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop Speech" else "Start Speech",
                modifier = Modifier.size(32.dp),
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
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