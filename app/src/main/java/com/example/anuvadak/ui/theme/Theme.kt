package com.example.anuvadak.ui.theme

import android.app.Activity
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Saffron80,
    onPrimary = Grey900,
    primaryContainer = SaffronGrey40,
    onPrimaryContainer = White,

    secondary = Green80,
    onSecondary = Grey900,
    secondaryContainer = Green40,
    onSecondaryContainer = White,

    tertiary = SaffronGrey80,
    onTertiary = Grey900,
    tertiaryContainer = SaffronGrey40,
    onTertiaryContainer = White,

    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRedLight,
    onErrorContainer = Grey900,

    background = Grey900,
    onBackground = White,
    surface = Grey800,
    onSurface = White,
    surfaceVariant = Grey700,
    onSurfaceVariant = Grey200,

    outline = Grey500,
    outlineVariant = Grey600,
    scrim = Grey900,

    inverseSurface = OffWhite,
    inverseOnSurface = Grey900,
    inversePrimary = Saffron40
)

private val LightColorScheme = lightColorScheme(
    primary = Saffron40,
    onPrimary = White,
    primaryContainer = LightSaffron,
    onPrimaryContainer = SaffronGrey40,

    secondary = Green40,
    onSecondary = White,
    secondaryContainer = LightGreen,
    onSecondaryContainer = DarkGreen,

    tertiary = SaffronGrey40,
    onTertiary = White,
    tertiaryContainer = LightSaffron,
    onTertiaryContainer = SaffronGrey40,

    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRedLight,
    onErrorContainer = Grey900,

    background = White,
    onBackground = Grey900,
    surface = OffWhite,
    onSurface = Grey900,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,

    outline = Grey400,
    outlineVariant = Grey300,
    scrim = Grey900,

    inverseSurface = Grey800,
    inverseOnSurface = White,
    inversePrimary = Saffron80
)

/**
 * App theme composable that provides Material 3 theming with Indian flag colors.
 * Handles both light and dark themes with saffron, white, and green color scheme.
 */
@Composable
fun AnuvadakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to use our custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Preview(showBackground = true, name = "Indian Flag Theme Preview Light")
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    name = "Indian Flag Theme Preview Dark"
)
@Composable
fun ThemePreview() {
    AnuvadakTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Anuvadak Translation App",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sample text with Indian flag colors",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Secondary color text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}