package fr.familia.app.core.theme

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.familia.app.R

/*
 * Style Argile: Fredoka for titles, times, buttons and tabs, Nunito for text. They are embedded
 * with Fraunces and Atkinson Hyperlegible (SIL Open Font License, see assets/licences), so every
 * phone shows the same.
 */
// Font variation settings are still marked experimental in Compose, but are stable since API 26.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

internal val Fredoka = FontFamily(
    variable(R.font.fredoka, FontWeight.Normal),
    variable(R.font.fredoka, FontWeight.Medium),
    variable(R.font.fredoka, FontWeight.SemiBold),
    variable(R.font.fredoka, FontWeight.Bold),
)

internal val Nunito = FontFamily(
    variable(R.font.nunito, FontWeight.Normal),
    variable(R.font.nunito, FontWeight.Medium),
    variable(R.font.nunito, FontWeight.SemiBold),
    variable(R.font.nunito, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.sized(size: Int, lineHeight: Int, weight: FontWeight? = null, family: FontFamily = Fredoka) =
    copy(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight ?: fontWeight, fontFamily = family)

/**
 * Material 3 type scale raised one step for the grandparents (design principles, "Typographie").
 * Sizes are in sp so they follow the phone's font size setting. No style goes below 14 sp.
 * Fredoka for titles and labels, Nunito for body text (Style Argile).
 */
internal val FamiliaTypography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontFamily = Fredoka),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Fredoka),
    headlineMedium = Base.headlineMedium.sized(28, 36, FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Fredoka),
    titleLarge = Base.titleLarge.sized(22, 28, FontWeight.SemiBold),
    titleMedium = Base.titleMedium.sized(18, 24, FontWeight.Medium),
    titleSmall = Base.titleSmall.sized(16, 22, FontWeight.Medium),
    bodyLarge = Base.bodyLarge.sized(18, 26, family = Nunito),
    bodyMedium = Base.bodyMedium.sized(16, 24, family = Nunito),
    bodySmall = Base.bodySmall.sized(14, 20, family = Nunito),
    labelLarge = Base.labelLarge.sized(16, 20, FontWeight.Medium),
    labelMedium = Base.labelMedium.sized(14, 20, FontWeight.Medium),
    labelSmall = Base.labelSmall.sized(14, 18, FontWeight.Medium),
)

internal val Fraunces = FontFamily(variable(R.font.fraunces, FontWeight.SemiBold), variable(R.font.fraunces, FontWeight.Bold))

internal val AtkinsonHyperlegible = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

/**
 * « Police des titres » (DESIGN-45, Kévin 18/11), chosen apart from the theme and kept on the phone
 * under « titleFont »: titles of screens and cards, and the day headers.
 */
enum class TitleFont(val key: String, @StringRes val labelRes: Int, internal val family: FontFamily, internal val weight: FontWeight? = null) {
    Round("fredoka", R.string.appearance_title_font_round, Fredoka),
    Elegant("fraunces", R.string.appearance_title_font_elegant, Fraunces),
    Sober("nunito", R.string.appearance_title_font_sober, Nunito, FontWeight.Bold),
    ;

    companion object {
        fun fromKey(key: String?): TitleFont = entries.firstOrNull { it.key == key } ?: Round
    }
}

/**
 * « Police du texte » (DESIGN-46, Kévin 18/11), kept on the phone under « bodyFont »: Nunito, or
 * Atkinson Hyperlegible for the poor eyes (I, l and 1 never look alike). Sizes do not change.
 */
enum class BodyFont(val key: String, @StringRes val labelRes: Int, internal val family: FontFamily) {
    Normal("nunito", R.string.appearance_body_font_normal, Nunito),
    Legible("atkinson", R.string.appearance_body_font_legible, AtkinsonHyperlegible),
    ;

    companion object {
        fun fromKey(key: String?): BodyFont = entries.firstOrNull { it.key == key } ?: Normal
    }
}

/**
 * The type scale for the chosen fonts. The defaults (Ronde, Normale) give [FamiliaTypography]
 * unchanged. The title font goes on the headlines and titleLarge; Très lisible goes on all the
 * running text: body, labels (buttons, fields, tabs) and the small titles.
 */
internal fun familiaTypography(title: TitleFont = TitleFont.Round, body: BodyFont = BodyFont.Normal): Typography {
    var t = FamiliaTypography
    if (title != TitleFont.Round) {
        fun TextStyle.titled() = copy(fontFamily = title.family, fontWeight = title.weight ?: fontWeight)
        t = t.copy(
            displaySmall = t.displaySmall.titled(),
            headlineLarge = t.headlineLarge.titled(),
            headlineMedium = t.headlineMedium.titled(),
            headlineSmall = t.headlineSmall.titled(),
            titleLarge = t.titleLarge.titled(),
        )
    }
    if (body != BodyFont.Normal) {
        fun TextStyle.running() = copy(fontFamily = body.family)
        t = t.copy(
            titleMedium = t.titleMedium.running(),
            titleSmall = t.titleSmall.running(),
            bodyLarge = t.bodyLarge.running(),
            bodyMedium = t.bodyMedium.running(),
            bodySmall = t.bodySmall.running(),
            labelLarge = t.labelLarge.running(),
            labelMedium = t.labelMedium.running(),
            labelSmall = t.labelSmall.running(),
        )
    }
    return t
}

/** The day headers of the planning, in the title font. */
internal fun dayHeaderStyle(title: TitleFont, base: TextStyle): TextStyle =
    if (title == TitleFont.Round) base else base.copy(fontFamily = title.family, fontWeight = title.weight ?: base.fontWeight)

/** The name of a title font, written in it, at the size of a card title. */
val TitleFont.sample: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleLarge.copy(fontFamily = family, fontWeight = weight ?: FontWeight.SemiBold)

/** The name of a text font, written in it, at the size of the running text. */
val BodyFont.sample: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(fontFamily = family)
