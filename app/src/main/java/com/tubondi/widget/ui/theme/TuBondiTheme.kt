package com.tubondi.widget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Semantic light/dark colors and compact spacing shared by Compose and Glance. */
object TuBondiPalette {
    val lightPrimary = Color(0xFF0E627C)
    val lightOnPrimary = Color(0xFFFFFFFF)
    val lightBackground = Color(0xFFF0F5F7)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceVariant = Color(0xFFE8F0F3)
    val lightText = Color(0xFF172D37)
    val lightMuted = Color(0xFF536C77)
    val lightSecondary = Color(0xFF146E68)
    val lightOnSecondary = Color.White
    val lightSecondaryContainer = Color(0xFFD9F1ED)
    val lightOnSecondaryContainer = Color(0xFF003B37)
    val lightPrimaryContainer = Color(0xFFD3EBF2)
    val lightOnPrimaryContainer = Color(0xFF003746)
    val lightTertiary = Color(0xFF815019)
    val lightOnTertiary = Color.White
    val lightOnTertiaryContainer = Color(0xFF51320A)
    val lightEta = Color(0xFF0E714A)
    val lightEtaContainer = Color(0xFFD9F4E5)
    val lightWarning = Color(0xFF815019)
    val lightWarningContainer = Color(0xFFFFF0D6)
    val lightError = Color(0xFFA53D31)
    val lightErrorContainer = Color(0xFFFDE8E4)

    val darkPrimary = Color(0xFF81D7E9)
    val darkOnPrimary = Color(0xFF092A32)
    val darkBackground = Color(0xFF101B22)
    val darkSurface = Color(0xFF1B2A32)
    val darkSurfaceVariant = Color(0xFF233741)
    val darkText = Color(0xFFEDF5F7)
    val darkMuted = Color(0xFFB5C6CC)
    val darkSecondary = Color(0xFF8FE1D4)
    val darkOnSecondary = Color(0xFF003731)
    val darkSecondaryContainer = Color(0xFF1B433F)
    val darkOnSecondaryContainer = Color(0xFFB1F1E3)
    val darkPrimaryContainer = Color(0xFF1D4B56)
    val darkOnPrimaryContainer = Color(0xFFD0F3FF)
    val darkTertiary = Color(0xFFFFCF91)
    val darkOnTertiary = Color(0xFF482B00)
    val darkOnTertiaryContainer = Color(0xFFFFE0B2)
    val darkEta = Color(0xFF9BE8B8)
    val darkEtaContainer = Color(0xFF183B2C)
    val darkWarning = Color(0xFFFFD18E)
    val darkWarningContainer = Color(0xFF43331E)
    val darkError = Color(0xFFFFB4A9)
    val darkErrorContainer = Color(0xFF492A29)
}

object TuBondiSpacing {
    val compact = 8.dp
    val standard = 12.dp
    val content = 16.dp
    val expanded = 24.dp
    val cardRadius = 18.dp
    val largeCardRadius = 20.dp
}

@Composable
fun TuBondiTheme(
    highContrast: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = when {
        highContrast && darkTheme -> darkColorScheme(
            primary = Color(0xFFFFFF00), onPrimary = Color.Black,
            secondary = Color(0xFF00FFFF), onSecondary = Color.Black,
            tertiary = Color(0xFFFFEA00), onTertiary = Color.Black,
            tertiaryContainer = Color(0xFF453900), onTertiaryContainer = Color(0xFFFFF6BF),
            background = Color.Black, onBackground = Color.White,
            surface = Color.Black, onSurface = Color.White,
            surfaceVariant = Color(0xFF161616), onSurfaceVariant = Color.White,
            error = Color(0xFFFF8A80), onError = Color.Black,
            errorContainer = Color(0xFF690005), onErrorContainer = Color(0xFFFFDAD4)
        )
        highContrast -> lightColorScheme(
            primary = Color(0xFF003B4A), onPrimary = Color.White,
            secondary = Color(0xFF004C45), onSecondary = Color.White,
            tertiary = Color(0xFF5C3900), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFF4CC), onTertiaryContainer = Color(0xFF201800),
            background = Color.White, onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            surfaceVariant = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF171717),
            error = Color(0xFF9B0016), onError = Color.White,
            errorContainer = Color(0xFFFFDAD4), onErrorContainer = Color(0xFF410002)
        )
        darkTheme -> darkColorScheme(
            primary = TuBondiPalette.darkPrimary,
            onPrimary = TuBondiPalette.darkOnPrimary,
            secondary = TuBondiPalette.darkSecondary,
            onSecondary = TuBondiPalette.darkOnSecondary,
            secondaryContainer = TuBondiPalette.darkSecondaryContainer,
            onSecondaryContainer = TuBondiPalette.darkOnSecondaryContainer,
            primaryContainer = TuBondiPalette.darkPrimaryContainer,
            onPrimaryContainer = TuBondiPalette.darkOnPrimaryContainer,
            tertiary = TuBondiPalette.darkTertiary,
            onTertiary = TuBondiPalette.darkOnTertiary,
            tertiaryContainer = TuBondiPalette.darkWarningContainer,
            onTertiaryContainer = TuBondiPalette.darkOnTertiaryContainer,
            background = TuBondiPalette.darkBackground,
            onBackground = TuBondiPalette.darkText,
            surface = TuBondiPalette.darkSurface,
            onSurface = TuBondiPalette.darkText,
            surfaceVariant = TuBondiPalette.darkSurfaceVariant,
            onSurfaceVariant = TuBondiPalette.darkMuted,
            error = TuBondiPalette.darkError,
            onError = Color(0xFF492A29),
            errorContainer = TuBondiPalette.darkErrorContainer,
            onErrorContainer = Color(0xFFFFDAD4)
        )
        else -> lightColorScheme(
            primary = TuBondiPalette.lightPrimary,
            onPrimary = TuBondiPalette.lightOnPrimary,
            secondary = TuBondiPalette.lightSecondary,
            onSecondary = TuBondiPalette.lightOnSecondary,
            secondaryContainer = TuBondiPalette.lightSecondaryContainer,
            onSecondaryContainer = TuBondiPalette.lightOnSecondaryContainer,
            primaryContainer = TuBondiPalette.lightPrimaryContainer,
            onPrimaryContainer = TuBondiPalette.lightOnPrimaryContainer,
            tertiary = TuBondiPalette.lightTertiary,
            onTertiary = TuBondiPalette.lightOnTertiary,
            tertiaryContainer = TuBondiPalette.lightWarningContainer,
            onTertiaryContainer = TuBondiPalette.lightOnTertiaryContainer,
            background = TuBondiPalette.lightBackground,
            onBackground = TuBondiPalette.lightText,
            surface = TuBondiPalette.lightSurface,
            onSurface = TuBondiPalette.lightText,
            surfaceVariant = TuBondiPalette.lightSurfaceVariant,
            onSurfaceVariant = TuBondiPalette.lightMuted,
            error = TuBondiPalette.lightError,
            onError = Color.White,
            errorContainer = TuBondiPalette.lightErrorContainer,
            onErrorContainer = Color(0xFF5F160F)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
