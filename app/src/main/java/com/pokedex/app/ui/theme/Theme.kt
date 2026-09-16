package com.pokedex.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DexLightColors = lightColorScheme(
    primary = DexRed,
    onPrimary = Color.White,
    primaryContainer = DexRedDark,
    onPrimaryContainer = Color.White,
    secondary = DexLensGlass,
    onSecondary = Color.White,
    secondaryContainer = DexLensDeep,
    onSecondaryContainer = Color.White,
    tertiary = DexLedYellow,
    onTertiary = Color(0xFF3A2E00),
    background = DexRed,
    onBackground = Color.White,
    surface = DexScreen,
    onSurface = DexReadout,
    surfaceVariant = DexScreenEdge,
    onSurfaceVariant = DexReadoutDim,
    outline = DexScreenEdge,
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3A0906),
)

private val DexDarkColors = darkColorScheme(
    primary = DexRedBright,
    onPrimary = Color.White,
    primaryContainer = DexRedDark,
    onPrimaryContainer = Color.White,
    secondary = DexLensCyan,
    onSecondary = Color(0xFF04263F),
    secondaryContainer = DexLensDeep,
    onSecondaryContainer = Color.White,
    tertiary = DexLedYellow,
    onTertiary = Color(0xFF3A2E00),
    background = DexRedDark,
    onBackground = Color.White,
    surface = DexScreen,
    onSurface = DexReadout,
    surfaceVariant = DexScreenEdge,
    onSurfaceVariant = DexReadoutDim,
    outline = DexScreenEdge,
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3A0906),
)

@Composable
fun PokeDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** The anime-Pokédex device look is fixed by default; opt in to Material You. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DexDarkColors
        else -> DexLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = DexRed.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
