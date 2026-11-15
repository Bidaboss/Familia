package fr.familia.app.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Who a card is for (design principles, « Style Argile »). The tint always doubles a « Pour … »
 * text, never carries the information alone.
 */
enum class ClayTint { Plain, Household, Family, Birthday }

/** The colors of the Argile style that Material's color scheme has no role for. */
@Immutable
data class ClayColors(
    val card: Color,
    val household: Color,
    val family: Color,
    val birthday: Color,
    /** Secondary text on a plain or birthday card, on a household card, on a family card. */
    val onPlainVariant: Color,
    val onHouseholdVariant: Color,
    val onFamilyVariant: Color,
    /** Day headers of the planning. */
    val dayTitle: Color,
    /** Colored outer shadow (Android 9 and later; grey before). */
    val shadow: Color,
    /** Highlight top left and shade bottom right, drawn inside the shape. */
    val highlight: Color,
    val shade: Color,
    /**
     * In dark mode the card stays [card] and the tint becomes a 4 dp stripe on the left, so
     * light text keeps its contrast.
     */
    val tintAsStripe: Boolean,
) {
    fun tint(tint: ClayTint): Color = when (tint) {
        ClayTint.Plain -> card
        ClayTint.Household -> household
        ClayTint.Family -> family
        ClayTint.Birthday -> birthday
    }

    fun onTintVariant(tint: ClayTint): Color = when (tint) {
        ClayTint.Household -> onHouseholdVariant
        ClayTint.Family -> onFamilyVariant
        ClayTint.Plain, ClayTint.Birthday -> onPlainVariant
    }
}

// Contrast checked by the DESIGN thread: every text 6.3:1 to 13.3:1 on its background.
internal val LightClayColors = ClayColors(
    card = Color(0xFFFFFFFF),
    household = Color(0xFFE3EEFF),
    family = Color(0xFFFFF1C9),
    birthday = Color(0xFFFFE2D3),
    onPlainVariant = Color(0xFF5A463D),
    onHouseholdVariant = Color(0xFF3E4A63),
    onFamilyVariant = Color(0xFF5C4A1E),
    dayTitle = Color(0xFF8A3A10),
    shadow = Color(0x26B5541C),
    highlight = Color(0x99FFFFFF),
    shade = Color(0x0F5A2A10),
    tintAsStripe = false,
)

// Dark: text #EBDCD3 on cards #2B201A (11.9:1).
internal val DarkClayColors = ClayColors(
    card = Color(0xFF2B201A),
    household = Color(0xFF8FB2F0),
    family = Color(0xFFE9C969),
    birthday = Color(0xFFF2A585),
    onPlainVariant = Color(0xFFD8C2BA),
    onHouseholdVariant = Color(0xFFD8C2BA),
    onFamilyVariant = Color(0xFFD8C2BA),
    dayTitle = Color(0xFFFFB693),
    shadow = Color(0x66000000),
    highlight = Color(0x14FFFFFF),
    shade = Color(0x1F000000),
    tintAsStripe = true,
)

val LocalClayColors = staticCompositionLocalOf { LightClayColors }
