package com.example.anuvadak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.anuvadak.navigation.TranslationApp
import com.example.anuvadak.ui.theme.AnuvadakTheme

/**
 * Main entry point of the Translation App.
 * Allows natural back button behavior - app closes when back is pressed on HomeScreen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AnuvadakTheme {
                TranslationApp()
            }
        }
    }

    // No override of onBackPressed - allows natural Android back button behavior
    // This means:
    // - HomeScreen + Back = App closes (normal Android behavior)
    // - Feature Screens + Back = Handled by BackHandler in Compose (goes to Home)
}