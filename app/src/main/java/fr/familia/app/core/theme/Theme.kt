package fr.familia.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

internal val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

internal val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

/**
 * Wraps every Familia screen with its theme and the Argile style, in light or dark mode.
 * [theme] is the one chosen on this phone (Famille › Réglages › Apparence); Classique, the
 * terracotta Argile, follows the phone's dark mode, and the other themes keep their own look.
 * Android 12 dynamic color is intentionally not used (see Color.kt).
 */
@Composable
fun FamiliaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: FamiliaThemeId = FamiliaThemeId.Classic,
    content: @Composable () -> Unit,
) {
    val spec = themeSpec(theme)
    val dark = darkTheme && spec.dark != null
    CompositionLocalProvider(
        LocalClayColors provides if (dark) spec.darkClay ?: spec.lightClay else spec.lightClay,
        LocalClayStyle provides spec.style,
    ) {
        MaterialTheme(
            colorScheme = if (dark) spec.dark ?: spec.light else spec.light,
            typography = familiaTypography(spec.style),
            shapes = FamiliaShapes,
            content = content,
        )
    }
}

/** The Argile colors of the current theme (cards, tints, reliefs). */
object FamiliaClay {
    val colors: ClayColors
        @Composable @ReadOnlyComposable
        get() = LocalClayColors.current

    /** The cards, fonts and background of the current theme. */
    val style: ClayStyle
        @Composable @ReadOnlyComposable
        get() = LocalClayStyle.current
}

/** Day headers of the planning: the day font and color of the theme (Classique: Fredoka, primary). */
@Composable
@ReadOnlyComposable
fun dayHeaderTextStyle(): TextStyle = dayHeaderStyle(LocalClayStyle.current, MaterialTheme.typography.titleLarge)

@Composable
@ReadOnlyComposable
fun dayHeaderColor(): Color = LocalClayStyle.current.dayColor ?: MaterialTheme.colorScheme.primary
