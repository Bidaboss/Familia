package fr.familia.app.feature.members

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The Famille tab « par foyer » (récit 64): its sections in order, folding, and TalkBack. Fictional family. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class FamilyByHouseholdScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)

    private fun stateOf(meId: String) = MembersUiState.Success(previewGroups(), meId)

    private fun hasState(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `my card, my household, then the other households folded, and a touch unfolds one`() {
        compose.setContent {
            FamiliaTheme {
                MembersScreen(stateOf("a"), onRetry = {}, onOpenProfile = {}, byHousehold = true, today = today)
            }
        }
        compose.onNodeWithText("Voir et modifier ma fiche").assertIsDisplayed()
        compose.onNodeWithText("Foyer Martin").assertIsDisplayed()
        compose.onNodeWithText("Sacha").assertIsDisplayed()
        compose.onNodeWithText("Les autres foyers").assertIsDisplayed()
        // Folded: TalkBack hears the household, « replié » and how many are in it; no member row yet.
        val bernard = hasContentDescription("Foyer Bernard")
        compose.onNode(hasScrollAction()).performScrollToNode(bernard)
        compose.onNode(bernard.and(hasState("replié, 2 membres"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Foyer Bernard").performClick()
        compose.onNode(bernard.and(hasState("déplié"))).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText("Inès"))
        compose.onNodeWithText("Inès").assertIsDisplayed()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the text size, everything is read and tall enough`() = compose.checkAccessibility {
        MembersScreen(stateOf("a"), onRetry = {}, onOpenProfile = {}, byHousehold = true, today = today)
    }
}
