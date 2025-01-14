package com.example.anuvadak.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ImageTranslationViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ImageTranslationState())
    val uiState: StateFlow<ImageTranslationState> = _uiState.asStateFlow()

    private var textRecognizer: TextRecognizer
    private var translator: Translator? = null

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
                val tempFile = createTempImageFile(context, uri)
                recognizeTextFromImage(tempFile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Failed to process image: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun recognizeTextFromImage(imageFile: File) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
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

            translateRecognizedText(recognizedText)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "Text recognition failed: ${e.localizedMessage}"
            )
        } finally {
            imageFile.delete()
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
//            "Spanish" -> TranslateLanguage.SPANISH
//            "French" -> TranslateLanguage.FRENCH
//            "German" -> TranslateLanguage.GERMAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    override fun onCleared() {
        super.onCleared()
        translator?.close()
        textRecognizer.close()
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
    val sourceLanguage: String? = null
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
    viewModel: ImageTranslationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ImageTranslationViewModelFactory(LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLanguageMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.extras?.get("data")?.let { imageBitmap ->
                val uri = saveBitmapToUri(context, imageBitmap as Bitmap)
                uri?.let { viewModel.processImageFromUri(it) }
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processImageFromUri(it) }
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
                    listOf("English", "Hindi", /*"Spanish", "French", "German"*/).forEach { language ->
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
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        cameraLauncher.launch(intent)
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
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
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
                        Text(
                            text = "Translation:",
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

            // Bottom spacing for better scrolling
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// Utility functions
private fun createTempImageFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File.createTempFile("temp_image", ".jpg", context.cacheDir)

    inputStream?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }

    return tempFile
}

private fun saveBitmapToUri(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}