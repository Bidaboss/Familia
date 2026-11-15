package fr.familia.app.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `nine themes, Classique first and by default`() {
        assertEquals(9, FamiliaThemeId.entries.size)
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.entries.first())
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey(null))
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey("unknown"))
        assertEquals(FamiliaThemeId.Spain, FamiliaThemeId.fromKey("spain"))
        assertEquals(9, FamiliaThemeId.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `Classique is the Argile theme of before, and the only one with a dark mode`() {
        val classic = themeSpec(FamiliaThemeId.Classic)
        assertEquals(Color(0xFFB5541C), classic.light.primary)
        assertEquals(LightClayColors, classic.lightClay)
        assertEquals(ClayStyle(), classic.style)
        assertNotNull(classic.dark)
        FamiliaThemeId.entries.drop(1).forEach { assertNull(it.name, themeSpec(it).dark) }
    }

    @Test
    fun `every text keeps at least 4,5 to 1 on its background`() {
        FamiliaThemeId.entries.forEach { id ->
            val spec = themeSpec(id)
            val c = spec.light
            val clay = spec.lightClay
            val pairs = listOf(
                "text/background" to contrast(c.onSurface, c.background),
                "text/card" to contrast(c.onSurface, clay.card),
                "secondary text/card" to contrast(clay.onPlainVariant, clay.card),
                "secondary text/household" to contrast(clay.onHouseholdVariant, clay.household),
                "secondary text/family" to contrast(clay.onFamilyVariant, clay.family),
                "button" to contrast(c.onPrimary, c.primary),
            )
            pairs.forEach { (what, ratio) -> assertTrue("$id $what: $ratio", ratio >= 4.5) }
        }
    }

    @Test
    fun `a themed title keeps Nunito for the body text`() {
        val spain = familiaTypography(themeSpec(FamiliaThemeId.Spain).style)
        assertEquals(FamiliaTypography.bodyLarge, spain.bodyLarge)
        assertTrue(spain.titleLarge.fontFamily != FamiliaTypography.titleLarge.fontFamily)
        assertEquals(FamiliaTypography, familiaTypography(ClayStyle()))
    }
}
