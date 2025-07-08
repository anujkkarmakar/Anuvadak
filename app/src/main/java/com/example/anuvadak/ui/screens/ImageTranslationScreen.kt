package com.example.anuvadak.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ImageTranslationViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageTranslationState())
    val uiState: StateFlow<ImageTranslationState> = _uiState.asStateFlow()

    private var textRecognizer: TextRecognizer
    private var translator: Translator? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun processImageFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                error = null,
                lastCapturedImageUri = uri,
                recognizedText = "",
                translatedText = ""
            )

            try {
                // Create InputImage directly from URI
                val image = InputImage.fromFilePath(context, uri)
                recognizeTextFromImage(image)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Failed to process image: ${e.localizedMessage}"
                )
            }
        }
    }

    fun processImageFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                error = null,
                recognizedText = "",
                translatedText = ""
            )

            try {
                // Create InputImage from Bitmap
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizeTextFromImage(image)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Failed to process image: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun recognizeTextFromImage(image: InputImage) {
        try {
            val result = suspendCoroutine { continuation ->
                textRecognizer.process(image)
                    .addOnSuccessListener { text ->
                        continuation.resume(text)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            }

            val recognizedText = result.text
            _uiState.value = _uiState.value.copy(
                recognizedText = recognizedText
            )

            if (recognizedText.isNotEmpty()) {
                translateRecognizedText(recognizedText)
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "No text found in the image. Please try with a clearer image containing text."
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "Text recognition failed: ${e.localizedMessage}"
            )
        }
    }

    private suspend fun translateRecognizedText(text: String) {
        try {
            if (translator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(getLanguageCode(_uiState.value.targetLanguage))
                    .build()
                translator = Translation.getClient(options)
                translator?.downloadModelIfNeeded()?.await()
            }

            val translatedText = translator?.translate(text)?.await()
            _uiState.value = _uiState.value.copy(
                translatedText = translatedText ?: "",
                isProcessing = false,
                error = null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "Translation failed: ${e.localizedMessage}"
            )
        }
    }

    fun updateTargetLanguage(language: String) {
        viewModelScope.launch {
            try {
                // Update UI state immediately
                _uiState.value = _uiState.value.copy(
                    targetLanguage = language,
                    isProcessing = true,
                    translatedText = "" // Clear previous translation
                )

                // Close existing translator
                translator?.close()
                translator = null

                // Create new translator
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(getLanguageCode(language))
                    .build()
                translator = Translation.getClient(options)

                // Download language model if needed
                translator?.downloadModelIfNeeded()?.await()

                // Retranslate existing text if any
                if (_uiState.value.recognizedText.isNotEmpty()) {
                    translateRecognizedText(_uiState.value.recognizedText)
                } else {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update language: ${e.localizedMessage}",
                    isProcessing = false
                )
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

    override fun onCleared() {
        super.onCleared()
        translator?.close()
        textRecognizer.close()
        cameraExecutor.shutdown()
    }
}

data class ImageTranslationState(
    val isProcessing: Boolean = false,
    val recognizedText: String = "",
    val translatedText: String = "",
    val error: String? = null,
    val targetLanguage: String = "Hindi",
    val isModelDownloaded: Boolean = false,
    val lastCapturedImageUri: Uri? = null,
    val sourceLanguage: String? = null,
    val showCameraPreview: Boolean = false
)

class ImageTranslationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImageTranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImageTranslationViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun ImageTranslationScreen(
    viewModel: ImageTranslationViewModel = viewModel(
        factory = ImageTranslationViewModelFactory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showCameraOptions by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Create a file for camera capture
    val photoFile = remember {
        createImageFile(context)
    }
    val photoUri = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    // Camera launcher for full-size image capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Process the captured image from the file
            viewModel.processImageFromUri(photoUri)
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processImageFromUri(it) }
    }

    // Camera options dialog
    if (showCameraOptions) {
        AlertDialog(
            onDismissRequest = { showCameraOptions = false },
            title = { Text("Select Image Source") },
            text = { Text("Choose how you want to capture the image") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraOptions = false
                        cameraLauncher.launch(photoUri)
                    }
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Camera",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraOptions = false
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = "Gallery",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Gallery")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Image Translation",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Language Selection
            Box(modifier = Modifier.fillMaxWidth()) {
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
                    onDismissRequest = { showLanguageMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    listOf("English", "Hindi", "Bengali").forEach { language ->
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

            // Image Selection Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        cameraLauncher.launch(photoUri)
                    }
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Camera",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Take Photo")
                }

                Button(
                    onClick = {
                        galleryLauncher.launch("image/*")
                    }
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = "Gallery",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Choose Image")
                }
            }
        }

        // Scrollable Content Section
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Processing Indicator
            if (state.isProcessing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp)
                        )
                        Text(
                            text = "Processing image and extracting text...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Recognized Text Display
            if (state.recognizedText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = "Recognized Text",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Recognized Text:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = state.recognizedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Translation Display
            if (state.translatedText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Translation",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Translation:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = state.translatedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Help Text when no content
            if (!state.isProcessing && state.recognizedText.isEmpty() && state.error == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Capture or Select an Image",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Take a photo or choose an image from your gallery to extract and translate text",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Bottom spacing for better scrolling
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// Utility function to create image file
private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
}