package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = SaraRose,
    onSecondary = Color(0xFF3F0012),
    secondaryContainer = SaraRoseContainerDark,
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = NeuralViolet,
    onTertiary = Color(0xFF0F0D3E),
    tertiaryContainer = NeuralVioletContainerDark,
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = ObsidianBg,
    onBackground = Color(0xFFE2E8F0),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianOutline,
    error = CrimsonError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF083344),
    secondary = SaraRoseDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4E6),
    onSecondaryContainer = Color(0xFF4C0519),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = FrostBg,
    onBackground = Color(0xFF0F172A),
    surface = FrostSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = FrostSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = FrostOutline,
    error = CrimsonError,
    onError = Color.White
)

val SaraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SaraAiTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SaraShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SaraAiTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
