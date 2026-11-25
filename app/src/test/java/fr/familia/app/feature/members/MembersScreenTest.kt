package fr.familia.app.feature.members

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Famille > Foyers | Arbre (brief Famille-recettes F1). Fictional family of [previewGroups]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class MembersScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)

    private fun stateOf(meId: String) = MembersUiState.Success(previewGroups(), meId, ownerId = "a")

    @Test
    fun `the count, my household first, every household unfolded, and the rows' lines`() {
        compose.setContent {
            FamiliaTheme { MembersScreen(stateOf("d"), onRetry = {}, onOpenProfile = {}, today = today) }
        }
        compose.onNodeWithText("6 personnes · 2 foyers").assertIsDisplayed()
        compose.onNodeWithText("Mon foyer").assertIsDisplayed()
        // My own birthday is in 5 days; Lou is a child of 12.
        compose.onNodeWithText("🎂 Dans 5 jours").assertIsDisplayed()
        compose.onNodeWithText("Enfant · 12 ans").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Foyer Martin"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Administrateur principal"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Enfant sans l'appli · 5 ans"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Autres"))
        compose.onNodeWithText("Jules").assertIsDisplayed()
    }

    @Test
    fun `Appeler and Message on the other adults with a phone, never on me, and a row opens the page`() {
        var opened: String? = null
        compose.setContent {
            FamiliaTheme { MembersScreen(stateOf("a"), onRetry = {}, onOpenProfile = { opened = it }, today = today) }
        }
        compose.onNodeWithContentDescription("Appeler Sacha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Écrire un message à Sacha").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Appeler Camille")).fetchSemanticsNodes().size)
        compose.onNodeWithText("Noé").performClick()
        assertEquals("c", opened)
    }

    @Test
    fun `the Arbre tab shows the tree in place of the households`() {
        compose.setContent {
            FamiliaTheme { MembersScreen(stateOf("a"), onRetry = {}, tree = { Text("le dessin") }, today = today) }
        }
        explained {
            compose.onNodeWithText("Foyers").assertIsSelected()
            compose.onNodeWithText("Arbre").assertIsNotSelected().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Arbre").assertIsSelected()
            compose.onNodeWithText("le dessin").assertExists()
            assertEquals(0, compose.onAllNodes(hasText("Mon foyer")).fetchSemanticsNodes().size)
        }
    }

    /** On a failure, the whole semantics tree in the message, to see what the screen held. */
    private fun explained(block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError(e.message + "\n" + compose.onRoot(useUnmergedTree = true).printToString(), e)
        }
    }

    @Test
    fun `without the module Arbre, no tabs`() {
        compose.setContent {
            FamiliaTheme { MembersScreen(stateOf("a"), onRetry = {}, today = today) }
        }
        assertEquals(0, compose.onAllNodes(hasText("Arbre")).fetchSemanticsNodes().size)
    }

    @Test
    fun `Gerer shows for the admin principal only`() {
        var managed = false
        compose.setContent {
            FamiliaTheme {
                MembersScreen(stateOf("a").copy(isOwner = true), onRetry = {}, onManage = { managed = true }, today = today)
            }
        }
        compose.onNodeWithContentDescription("Gérer la famille").performClick()
        assertEquals(true, managed)
    }

    @Test
    fun `a member who is not the admin principal has no Gerer`() {
        compose.setContent {
            FamiliaTheme { MembersScreen(stateOf("b"), onRetry = {}, onManage = {}, today = today) }
        }
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Gérer la famille")).fetchSemanticsNodes().size)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the text size, everything is read and tall enough`() = compose.checkAccessibility {
        MembersScreen(stateOf("a").copy(isOwner = true), onRetry = {}, onOpenProfile = {}, tree = {}, onManage = {}, today = today)
    }
}
