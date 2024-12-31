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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
                Text("Microphone Permission Required")
            },
            text = {
                Text(
                    "The microphone permission is required for speech recognition. " +
                            "Please grant the permission to use this feature."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rationaleState.showRationale = false
                        rationaleState.hasBeenShownOnce = true
                        onRequestPermission()
                    }
                ) {
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
                _uiState.value = _uiState.value.copy(isRecording = true)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        recognizedText = matches[0],
                        isRecording = false
                    )
                    viewModelScope.launch {
                        translate()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(recognizedText = matches[0])
                }
            }

            override fun onError(error: Int) {
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    error = "Recognition error: $error"
                )
            }

            // Implement other RecognitionListener methods
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
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
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun stopRecording() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(isRecording = false)
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
            "Spanish" -> TranslateLanguage.SPANISH
            "French" -> TranslateLanguage.FRENCH
            "German" -> TranslateLanguage.GERMAN
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
    val recognizedText: String = "",
    val translatedText: String = "",
    val targetLanguage: String = "Hindi",
    val isLoading: Boolean = false,
    val error: String? = null
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Speech Translation",
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
                    contentDescription = "Target Language",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(state.targetLanguage)
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
                listOf("English", "Hindi", "Spanish", "French", "German").forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
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

        // Recording status indicator
        if (state.isRecording) {
            RecordingIndicator()
        }

        // Recognized text display
        if (state.recognizedText.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Recognized Text:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = state.recognizedText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Microphone button
        MicrophoneButton(
            isRecording = state.isRecording,
            onRecordingStateChanged = { handleRecordingClick() }
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

    // Translation Result
    if (state.translatedText.isNotEmpty()) {
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

@Composable
private fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )

    Box(
        modifier = Modifier
            .padding(16.dp)
            .scale(scale)
    ) {
        Text(
            text = "Recording...",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun MicrophoneButton(
    isRecording: Boolean,
    onRecordingStateChanged: () -> Unit
) {
    FloatingActionButton(
        onClick = onRecordingStateChanged,
        shape = CircleShape,
        containerColor = if (isRecording)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(16.dp)
            .size(72.dp)
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
            modifier = Modifier.size(32.dp)
        )
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