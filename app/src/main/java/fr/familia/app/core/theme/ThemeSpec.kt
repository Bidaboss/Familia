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
 * The photo themes (récit 113, règles 14 à 18, 25/11): a theme is a photo and two colors, an
 * accent and a dark one. They replace the 7 color themes of DESIGN-43. A theme never changes the
 * fonts (chosen apart: TitleFont, BodyFont), the white cards, the colors of the types and of the
 * members, the corners nor the targets. « Selon la saison » is the default (ThemeChoice). « Contraste
 * fort » is apart, an accessibility setting: plain, without photo. Argile stays the look of the app
 * while the Thèmes module is off, and the only one that follows the phone's dark mode.
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
    None,

    /** « Pépins » of Argile in the R1 bis presentation: three small round seeds on a 64 dp grid. */
    Seeds,
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
 * The looks of the app. [key] is what the phone keeps. Classic is Argile, while the Thèmes module is
 * off; then the 8 photo themes (A5, DESIGN-159 B: the Unsplash ones at launch), each with its [accent]
 * (« J'y vais », the avatar, the counters) and its [dark] (the switch, the chosen day, the central
 * button); and « Contraste fort », chosen apart (règle 18).
 */
enum class FamiliaThemeId(val key: String, @StringRes val labelRes: Int, val accent: Long? = null, val dark: Long? = null) {
    Classic("classic", R.string.theme_classic),
    Clouds("u_nuages", R.string.theme_clouds, 0xFF6E8FB5, 0xFF2B3A4E),
    Alps("u_alpes", R.string.theme_alps, 0xFF4F8A6B, 0xFF24392F),
    Sunset("u_plage", R.string.theme_sunset, 0xFFE8875A, 0xFF4E2E3A),
    Christmas("u_noel", R.string.theme_christmas, 0xFFC0503F, 0xFF1E2D4A),
    Autumn("u_automne", R.string.theme_autumn, 0xFFD98A2B, 0xFF3A2A1A),
    Lake("u_lac", R.string.theme_lake, 0xFF2F9AA8, 0xFF1C3940),
    Lavender("u_lavande", R.string.theme_lavender, 0xFF8A6BC0, 0xFF33284A),
    Coast("u_cote", R.string.theme_coast, 0xFF3C8FB8, 0xFF1D3848),
    HighContrast("contrast", R.string.theme_contrast),
    ;

    /** True for the 8 themes with a photo. */
    val isPhoto: Boolean get() = accent != null

    companion object {
        /** The photo themes, in the order of Réglages > Thème. */
        val photos: List<FamiliaThemeId> = entries.filter { it.isPhoto }
    }
}

/** The cream under the photo and on the screens without one (A5). */
private const val PHOTO_CREAM = 0xFFFFFAF2

/** [t] of the way from [from] to [to], both opaque ARGB. */
private fun mix(from: Long, to: Long, t: Float): Long {
    fun channel(shift: Int): Long {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).toLong().coerceIn(0, 255) shl shift
    }
    return 0xFF000000 or channel(16) or channel(8) or channel(0)
}

/**
 * A photo theme on the screens: cream background, white cards, the dark color for the buttons, the
 * day headers and the titles, the accent lightly on the pills and the tints. The accent itself, too
 * light for a white text, is kept for the home (LocalPhotoTheme).
 */
private fun photoSpec(id: FamiliaThemeId): FamiliaThemeSpec {
    val accent = id.accent ?: error("$id has no photo")
    val dark = id.dark ?: error("$id has no photo")
    val pill = mix(PHOTO_CREAM, accent, 0.3f)
    return themed(
        Palette(
            bg = PHOTO_CREAM,
            card = 0xFFFFFFFF,
            pill = pill,
            tintB = mix(PHOTO_CREAM, accent, 0.16f),
            tintC = mix(PHOTO_CREAM, dark, 0.08f),
            primary = dark,
            onPrimary = 0xFFFFFFFF,
            accent = dark,
            text = 0xFF231A16,
            sec = 0xFF3F342C,
            title = dark,
        ),
        mood(ThemePattern.None, pill),
        shadow = Color((0x24L shl 24) or (dark and 0xFFFFFF)),
    )
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

/** The spec of each look. Only Argile follows the dark mode. */
fun themeSpec(id: FamiliaThemeId): FamiliaThemeSpec = when (id) {
    FamiliaThemeId.Classic -> ClassicSpec
    FamiliaThemeId.HighContrast -> themed(
        Palette(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFE08A, 0xFFE6F0FF, 0xFFFFF0D6, 0xFF000000, 0xFFFFFFFF, 0xFF003C8F, 0xFF000000, 0xFF1A1A1A, 0xFF000000),
        ClayStyle(border = ClayBorder(2.dp, Color(0xFF1A1A1A)), shadow = ClayShadow.None, tabPill = Color(0xFFFFE08A)),
        strongTints = true,
    )
    else -> photoSpec(id)
}

/** The Argile theme of the app until now, in light and dark mode: nothing changes for it. */
private val ClassicSpec = FamiliaThemeSpec(
    light = LightColors,
    lightClay = LightClayColors,
    dark = DarkColors,
    darkClay = DarkClayColors,
)
