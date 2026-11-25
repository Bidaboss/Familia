package fr.familia.app.feature.home

import fr.familia.app.core.theme.FamiliaThemeId
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoColorsTest {

    @Test
    fun `the filled buttons and pills of every photo theme keep a white text readable`() {
        FamiliaThemeId.entries.filter { it.isPhoto }.forEach { theme ->
            val ratio = contrastWithWhite(PhotoColors.of(theme).filled)
            assertTrue("${theme.key}: $ratio", ratio >= 4.5)
        }
    }
}
