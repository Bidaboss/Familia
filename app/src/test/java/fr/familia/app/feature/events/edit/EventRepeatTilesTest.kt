package fr.familia.app.feature.events.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.model.RepeatEnd
import fr.familia.app.core.theme.FamiliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** « Se répète » and « Se termine » in pills and tiles (refonte, DESIGN-57): no radio button left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi")
class EventRepeatTilesTest {

    @get:Rule
    val compose = createComposeRule()

    private var form by mutableStateOf(EventForm(date = LocalDate.of(2026, 12, 2), repeat = RepeatChoice.Weekly))

    private fun show(endInvalid: Boolean = false) = compose.setContent {
        FamiliaTheme {
            RepeatSection(form, isOccurrence = false, endInvalid = endInvalid, onChange = { change -> form = change(form) })
        }
    }

    @Test
    fun `« Se termine » is three pills, touched once`() {
        show(endInvalid = true)
        compose.onNodeWithText("Après un nombre de fois").performClick()
        assertEquals(RepeatEnd.After(10), form.end)
        compose.onNodeWithText("Après un nombre de fois").assertIsSelected()
        compose.onNodeWithText("Jamais").performClick()
        assertEquals(RepeatEnd.Never, form.end)
    }

    @Test
    fun `« Autre… » shows every rhythm as tiles, and one touch chooses and closes`() {
        show()
        compose.onNodeWithText("Autre…").performClick()
        val yearly = compose.onAllNodesWithText("Chaque année", substring = true)
        yearly.assertCountEquals(1)
        yearly[0].performClick()
        assertEquals(RepeatChoice.Yearly, form.repeat)
        compose.onAllNodesWithText("Chaque année", substring = true).assertCountEquals(1)
    }

    @Test
    fun `every choice still reads as a radio button for TalkBack`() {
        show(endInvalid = true)
        val radios = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        // « Une fois », « Chaque semaine », « Chaque mois », « Autre… », then the 3 of « Se termine ».
        radios.assertCountEquals(7)
    }
}
