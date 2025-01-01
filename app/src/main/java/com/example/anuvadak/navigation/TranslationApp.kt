package com.example.anuvadak.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import com.example.anuvadak.ui.screens.*

/**
 * Root composable that sets up the navigation structure of the app.
 * Implements bottom navigation and handles screen navigation.
 */
@Composable
fun TranslationApp() {
    val navController = rememberNavController()
    val screens = Screen.getAllScreens()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.TextTranslation.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.TextTranslation.route) {
                TextTranslationScreen()
            }
            composable(Screen.SpeechTranslation.route) {
                SpeechTranslationScreen()
            }
            composable(Screen.TextToSpeech.route) {
                TextToSpeechScreen()
            }
            composable(Screen.ImageTranslation.route) {
                ImageTranslationScreen()
            }
        }
    }
}

@Preview(showBackground = true, name = "Navigation Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Navigation Preview - Dark"
)
@Composable
fun TranslationAppPreview() {
    TranslationApp()
}