package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryRed,
    onPrimary = Color.White,
    secondary = RoyalGold,
    onSecondary = BackgroundBlack,
    tertiary = DarkRed,
    background = BackgroundBlack,
    onBackground = Color.White,
    surface = CardBlack,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color.LightGray,
    error = DarkRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the luxury gaming brand identity
    dynamicColor: Boolean = false, // Disable dynamic colors to keep royal red/gold active
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
