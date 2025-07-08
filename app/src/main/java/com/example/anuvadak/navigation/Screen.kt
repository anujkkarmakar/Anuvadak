package com.example.anuvadak.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
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
    object Authentication : Screen(
        route = "auth",
        icon = Icons.Default.Login,
        label = "Authentication"
    )

    object Home : Screen(
        route = "home",
        icon = Icons.Default.Home,
        label = "Home"
    )

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

    object GestureRecognition : Screen(
        route = "gesture_recognition",
        icon = Icons.Default.PanTool,
        label = "Gesture"
    )

    object FeedbackScreen : Screen(
        route = "feedback_screen",
        icon = Icons.Default.Feedback,
        label = "Feedback"
    )

    object AboutUs : Screen(
        route = "about_us",
        icon = Icons.Default.Info,
        label = "About Us"
    )

    companion object {
        fun getAllScreens() = listOf(
            Authentication,
            Home,
            TextTranslation,
            SpeechTranslation,
            TextToSpeech,
            ImageTranslation,
            GestureRecognition,
            FeedbackScreen,
            AboutUs
        )

        fun getBottomNavScreens() = listOf(
            TextTranslation,
            SpeechTranslation,
            TextToSpeech,
            ImageTranslation,
            GestureRecognition
        )

        fun getMainFeatureScreens() = listOf(
            TextTranslation,
            SpeechTranslation,
            TextToSpeech,
            ImageTranslation,
            GestureRecognition
        )

        fun getInfoScreens() = listOf(
            FeedbackScreen,
            AboutUs
        )

        // Get a screen by its route (useful for navigation actions)
        fun getScreenByRoute(route: String?): Screen? {
            return when(route) {
                Authentication.route -> Authentication
                Home.route -> Home
                TextTranslation.route -> TextTranslation
                SpeechTranslation.route -> SpeechTranslation
                TextToSpeech.route -> TextToSpeech
                ImageTranslation.route -> ImageTranslation
                GestureRecognition.route -> GestureRecognition
                FeedbackScreen.route -> FeedbackScreen
                AboutUs.route -> AboutUs
                else -> null
            }
        }
    }
}