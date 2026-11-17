package fr.familia.app.core.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/*
 * The 9 themes to choose from (DESIGN-7, 15/11): values copied from the « planche des thèmes »,
 * rules from the « Thèmes au choix » section of the design principles. A theme changes the colors,
 * the background and its pattern, the font of titles and day headers, the corners, the border and
 * the shadow of the cards. It never changes the layout, the texts, the icons, the 56 dp targets,
 * the members' colors, nor the body text (always Nunito).
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

/** The card border of a theme; [dashed] for the Carnet de voyage. */
@Immutable
data class ClayBorder(val width: Dp, val color: Color, val dashed: Boolean = false)

/** The background pattern of a theme, drawn under the content (ThemePatterns.kt). */
enum class ThemePattern {
    None, PixelDots, Stars, FlagBand, Snow, Leaves, Stripes70, NotebookLines,

    /** « Pépins » of Classique in the R1 bis presentation: three small round seeds on a 64 dp grid. */
    Seeds,
}

/** Everything a theme sets besides the colors: cards, fonts and background. */
@Immutable
data class ClayStyle(
    val cardRadius: Dp = 28.dp,
    val border: ClayBorder? = null,
    val shadow: ClayShadow = ClayShadow.Relief,
    /** Font of titles (titles of screens and cards); null keeps Fredoka. */
    val titleFont: FontFamily? = null,
    /** Font of the day headers of the planning; null keeps the title font. */
    val dayFont: FontFamily? = null,
    /** A wide display font is set a little smaller, so titles keep to their line. */
    val titleScale: Float = 1f,
    /** Day headers and the title bar; null keeps the primary color. */
    val dayColor: Color? = null,
    val titleColor: Color? = null,
    val pattern: ThemePattern = ThemePattern.None,
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

/** The 9 themes, in the order of the Apparence screen. [key] is what the phone keeps. */
enum class FamiliaThemeId(val key: String, @StringRes val labelRes: Int) {
    Classic("classic", R.string.theme_classic),
    Arcade("arcade", R.string.theme_arcade),
    Retro("retro", R.string.theme_retro),
    Travel("travel", R.string.theme_travel),
    Garden("garden", R.string.theme_garden),
    Cosmos("cosmos", R.string.theme_cosmos),
    Winter("winter", R.string.theme_winter),
    Spain("spain", R.string.theme_spain),
    HighContrast("contrast", R.string.theme_contrast),
    ;

    companion object {
        fun fromKey(key: String?): FamiliaThemeId = entries.firstOrNull { it.key == key } ?: Classic
    }
}

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(resId, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) },
)

private val PressStart = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))
private val Silkscreen = FontFamily(Font(R.font.silkscreen, FontWeight.Normal))
private val Shrikhand = FontFamily(Font(R.font.shrikhand, FontWeight.Normal))
private val AbrilFatface = FontFamily(Font(R.font.abril_fatface, FontWeight.Normal))
private val Kalam = FontFamily(Font(R.font.kalam_bold, FontWeight.Bold))
private val Fraunces = variableFamily(R.font.fraunces, FontWeight.SemiBold, FontWeight.Bold)
private val Orbitron = variableFamily(R.font.orbitron, FontWeight.SemiBold, FontWeight.Bold)

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

/** [strongTints] keeps the tinted cards (dark themes, Contraste fort: DESIGN-11). */
private fun themed(p: Palette, style: ClayStyle, shadow: Color = Color.Transparent, strongTints: Boolean = p.dark) = FamiliaThemeSpec(
    light = scheme(p),
    lightClay = clay(p, shadow, tintAsDot = !strongTints),
    style = style.copy(dayColor = Color(p.accent), titleColor = Color(p.title)),
)

/** The spec of each theme. Arcade and Cosmos are dark by nature; the others keep their light version. */
fun themeSpec(id: FamiliaThemeId): FamiliaThemeSpec = when (id) {
    FamiliaThemeId.Classic -> ClassicSpec
    FamiliaThemeId.Arcade -> themed(
        Palette(0xFF140B2E, 0xFF22164A, 0xFF3A2A7A, 0xFF1B2A55, 0xFF3A1748, 0xFFFFD23F, 0xFF1A0F00, 0xFF00E5FF, 0xFFF4F0FF, 0xFFC9C0F0, 0xFFFFD23F, dark = true),
        ClayStyle(
            cardRadius = 6.dp,
            border = ClayBorder(2.dp, Color(0xFF5B3DF5)),
            shadow = ClayShadow.Hard(3.dp, Color(0xFF00B8CC), emphasisOffset = 5.dp),
            titleFont = PressStart,
            dayFont = Silkscreen,
            titleScale = 0.8f,
            pattern = ThemePattern.PixelDots,
        ),
    )
    FamiliaThemeId.Retro -> themed(
        Palette(0xFFF6EAD7, 0xFFFFF8EC, 0xFFF2C66D, 0xFFF7D9A8, 0xFFE3EBCB, 0xFFA8431F, 0xFFFFFFFF, 0xFF8A3A1A, 0xFF3A2418, 0xFF6B4A36, 0xFFA8431F),
        ClayStyle(
            cardRadius = 22.dp,
            border = ClayBorder(2.dp, Color(0xFF6B3E26)),
            shadow = ClayShadow.Hard(3.dp, Color(0xFF6B3E26), emphasisOffset = 4.dp),
            titleFont = Shrikhand,
            dayFont = Shrikhand,
            pattern = ThemePattern.Stripes70,
        ),
    )
    FamiliaThemeId.Travel -> themed(
        Palette(0xFFF3EDE1, 0xFFFFFDF8, 0xFFE2D6BE, 0xFFDCE7F2, 0xFFF4E2C4, 0xFF1F3A5F, 0xFFFFFFFF, 0xFF8F2F1C, 0xFF1C2430, 0xFF4A5566, 0xFF1F3A5F),
        ClayStyle(
            cardRadius = 14.dp,
            border = ClayBorder(2.dp, Color(0xFF1F3A5F), dashed = true),
            shadow = ClayShadow.Soft(6.dp, Color(0x261F3A5F)),
            titleFont = Kalam,
            dayFont = Kalam,
            pattern = ThemePattern.NotebookLines,
        ),
        shadow = Color(0x261F3A5F),
    )
    FamiliaThemeId.Garden -> themed(
        Palette(0xFFEEF3E6, 0xFFFFFFFF, 0xFFD7E6C3, 0xFFE3EED8, 0xFFF6E7D0, 0xFF2F5D3A, 0xFFFFFFFF, 0xFF3F6B2E, 0xFF1E2B1F, 0xFF4A5A4B, 0xFF2F5D3A),
        ClayStyle(
            cardRadius = 26.dp,
            shadow = ClayShadow.Soft(10.dp, Color(0x242F5D3A)),
            titleFont = Fraunces,
            dayFont = Fraunces,
            pattern = ThemePattern.Leaves,
        ),
        shadow = Color(0x242F5D3A),
    )
    FamiliaThemeId.Cosmos -> themed(
        Palette(0xFF0B1026, 0xFF161D3F, 0xFF2A3366, 0xFF1A2A4F, 0xFF2E1F4F, 0xFF8FB8FF, 0xFF0B1026, 0xFFFFB86B, 0xFFEEF1FF, 0xFFB8C0E6, 0xFF8FB8FF, dark = true),
        ClayStyle(
            cardRadius = 18.dp,
            border = ClayBorder(1.dp, Color(0x598FB8FF)),
            shadow = ClayShadow.Soft(18.dp, Color(0x388FB8FF)),
            titleFont = Orbitron,
            dayFont = Orbitron,
            titleScale = 0.9f,
            pattern = ThemePattern.Stars,
        ),
        shadow = Color(0x388FB8FF),
    )
    FamiliaThemeId.Winter -> themed(
        Palette(0xFFEAF2F8, 0xFFFFFFFF, 0xFFD6E6F2, 0xFFDDEBF5, 0xFFF6DADA, 0xFFB3262E, 0xFFFFFFFF, 0xFF1E5B7A, 0xFF13293D, 0xFF3E566B, 0xFFB3262E),
        ClayStyle(
            cardRadius = 24.dp,
            shadow = ClayShadow.Soft(8.dp, Color(0x1F13293D)),
            pattern = ThemePattern.Snow,
        ),
        shadow = Color(0x1F13293D),
    )
    FamiliaThemeId.Spain -> themed(
        // The colors of the flag (DESIGN, 16/11): red #AA151B and yellow #F1BF00, contrasts over 4.5:1.
        Palette(0xFFFFF8E6, 0xFFFFFFFF, 0xFFF1BF00, 0xFFFDEDB3, 0xFFF8D9D6, 0xFFAA151B, 0xFFFFFFFF, 0xFF8A1015, 0xFF2A1A12, 0xFF5C3A2E, 0xFFAA151B),
        ClayStyle(
            cardRadius = 18.dp,
            border = ClayBorder(2.dp, Color(0xFFAA151B)),
            shadow = ClayShadow.Hard(3.dp, Color(0xFFF1BF00), emphasisOffset = 4.dp),
            titleFont = AbrilFatface,
            dayFont = AbrilFatface,
            pattern = ThemePattern.FlagBand,
        ),
    )
    FamiliaThemeId.HighContrast -> themed(
        Palette(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFE08A, 0xFFE6F0FF, 0xFFFFF0D6, 0xFF7A2A00, 0xFFFFFFFF, 0xFF003C8F, 0xFF000000, 0xFF1A1A1A, 0xFF000000),
        ClayStyle(
            cardRadius = 16.dp,
            border = ClayBorder(2.dp, Color(0xFF1A1A1A)),
            shadow = ClayShadow.None,
        ),
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
