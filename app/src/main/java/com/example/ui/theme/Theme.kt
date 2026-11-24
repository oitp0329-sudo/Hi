package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.core.model.AppThemeMode

data class FileDudeExtendedColors(
    val border: Color,
    val paleMint: Color,
    val darkGreenText: Color,
    val secondarySurface: Color,
    val warning: Color,
    val warningContainer: Color
)

val LocalFileDudeColors = staticCompositionLocalOf {
    FileDudeExtendedColors(
        border = FdBorder,
        paleMint = FdPaleMint,
        darkGreenText = FdDarkGreenText,
        secondarySurface = FdSecondarySurface,
        warning = FdWarning,
        warningContainer = FdWarningContainer
    )
}

private val FileDudeLightColorScheme = lightColorScheme(
    primary = FdPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = FdPaleMint,
    onPrimaryContainer = FdDarkGreenText,
    secondary = FdDarkGreenText,
    onSecondary = Color.White,
    secondaryContainer = FdSecondarySurface,
    onSecondaryContainer = FdMainText,
    tertiary = FdWarning,
    onTertiary = Color.White,
    tertiaryContainer = FdWarningContainer,
    onTertiaryContainer = FdMainText,
    background = FdMainBackground,
    onBackground = FdMainText,
    surface = FdCardBackground,
    onSurface = FdMainText,
    surfaceVariant = FdSecondarySurface,
    onSurfaceVariant = FdSecondaryText,
    outline = FdBorder,
    outlineVariant = FdBorder,
    error = FdError,
    onError = Color.White,
    errorContainer = FdErrorContainer,
    onErrorContainer = FdError
)

private val FileDudeDarkColorScheme = darkColorScheme(
    primary = FdDarkPrimaryGreen,
    onPrimary = Color(0xFF0A2418),
    primaryContainer = FdDarkMintContainer,
    onPrimaryContainer = FdPaleMint,
    secondary = FdPaleMint,
    onSecondary = FdDarkGreenText,
    secondaryContainer = FdDarkSecondarySurface,
    onSecondaryContainer = FdDarkMainText,
    tertiary = FdWarning,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = Color(0xFF3E2907),
    onTertiaryContainer = Color(0xFFFFE5BE),
    background = FdDarkBackground,
    onBackground = FdDarkMainText,
    surface = FdDarkCard,
    onSurface = FdDarkMainText,
    surfaceVariant = FdDarkSecondarySurface,
    onSurfaceVariant = FdDarkSecondaryText,
    outline = FdDarkBorder,
    outlineVariant = FdDarkBorder,
    error = Color(0xFFEB7571),
    onError = Color(0xFF3D0A09),
    errorContainer = Color(0xFF4C1716),
    onErrorContainer = Color(0xFFFFD8D6)
)

private val FileDudeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FileDudeTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) FileDudeDarkColorScheme else FileDudeLightColorScheme
    val extendedColors = if (useDarkTheme) {
        FileDudeExtendedColors(
            border = FdDarkBorder,
            paleMint = FdDarkMintContainer,
            darkGreenText = FdPaleMint,
            secondarySurface = FdDarkSecondarySurface,
            warning = FdWarning,
            warningContainer = Color(0xFF3E2907)
        )
    } else {
        FileDudeExtendedColors(
            border = FdBorder,
            paleMint = FdPaleMint,
            darkGreenText = FdDarkGreenText,
            secondarySurface = FdSecondarySurface,
            warning = FdWarning,
            warningContainer = FdWarningContainer
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalFileDudeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FileDudeTypography,
            shapes = FileDudeShapes,
            content = content
        )
    }
}
