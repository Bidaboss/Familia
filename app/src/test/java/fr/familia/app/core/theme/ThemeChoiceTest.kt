package fr.familia.app.core.theme

import fr.familia.app.feature.settings.appearance.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ThemeChoiceTest {

    private fun on(day: String) = seasonTheme(LocalDate.parse(day))

    @Test
    fun `the season changes on the 21 December, 20 March, 21 June and 23 September`() {
        assertEquals(FamiliaThemeId.Autumn, on("2026-12-20"))
        assertEquals(FamiliaThemeId.Clouds, on("2026-12-21"))
        assertEquals(FamiliaThemeId.Clouds, on("2027-01-01"))
        assertEquals(FamiliaThemeId.Clouds, on("2027-03-19"))
        assertEquals(FamiliaThemeId.Lake, on("2027-03-20"))
        assertEquals(FamiliaThemeId.Lake, on("2027-06-20"))
        assertEquals(FamiliaThemeId.Coast, on("2027-06-21"))
        assertEquals(FamiliaThemeId.Coast, on("2027-09-22"))
        assertEquals(FamiliaThemeId.Autumn, on("2027-09-23"))
        assertEquals(FamiliaThemeId.Clouds, on("2028-02-29"))
    }

    @Test
    fun `Selon la saison first and by default, then the 8 photos`() {
        assertEquals(ThemeChoice.Season, ThemeChoice.all.first())
        assertEquals(9, ThemeChoice.all.size)
        assertEquals(ThemeChoice.Season, ThemeChoice.fromKey(null))
        assertEquals(ThemeChoice.Photo(FamiliaThemeId.Lavender), ThemeChoice.fromKey("u_lavande"))
        assertEquals(FamiliaThemeId.Lavender, ThemeChoice.fromKey("u_lavande").on(LocalDate.of(2026, 1, 1)))
        ThemeChoice.all.forEach { assertEquals(it, ThemeChoice.fromKey(it.key)) }
    }

    @Test
    fun `every color theme of before goes to Selon la saison`() {
        listOf("classic", "garden", "spain", "winter", "lavender", "night", "contrast", "arcade", "cosmos", "retro", "travel").forEach {
            assertEquals(it, ThemeChoice.Season, ThemeChoice.fromKey(it))
        }
    }

    @Test
    fun `a phone on Contraste fort keeps it, now apart from the theme`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("familia", 0).edit().putString("theme", "contrast").commit()
        val prefs = ThemePreferences(context)
        assertEquals(ThemeChoice.Season, prefs.theme)
        assertTrue(prefs.highContrast)
        prefs.theme = ThemeChoice.Photo(FamiliaThemeId.Alps)
        assertTrue(prefs.highContrast)
        prefs.highContrast = false
        assertFalse(prefs.highContrast)
        assertEquals(ThemeChoice.Photo(FamiliaThemeId.Alps), prefs.theme)
    }

    @Test
    fun `any other phone starts without Contraste fort`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("familia", 0).edit().putString("theme", "spain").commit()
        assertFalse(ThemePreferences(context).highContrast)
    }

    @Test
    fun `the extent of the photo is the whole screen at first, and kept once chosen`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = ThemePreferences(context)
        assertEquals(PhotoExtent.Full, prefs.photoExtent)
        prefs.photoExtent = PhotoExtent.Top
        assertEquals(PhotoExtent.Top, ThemePreferences(context).photoExtent)
    }

    @Test
    fun `every photo theme has its author, its Unsplash page and its picture, the others none`() {
        FamiliaThemeId.photos.forEach { theme ->
            val credit = ThemePhotos.credit(theme)
            assertTrue(theme.key, credit != null && credit.author.isNotBlank() && credit.page.startsWith("https://unsplash.com/photos/"))
            assertEquals("${credit!!.page}/download?force=true&w=1080", ThemePhotos.url(theme))
        }
        assertEquals(8, FamiliaThemeId.photos.map { ThemePhotos.credit(it)!!.id }.distinct().size)
        assertTrue(FamiliaThemeId.entries.filterNot { it.isPhoto }.none { ThemePhotos.url(it) != null })
    }
}
