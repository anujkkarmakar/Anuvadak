package com.example.anuvadak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.anuvadak.navigation.TranslationApp
import com.example.anuvadak.ui.theme.AnuvadakTheme

/**
 * Main entry point of the Translation App.
 * Hosts the root composable and sets up the app theme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnuvadakTheme {
                TranslationApp()
            }
        }
    }
}