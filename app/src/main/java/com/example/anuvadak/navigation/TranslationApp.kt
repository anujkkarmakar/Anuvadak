package com.example.anuvadak.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.anuvadak.ui.auth.AuthenticationScreen
import com.example.anuvadak.ui.auth.AuthenticationViewModel
import com.example.anuvadak.ui.gesture.GestureRecognitionScreen
import com.example.anuvadak.ui.screens.*
import com.example.anuvadak.ui.theme.AnuvadakTheme

/**
 * Root composable that sets up the navigation structure of the app.
 * Uses proper BackHandler implementation to manage back button behavior.
 */
@Composable
fun TranslationApp() {
    val navController = rememberNavController()
    val authViewModel: AuthenticationViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()

    // Track current destination for back button logic
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(
        Screen.TextTranslation,
        Screen.SpeechTranslation,
        Screen.TextToSpeech,
        Screen.ImageTranslation,
        Screen.GestureRecognition
    )

    // Global back handler - only intercept back presses when NOT on home screen
    BackHandler(enabled = currentRoute != Screen.Home.route) {
        when (currentRoute) {
            "auth" -> {
                // On auth screen - do nothing (prevents app exit)
            }
            Screen.AboutUs.route, Screen.FeedbackScreen.route -> {
                // On info screens - go back to home
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            else -> {
                // On any feature screen - go to home
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState.isAuthenticated) Screen.Home.route else "auth"
    ) {
        // Authentication Screen
        composable("auth") {
            AuthenticationScreen(
                onAuthenticationSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo("auth") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                viewModel = authViewModel
            )
        }

        // Home Screen - Main destination
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToMainApp = {
                    navController.navigate(Screen.TextTranslation.route)
                },
                onNavigateToFeedback = {
                    navController.navigate(Screen.FeedbackScreen.route)
                },
                onNavigateToFeature = { screen ->
                    navController.navigate(screen.route)
                },
                onNavigateToAboutUs = {
                    navController.navigate(Screen.AboutUs.route)
                },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate("auth") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Feedback Screen
        composable(Screen.FeedbackScreen.route) {
            FeedbackScreen()
        }

        // About Us Screen
        composable(Screen.AboutUs.route) {
            AboutUsScreen()
        }

        // Main App Screens with Bottom Navigation
        composable(Screen.TextTranslation.route) {
            MainAppScaffold(
                navController = navController,
                bottomNavScreens = bottomNavScreens,
                currentScreen = Screen.TextTranslation,
                authViewModel = authViewModel
            ) {
                TextTranslationScreen()
            }
        }

        composable(Screen.SpeechTranslation.route) {
            MainAppScaffold(
                navController = navController,
                bottomNavScreens = bottomNavScreens,
                currentScreen = Screen.SpeechTranslation,
                authViewModel = authViewModel
            ) {
                SpeechTranslationScreen()
            }
        }

        composable(Screen.TextToSpeech.route) {
            MainAppScaffold(
                navController = navController,
                bottomNavScreens = bottomNavScreens,
                currentScreen = Screen.TextToSpeech,
                authViewModel = authViewModel
            ) {
                TextToSpeechScreen()
            }
        }

        composable(Screen.ImageTranslation.route) {
            MainAppScaffold(
                navController = navController,
                bottomNavScreens = bottomNavScreens,
                currentScreen = Screen.ImageTranslation,
                authViewModel = authViewModel
            ) {
                ImageTranslationScreen()
            }
        }

        // Gesture Recognition Screen
        composable(Screen.GestureRecognition.route) {
            MainAppScaffold(
                navController = navController,
                bottomNavScreens = bottomNavScreens,
                currentScreen = Screen.GestureRecognition,
                authViewModel = authViewModel
            ) {
                GestureRecognitionScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold(
    navController: NavHostController,
    bottomNavScreens: List<Screen>,
    currentScreen: Screen,
    authViewModel: AuthenticationViewModel,
    content: @Composable () -> Unit
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        authViewModel.signOut()
                        navController.navigate("auth") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            Screen.GestureRecognition -> "🤟 ${currentScreen.label}"
                            Screen.TextTranslation -> "📝 ${currentScreen.label}"
                            Screen.SpeechTranslation -> "🎤 ${currentScreen.label}"
                            Screen.TextToSpeech -> "🔊 ${currentScreen.label}"
                            Screen.ImageTranslation -> "📷 ${currentScreen.label}"
                            else -> currentScreen.label
                        }
                    )
                },
                navigationIcon = {
                    // Home button in top bar - simple navigation
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // About Us button in top bar
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.AboutUs.route)
                        }
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = "About Us",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Sign out button
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                tint = if (currentScreen.route == screen.route)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                color = if (currentScreen.route == screen.route)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        selected = currentScreen.route == screen.route,
                        onClick = {
                            if (screen.route != currentScreen.route) {
                                navController.navigate(screen.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, name = "App Navigation Preview")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "App Navigation Preview - Dark"
)
@Composable
fun TranslationAppPreview() {
    AnuvadakTheme {
        Surface {
            Text("App Navigation Preview - Run on device/emulator for full experience")
        }
    }
}