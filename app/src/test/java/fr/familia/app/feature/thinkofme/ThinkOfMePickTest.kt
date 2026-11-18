package fr.familia.app.feature.thinkofme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * « Penser à quelqu'un » (PRODUIT-135): « Pensez à moi » left the member's page for the « + » of
 * the Accueil. Me first, to note my own moments, then everyone else by household. Fictional family.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xxhdpi", fontScale = 2f)
class ThinkOfMePickTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `me first, then the others by household, never me twice`() {
        val state = thinkOfMePickState(TestFamily.family, "adult1")
        assertEquals("adult1", state.me?.id)
        val ids = state.groups.flatMap { group -> group.members.map { it.id } }
        assertTrue("adult1" !in ids)
        assertEquals(setOf("admin1", "kid1", "adult2", "kid2"), ids.toSet())
        // By household, as in the Famille tab: never two groups for the same household.
        assertEquals(state.groups.size, state.groups.map { it.name }.toSet().size)
    }

    @Test
    fun `touching Moi opens my own « Pensez à moi »`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            FamiliaTheme { ThinkOfMePickScreen(thinkOfMePickState(TestFamily.family, "adult1"), onBack = {}, onPick = { picked += it }) }
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("Moi").fetchSemanticsNodes().size)
        compose.onNodeWithText("Moi").performClick()
        compose.onNodeWithText("Kid2").performClick()
        assertEquals(listOf("adult1", "kid2"), picked)
    }

    @Test
    fun `at 200 percent every row is big enough and named`() = compose.checkAccessibility {
        ThinkOfMePickScreen(thinkOfMePickState(TestFamily.family, "adult1"), onBack = {}, onPick = {})
    }
}
