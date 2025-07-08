package com.example.anuvadak.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RationaleState {
    var showRationale by mutableStateOf(false)
    var hasBeenShownOnce by mutableStateOf(false)
}

@Composable
fun rememberRationaleState(): RationaleState {
    return remember { RationaleState() }
}

@Composable
fun RationaleDialog(
    rationaleState: RationaleState,
    onRequestPermission: () -> Unit
) {
    if (rationaleState.showRationale) {
        AlertDialog(
            onDismissRequest = {
                rationaleState.showRationale = false
                rationaleState.hasBeenShownOnce = true
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Microphone Permission Required")
                }
            },
            text = {
                Text(
                    "The microphone permission is required for speech recognition. " +
                            "This enables real-time voice translation and ensures accurate speech processing.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rationaleState.showRationale = false
                        rationaleState.hasBeenShownOnce = true
                        onRequestPermission()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rationaleState.showRationale = false
                        rationaleState.hasBeenShownOnce = true
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

class SpeechTranslationViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(SpeechTranslationState())
    val uiState: StateFlow<SpeechTranslationState> = _uiState.asStateFlow()

    private var translator: Translator? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupRecognitionListener()
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Speech recognition is not available on this device"
            )
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.value = _uiState.value.copy(
                    isRecording = true,
                    isListening = true,
                    error = null
                )
            }

            override fun onBeginningOfSpeech() {
                _uiState.value = _uiState.value.copy(
                    isListening = true,
                    recognizedText = ""
                )
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update audio level for visual feedback
                val normalizedLevel = (rmsdB + 10) / 20 // Normalize to 0-1 range
                _uiState.value = _uiState.value.copy(
                    audioLevel = normalizedLevel.coerceIn(0f, 1f)
                )
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }

            override fun onEndOfSpeech() {
                _uiState.value = _uiState.value.copy(
                    isListening = false
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        recognizedText = matches[0],
                        isRecording = false,
                        isListening = false,
                        audioLevel = 0f
                    )
                    viewModelScope.launch {
                        translate()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        recognizedText = matches[0]
                    )
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech input detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error occurred"
                }

                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    isListening = false,
                    audioLevel = 0f,
                    error = errorMessage
                )
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _uiState.value = _uiState.value.copy(
            error = null,
            recognizedText = "",
            translatedText = ""
        )
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun stopRecording() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isListening = false,
            audioLevel = 0f
        )
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            recognizedText = "",
            translatedText = "",
            error = null
        )
    }

    fun updateTargetLanguage(language: String) {
        _uiState.value = _uiState.value.copy(targetLanguage = language)
        translator?.close()
        translator = null
        if (_uiState.value.recognizedText.isNotEmpty()) {
            viewModelScope.launch {
                translate()
            }
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

    private suspend fun translate() {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            if (translator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH) // Assuming speech is in English
                    .setTargetLanguage(getLanguageCode(_uiState.value.targetLanguage))
                    .build()
                translator = Translation.getClient(options)
                translator?.downloadModelIfNeeded()?.await()
            }

            val translatedText = translator?.translate(_uiState.value.recognizedText)?.await()
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

    override fun onCleared() {
        super.onCleared()
        translator?.close()
        speechRecognizer?.destroy()
    }
}

data class SpeechTranslationState(
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val translatedText: String = "",
    val targetLanguage: String = "Hindi",
    val isLoading: Boolean = false,
    val error: String? = null,
    val audioLevel: Float = 0f
)

class SpeechTranslationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeechTranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeechTranslationViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun rememberPermissionState(
    permission: String,
    onPermissionResult: (Boolean) -> Unit
): PermissionState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as ComponentActivity

    val permissionState = remember(permission) {
        MutableStateFlow(
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState.value = isGranted
        onPermissionResult(isGranted)
    }

    return remember(permission) {
        object : PermissionState {
            override val permission = permission
            override val hasPermission: Boolean
                get() = permissionState.value
            override val shouldShowRationale: Boolean
                get() = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, permission
                )

            override fun launchPermissionRequest() {
                scope.launch {
                    launcher.launch(permission)
                }
            }
        }
    }
}

interface PermissionState {
    val permission: String
    val hasPermission: Boolean
    val shouldShowRationale: Boolean
    fun launchPermissionRequest()
}

@Composable
fun SpeechTranslationScreen() {
    val context = LocalContext.current
    val viewModel: SpeechTranslationViewModel = viewModel(
        factory = SpeechTranslationViewModelFactory(context)
    )
    val state by viewModel.uiState.collectAsState()
    var showLanguageMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO,
        onPermissionResult = { isGranted ->
            if (isGranted && !state.isRecording) {
                viewModel.toggleRecording()
            }
        }
    )

    val rationaleState = rememberRationaleState()

    fun handleRecordingClick() {
        when {
            permissionState.hasPermission -> {
                viewModel.toggleRecording()
            }
            permissionState.shouldShowRationale || !rationaleState.hasBeenShownOnce -> {
                rationaleState.showRationale = true
            }
            else -> {
                permissionState.launchPermissionRequest()
            }
        }
    }

    RationaleDialog(
        rationaleState = rationaleState,
        onRequestPermission = { permissionState.launchPermissionRequest() }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(bottom = 8.dp),
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
                        text = "🎤",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Speech Translation",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Speak naturally and get instant translations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Language Selection
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Translation Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = { showLanguageMenu = true },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Target Language",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Translate to: ${state.targetLanguage}")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Language"
                            )
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            listOf("English", "Hindi", "Bengali").forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language, textAlign = TextAlign.Center) },
                                    onClick = {
                                        viewModel.updateTargetLanguage(language)
                                        showLanguageMenu = false
                                    },
                                    leadingIcon = {
                                        if (language == state.targetLanguage) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Recording Section - Centered
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isRecording)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status Text
                        Text(
                            text = when {
                                state.isListening -> "Listening... Speak now"
                                state.isRecording -> "Processing speech..."
                                else -> "Tap to start speaking"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isRecording)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(bottom = 24.dp),
                            textAlign = TextAlign.Center
                        )

                        // Microphone Button with Audio Level Animation
                        MicrophoneButton(
                            isRecording = state.isRecording,
                            isListening = state.isListening,
                            audioLevel = state.audioLevel,
                            onRecordingStateChanged = { handleRecordingClick() }
                        )

                        // Recording Indicator
                        if (state.isRecording) {
                            Spacer(modifier = Modifier.height(20.dp))
                            RecordingIndicator(audioLevel = state.audioLevel)
                        }
                    }
                }
            }

            // Results Section - Centered
            if (state.recognizedText.isNotEmpty() || state.translatedText.isNotEmpty() || state.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (state.recognizedText.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearResults() }
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear Results",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Recognized Text
                        if (state.recognizedText.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.RecordVoiceOver,
                                            contentDescription = "Recognized Speech",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "Recognized Speech:",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = state.recognizedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Translation Loading
                        if (state.isLoading) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(end = 12.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Translating...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Translation Result
                        if (state.translatedText.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Translate,
                                            contentDescription = "Translation",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "Translation (${state.targetLanguage}):",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = state.translatedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error Message - Centered
            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Recognition Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Help Section - Centered
            if (!state.isRecording && state.recognizedText.isEmpty() && state.error == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.TipsAndUpdates,
                            contentDescription = "Tips",
                            modifier = Modifier
                                .size(36.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Speech Recognition Tips",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        val tips = listOf(
                            "🎯 Speak clearly and at a normal pace",
                            "🔇 Ensure you're in a quiet environment",
                            "📱 Hold the device close to your mouth",
                            "🗣️ Speak in English for best results"
                        )

                        tips.forEach { tip ->
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecordingIndicator(audioLevel: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_animation")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_animation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val baseHeight = 20f
            val maxAdditionalHeight = 30f
            val multiplier = (index + 1) / 5f
            val calculatedHeight = baseHeight + (audioLevel * maxAdditionalHeight * multiplier)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(calculatedHeight.dp)
                    .padding(horizontal = 2.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun MicrophoneButton(
    isRecording: Boolean,
    isListening: Boolean,
    audioLevel: Float,
    onRecordingStateChanged: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isListening) 1.1f + (audioLevel * 0.3f) else 1f,
        animationSpec = tween(150),
        label = "mic_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse ring when recording
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }

        // Main microphone button
        FloatingActionButton(
            onClick = onRecordingStateChanged,
            modifier = Modifier
                .size(80.dp)
                .scale(animatedScale),
            shape = CircleShape,
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                modifier = Modifier.size(36.dp),
                tint = if (isRecording) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        }
    }
}

@Preview(showBackground = true, name = "Speech Translation Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Speech Translation Preview - Dark"
)
@Composable
fun SpeechTranslationScreenPreview() {
    MaterialTheme {
        Surface {
            SpeechTranslationScreen()
        }
    }
}