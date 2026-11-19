package fr.familia.app.feature.planning

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.polls.PollsSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « + Ajouter » at the bottom of the Planning, and the card « Sondages » at its top (récit 75). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xxhdpi")
class PlanningAddTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()

    private fun show(pollsOn: Boolean = true, polls: PollsSummary = PollsSummary(open = 2, toVote = 1)) = compose.setContent {
        FamiliaTheme {
            PlanningScreen(
                previewPlanning(), {}, null, {},
                onAddEvent = { done += "event" },
                onAddPoll = if (pollsOn) ({ done += "poll" }) else null,
                onOpenPolls = if (pollsOn) ({ done += "polls" }) else null,
                polls = polls,
            )
        }
    }

    @Test
    fun `« Ajouter » opens the choice of an event or a poll`() {
        show()
        compose.onNodeWithText("Ajouter", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Un sondage").assertCountEquals(1)
        compose.onAllNodesWithText("Un événement").assertCountEquals(1)
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun `the choice adds an event or a poll`() {
        compose.setContent {
            FamiliaTheme { PlanningAddChoices(onEvent = { done += "event" }, onPoll = { done += "poll" }) }
        }
        compose.onNodeWithText("Un sondage").performClick()
        compose.onNodeWithText("Un événement").performClick()
        assertEquals(listOf("poll", "event"), done)
    }

    @Test
    fun `without polls « Ajouter » opens an event at once, and there is no card`() {
        show(pollsOn = false)
        compose.onNodeWithText("Ajouter", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("Un sondage").assertCountEquals(0)
        compose.onAllNodesWithText("Sondages").assertCountEquals(0)
        assertEquals(listOf("event"), done)
    }

    @Test
    fun `the card « Sondages » says what waits for me and opens the polls`() {
        show()
        compose.onNodeWithText("1 sondage attend ta réponse").performClick()
        assertEquals(listOf("polls"), done)
    }

    @Test
    fun `no poll open, no card`() {
        show(polls = PollsSummary())
        compose.onAllNodesWithText("Sondages").assertCountEquals(0)
    }
}
