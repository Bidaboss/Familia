package fr.familia.app.core.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/*
 * The 7 themes to choose from (DESIGN-43, Kévin 18/11): a theme only changes the mood. It sets the
 * background and its discreet pattern, the dark buttons, the pill of the active tab, the lines and
 * the outlines. It never changes the fonts (chosen apart: TitleFont, BodyFont), the white cards, the
 * colors of the types and of the members, the corners nor the targets: until the 21st every theme
 * keeps the look of Argile (28 dp, relief). Argile, the default, follows the phone's dark mode.
 */

/** How a card stands out: the Argile relief, a hard offset shadow, a soft or glowing one, or none. */
@Immutable
sealed interface ClayShadow {
    /** The Argile relief of the Classique theme: colored shadow, highlight and shade. */
    data object Relief : ClayShadow

    /** A solid copy of the card, moved right and down, without blur (« 4px 4px 0 »). */
    data class Hard(val offset: Dp, val color: Color, val emphasisOffset: Dp = offset) : ClayShadow

    /** A blurred shadow (« 0 8px 18px »), or a glow around the card when [elevation] is the blur alone. */
    data class Soft(val elevation: Dp, val color: Color) : ClayShadow

    data object None : ClayShadow
}

/** The card border of a theme; [dashed] draws it in dashes. */
@Immutable
data class ClayBorder(val width: Dp, val color: Color, val dashed: Boolean = false)

/** The background pattern of a theme, drawn under the content (ThemePatterns.kt). */
enum class ThemePattern {
    None, Snow, Leaves,

    /** « Pépins » of Argile in the R1 bis presentation: three small round seeds on a 64 dp grid. */
    Seeds,

    /** The same seeds in red and yellow (Espagne), in lilac (Lavande), and barely there (Nuit). */
    SeedsSpain, SeedsLilac, SeedsNight,
}

/** Everything a theme sets besides the colors: cards, fonts and background. */
@Immutable
data class ClayStyle(
    val cardRadius: Dp = 28.dp,
    val border: ClayBorder? = null,
    val shadow: ClayShadow = ClayShadow.Relief,
    /** Day headers and the title bar; null keeps the primary color. */
    val dayColor: Color? = null,
    val titleColor: Color? = null,
    val pattern: ThemePattern = ThemePattern.None,
    /** The pill behind the icon of the active tab (DESIGN-43): the yellow of Argile by default. */
    val tabPill: Color = Color(0xFFF2C14E),
)

val LocalClayStyle = staticCompositionLocalOf { ClayStyle() }

/** The colors of a theme, as on its board. */
private class Palette(
    val bg: Long,
    val card: Long,
    val pill: Long,
    val tintB: Long,
    val tintC: Long,
    val primary: Long,
    val onPrimary: Long,
    val accent: Long,
    val text: Long,
    val sec: Long,
    val title: Long,
    val dark: Boolean = false,
)

/** One theme: its Material colors, its Argile colors and its style. Classique follows the phone's dark mode. */
@Immutable
class FamiliaThemeSpec(
    val light: ColorScheme,
    val lightClay: ClayColors,
    val dark: ColorScheme? = null,
    val darkClay: ClayColors? = null,
    val style: ClayStyle = ClayStyle(),
)

/**
 * The 7 themes, in the order of the Apparence screen. [key] is what the phone keeps: Olivier keeps
 * the key of the Jardin it replaces. Classic is Argile.
 */
enum class FamiliaThemeId(val key: String, @StringRes val labelRes: Int) {
    Classic("classic", R.string.theme_classic),
    Olive("garden", R.string.theme_olive),
    Spain("spain", R.string.theme_spain),
    Winter("winter", R.string.theme_winter),
    Lavender("lavender", R.string.theme_lavender),
    Night("night", R.string.theme_night),
    HighContrast("contrast", R.string.theme_contrast),
    ;

    companion object {
        /** The themes removed on 18/11 go to the closest one: Arcade and Cosmos to Nuit, the others to Argile. */
        private val REMOVED = mapOf("arcade" to Night, "cosmos" to Night, "retro" to Classic, "travel" to Classic)

        fun fromKey(key: String?): FamiliaThemeId = entries.firstOrNull { it.key == key } ?: REMOVED[key] ?: Classic
    }
}

private fun scheme(p: Palette): ColorScheme {
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    val onAccent = if (p.dark) Color(p.bg) else Color.White
    return base.copy(
        primary = Color(p.primary),
        onPrimary = Color(p.onPrimary),
        primaryContainer = Color(p.pill),
        onPrimaryContainer = Color(p.text),
        secondary = Color(p.accent),
        onSecondary = onAccent,
        secondaryContainer = Color(p.pill),
        onSecondaryContainer = Color(p.text),
        tertiary = Color(p.accent),
        onTertiary = onAccent,
        tertiaryContainer = Color(p.tintC),
        onTertiaryContainer = Color(p.text),
        background = Color(p.bg),
        onBackground = Color(p.text),
        surface = Color(p.bg),
        onSurface = Color(p.text),
        surfaceVariant = Color(p.pill),
        onSurfaceVariant = Color(p.sec),
        surfaceContainerLowest = Color(p.card),
        surfaceContainerLow = Color(p.card),
        surfaceContainer = Color(p.card),
        surfaceContainerHigh = Color(p.card),
        surfaceContainerHighest = Color(p.pill),
        outline = Color(p.sec),
        outlineVariant = Color(p.pill),
        inverseSurface = Color(p.text),
        inverseOnSurface = Color(p.bg),
    )
}

private fun clay(p: Palette, shadow: Color, tintAsDot: Boolean) = ClayColors(
    card = Color(p.card),
    household = Color(p.tintB),
    family = Color(p.tintC),
    birthday = Color(p.pill),
    onPlainVariant = Color(p.sec),
    onHouseholdVariant = Color(p.sec),
    onFamilyVariant = Color(p.sec),
    dayTitle = Color(p.accent),
    shadow = shadow,
    // The themes have their own relief: no Argile highlight or shade on their cards.
    highlight = Color.Transparent,
    shade = Color.Transparent,
    tintAsStripe = false,
    tintAsDot = tintAsDot,
)

/** [strongTints] keeps the tinted cards (Contraste fort: DESIGN-11). */
private fun themed(p: Palette, style: ClayStyle, shadow: Color = Color.Transparent, strongTints: Boolean = p.dark) = FamiliaThemeSpec(
    light = scheme(p),
    lightClay = clay(p, shadow, tintAsDot = !strongTints),
    style = style.copy(dayColor = Color(p.accent), titleColor = Color(p.title)),
)

/**
 * The structure shared by the themes (DESIGN, 18/11): the look of Argile until the 21st, 28 dp
 * corners and its relief, no outline. Only the pattern and the pill of the active tab change; the
 * dark button is the theme's primary color.
 */
private fun mood(pattern: ThemePattern, tabPill: Long) = ClayStyle(pattern = pattern, tabPill = Color(tabPill))

/** The spec of each theme. Only Argile follows the dark mode; Nuit is the dark Argile, always. */
fun themeSpec(id: FamiliaThemeId): FamiliaThemeSpec = when (id) {
    FamiliaThemeId.Classic -> ClassicSpec
    FamiliaThemeId.Olive -> themed(
        Palette(0xFFF0F3E8, 0xFFFFFFFF, 0xFFC9DBA8, 0xFFE3EED8, 0xFFF6E7D0, 0xFF2F3B22, 0xFFFFFFFF, 0xFF3F6B2E, 0xFF1E2B1F, 0xFF4A5A4B, 0xFF2F3B22),
        mood(ThemePattern.Leaves, 0xFFC9DBA8),
        shadow = Color(0x242F3B22),
    )
    FamiliaThemeId.Spain -> themed(
        // The colors of the flag (DESIGN, 16/11), softened on 18/11: no band, no Abril Fatface.
        Palette(0xFFFFF8E6, 0xFFFFFFFF, 0xFFF1BF00, 0xFFFDEDB3, 0xFFF8D9D6, 0xFFAA151B, 0xFFFFFFFF, 0xFF8A1015, 0xFF2A1A12, 0xFF5C3A2E, 0xFFAA151B),
        mood(ThemePattern.SeedsSpain, 0xFFF1BF00),
        shadow = Color(0x24AA151B),
    )
    FamiliaThemeId.Winter -> themed(
        Palette(0xFFEAF2F8, 0xFFFFFFFF, 0xFFF6DADA, 0xFFDDEBF5, 0xFFF6DADA, 0xFF13293D, 0xFFFFFFFF, 0xFF1E5B7A, 0xFF13293D, 0xFF3E566B, 0xFFB3262E),
        mood(ThemePattern.Snow, 0xFFF6DADA),
        shadow = Color(0x1F13293D),
    )
    FamiliaThemeId.Lavender -> themed(
        Palette(0xFFF5F2FB, 0xFFFFFFFF, 0xFFD9CCF7, 0xFFE6E0F7, 0xFFFFF1C9, 0xFF2B2350, 0xFFFFFFFF, 0xFF5B4A9E, 0xFF1F1A33, 0xFF4E4766, 0xFF2B2350),
        mood(ThemePattern.SeedsLilac, 0xFFD9CCF7),
        shadow = Color(0x242B2350),
    )
    FamiliaThemeId.Night -> FamiliaThemeSpec(
        // The dark Argile, always; its dark button is light, with dark text (DESIGN, 18/11).
        light = DarkColors.copy(primary = Color(0xFFEBDCD3), onPrimary = Color(0xFF1E1511)),
        lightClay = DarkClayColors,
        style = ClayStyle(pattern = ThemePattern.SeedsNight),
    )
    FamiliaThemeId.HighContrast -> themed(
        Palette(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFE08A, 0xFFE6F0FF, 0xFFFFF0D6, 0xFF000000, 0xFFFFFFFF, 0xFF003C8F, 0xFF000000, 0xFF1A1A1A, 0xFF000000),
        ClayStyle(border = ClayBorder(2.dp, Color(0xFF1A1A1A)), shadow = ClayShadow.None, tabPill = Color(0xFFFFE08A)),
        strongTints = true,
    )
}

/** The Argile theme of the app until now, in light and dark mode: nothing changes for it. */
private val ClassicSpec = FamiliaThemeSpec(
    light = LightColors,
    lightClay = LightClayColors,
    dark = DarkColors,
    darkClay = DarkClayColors,
)
