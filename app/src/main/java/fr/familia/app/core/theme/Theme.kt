package fr.familia.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
 * [theme] is the one shown on this phone (Réglages › Thème, récit 113): a photo theme, or Contraste
 * fort; Classique, the terracotta Argile while the Thèmes module is off, follows the phone's dark
 * mode, and the others keep their own look (DESIGN-160: the same in dark mode).
 * Android 12 dynamic color is intentionally not used (see Color.kt).
 */
@Composable
fun FamiliaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: FamiliaThemeId = FamiliaThemeId.Classic,
    /** The R1 bis presentation gives Classique its « pépins » (light only); the other themes keep theirs. */
    seeds: Boolean = false,
    /** « Police des titres » and « Police du texte », chosen apart from the theme (DESIGN-45 and 46). */
    titleFont: TitleFont = TitleFont.Round,
    bodyFont: BodyFont = BodyFont.Normal,
    content: @Composable () -> Unit,
) {
    val spec = themeSpec(theme)
    val dark = darkTheme && spec.dark != null
    val style = if (seeds && theme == FamiliaThemeId.Classic && !darkTheme) spec.style.copy(pattern = ThemePattern.Seeds) else spec.style
    CompositionLocalProvider(
        LocalClayColors provides if (dark) spec.darkClay ?: spec.lightClay else spec.lightClay,
        LocalClayStyle provides style,
        LocalTitleFont provides titleFont,
        LocalPhotoTheme provides theme.takeIf { it.isPhoto },
    ) {
        MaterialTheme(
            colorScheme = if (dark) spec.dark ?: spec.light else spec.light,
            typography = familiaTypography(titleFont, bodyFont),
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

/** The title font chosen on this phone, for the day headers. */
val LocalTitleFont = staticCompositionLocalOf { TitleFont.Round }

/** Day headers of the planning: the title font and the day color of the theme (Argile: Fredoka, primary). */
@Composable
@ReadOnlyComposable
fun dayHeaderTextStyle(): TextStyle = dayHeaderStyle(LocalTitleFont.current, MaterialTheme.typography.titleLarge)

@Composable
@ReadOnlyComposable
fun dayHeaderColor(): Color = LocalClayStyle.current.dayColor ?: MaterialTheme.colorScheme.primary
