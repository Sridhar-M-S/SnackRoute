package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SnackOrangeDarkPrimary,
    secondary = SnackOrangeDarkSecondary,
    tertiary = SnackOrangeDarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = SnackOrangePrimary,
    secondary = SnackOrangeSecondary,
    tertiary = SnackOrangeTertiary,
    background = LightGrayBackground,
    surface = CleanWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkGrayBackground,
    onSurface = DarkGrayBackground,
    surfaceVariant = SnackOrangeTertiary,
    onSurfaceVariant = Color(0xFF79747E)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
