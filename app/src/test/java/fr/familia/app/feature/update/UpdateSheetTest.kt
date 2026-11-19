package fr.familia.app.feature.update

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sheet of a new version (DESIGN-47, F), once per version, then the dark card (D). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xxhdpi")
class UpdateSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val update = AppUpdate("0.2.420", "https://drive.google.com/drive/folders/abc")

    @Test
    fun `the sheet shows once per version`() {
        assertTrue(shouldShowUpdateSheet(update, seenVersion = null))
        assertTrue(shouldShowUpdateSheet(update, seenVersion = "0.2.411"))
        assertFalse(shouldShowUpdateSheet(update, seenVersion = "0.2.420"))
        assertFalse(shouldShowUpdateSheet(null, seenVersion = null))
    }

    @Test
    fun `« Mettre à jour maintenant » and « Plus tard » answer`() {
        val done = mutableListOf<String>()
        compose.setContent { FamiliaTheme { UpdateSheetContent(onUpdate = { done += "update" }, onLater = { done += "later" }) } }
        compose.onNodeWithText("Une nouvelle version est prête").assertExists()
        compose.onNodeWithText("Mettre à jour maintenant").performScrollTo().performClick()
        compose.onNodeWithText("Plus tard").performScrollTo().performClick()
        assertEquals(listOf("update", "later"), done)
    }

    @Test
    fun `the dark card answers too`() {
        val done = mutableListOf<String>()
        compose.setContent { FamiliaTheme { UpdateBanner(onUpdate = { done += "update" }, onLater = { done += "later" }) } }
        compose.onNodeWithText("NOUVEAU").assertExists()
        compose.onNodeWithText("Mettre à jour").performClick()
        compose.onNodeWithText("Plus tard").performClick()
        assertEquals(listOf("update", "later"), done)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `at 130 percent the sheet and the card have big enough named targets`() = compose.checkAccessibility {
        FamiliaTheme {
            androidx.compose.foundation.layout.Column {
                UpdateSheetContent({}, {})
                UpdateBanner({}, {})
            }
        }
    }
}
