package fr.familia.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.familia.app.R

/*
 * Style Argile: Fredoka for titles, times, buttons and tabs, Nunito for text. Both are embedded
 * (variable fonts, SIL Open Font License, see assets/licences), so every phone shows the same.
 */
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
