package fr.familia.app.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
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
    fun `seven themes, Argile first and by default`() {
        assertEquals(7, FamiliaThemeId.entries.size)
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.entries.first())
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey(null))
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey("unknown"))
        assertEquals(FamiliaThemeId.Spain, FamiliaThemeId.fromKey("spain"))
        assertEquals(7, FamiliaThemeId.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `a phone set on a removed theme goes to the closest one`() {
        assertEquals(FamiliaThemeId.Night, FamiliaThemeId.fromKey("arcade"))
        assertEquals(FamiliaThemeId.Night, FamiliaThemeId.fromKey("cosmos"))
        assertEquals(FamiliaThemeId.Olive, FamiliaThemeId.fromKey("garden"))
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey("retro"))
        assertEquals(FamiliaThemeId.Classic, FamiliaThemeId.fromKey("travel"))
    }

    @Test
    fun `a theme changes the mood only, with the same corners everywhere but Argile`() {
        FamiliaThemeId.entries.drop(1).forEach { id ->
            val style = themeSpec(id).style
            assertEquals(id.name, 22.dp, style.cardRadius)
            assertTrue(id.name, style.border == null || style.border?.dashed == false)
        }
        assertEquals(Color(0xFFC9DBA8), themeSpec(FamiliaThemeId.Olive).style.tabPill)
        assertEquals(Color(0xFF1E1511), themeSpec(FamiliaThemeId.Night).light.background)
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
                // Where the tint is a stripe or a dot (Nuit, the light themes), the card keeps its color.
                "secondary text/household" to contrast(clay.onHouseholdVariant, clay.cardBackground(ClayTint.Household)),
                "secondary text/family" to contrast(clay.onFamilyVariant, clay.cardBackground(ClayTint.Family)),
                "button" to contrast(c.onPrimary, c.primary),
                // Large texts (3:1 would do), held to 4,5:1 like the rest.
                "title bar" to contrast(spec.style.titleColor ?: c.onSurface, c.background),
                "day headers" to contrast(spec.style.dayColor ?: c.primary, c.background),
            )
            pairs.forEach { (what, ratio) -> assertTrue("$id $what: $ratio", ratio >= 4.5) }
        }
    }

    @Test
    fun `the fonts are chosen apart, and the defaults change nothing`() {
        assertEquals(FamiliaTypography, familiaTypography())
        assertEquals(TitleFont.Round, TitleFont.fromKey(null))
        assertEquals(BodyFont.Normal, BodyFont.fromKey("unknown"))
        assertEquals(BodyFont.Legible, BodyFont.fromKey("atkinson"))
        val elegant = familiaTypography(TitleFont.Elegant)
        assertEquals(FamiliaTypography.bodyLarge, elegant.bodyLarge)
        assertTrue(elegant.titleLarge.fontFamily != FamiliaTypography.titleLarge.fontFamily)
        val legible = familiaTypography(body = BodyFont.Legible)
        assertEquals(FamiliaTypography.titleLarge, legible.titleLarge)
        assertEquals(AtkinsonHyperlegible, legible.bodyLarge.fontFamily)
        assertEquals(AtkinsonHyperlegible, legible.labelLarge.fontFamily)
        assertEquals(FamiliaTypography.bodyLarge.fontSize, legible.bodyLarge.fontSize)
    }
}
