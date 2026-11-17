package fr.familia.app.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.ui.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contrast pass (avant le 21), on the colors of every theme, Classique dark included: the pairs
 * of text and background the screens use. Texts 4.5:1; large titles, day headers and the outline
 * of fields 3:1. The drawn screens are measured too (VoyageScreensContrastTest).
 */
class ThemeContrastTest {

    private class Pairs(val name: String) {
        val low = mutableListOf<String>()
        fun need(what: String, front: Color, back: Color, min: Float = 4.5f) {
            val ratio = contrast(front, back)
            if (ratio < min) low += "$name $what: ${"%.2f".format(ratio)}:1 (il faut $min)"
        }
    }

    private fun check(name: String, c: ColorScheme, clay: ClayColors, style: ClayStyle, strongTints: Boolean): List<String> {
        val p = Pairs(name)
        for ((surfaceName, surface) in listOf("fond" to c.background, "carte" to clay.card)) {
            p.need("texte/$surfaceName", c.onSurface, surface)
            p.need("texte secondaire/$surfaceName", c.onSurfaceVariant, surface)
            p.need("texte secondaire Argile/$surfaceName", clay.onPlainVariant, surface)
            p.need("lien et bouton texte/$surfaceName", c.primary, surface)
            p.need("erreur/$surfaceName", c.error, surface)
            p.need("contour des champs/$surfaceName", c.outline, surface, 3f)
            p.need("titres/$surfaceName", style.titleColor ?: c.onSurface, surface, 3f)
            p.need("en-têtes de jour/$surfaceName", style.dayColor ?: clay.dayTitle, surface, 3f)
        }
        p.need("bouton plein", c.onPrimary, c.primary)
        p.need("bouton tonal", c.onSecondaryContainer, c.secondaryContainer)
        p.need("pastille", c.onPrimaryContainer, c.primaryContainer)
        p.need("bandeau tertiaire", c.onTertiaryContainer, c.tertiaryContainer)
        p.need("bouton d'erreur", c.onError, c.error)
        p.need("bandeau d'erreur", c.onErrorContainer, c.errorContainer)
        p.need("texte/variante", c.onSurfaceVariant, c.surfaceVariant)
        if (strongTints) {
            for (tint in listOf(ClayTint.Household, ClayTint.Family, ClayTint.Birthday)) {
                p.need("texte/carte $tint", c.onSurface, clay.cardBackground(tint))
                p.need("texte secondaire/carte $tint", clay.onTintVariant(tint), clay.cardBackground(tint))
            }
        }
        return p.low
    }

    @Test
    fun `every theme, and Classique in dark mode, keeps its texts readable`() {
        val low = FamiliaThemeId.entries.flatMap { id ->
            val spec = themeSpec(id)
            val light = check(id.key, spec.light, spec.lightClay, spec.style, strongTints = !spec.lightClay.tintAsDot && !spec.lightClay.tintAsStripe)
            val dark = if (spec.dark != null) {
                val clay = spec.darkClay ?: spec.lightClay
                check("${id.key} sombre", spec.dark, clay, spec.style, strongTints = !clay.tintAsDot && !clay.tintAsStripe)
            } else {
                emptyList()
            }
            light + dark
        }
        assertTrue("contraste trop faible :\n" + low.joinToString("\n"), low.isEmpty())
    }
}
