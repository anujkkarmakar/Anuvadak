package com.example.anuvadak.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class representing the different screens in the app.
 * Each screen has a unique route, icon, and label.
 */
sealed class Screen(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object TextTranslation : Screen(
        route = "text_translation",
        icon = Icons.Default.TextFields,
        label = "Text"
    )

    object SpeechTranslation : Screen(
        route = "speech_translation",
        icon = Icons.Default.Mic,
        label = "Speech"
    )

    object TextToSpeech : Screen(
        route = "text_to_speech",
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        label = "Speak"
    )

    object ImageTranslation : Screen(
        route = "image_translation",
        icon = Icons.Default.Image,
        label = "Image"
    )

    companion object {
        fun getAllScreens() = listOf(TextTranslation, SpeechTranslation, TextToSpeech, ImageTranslation)
    }
}