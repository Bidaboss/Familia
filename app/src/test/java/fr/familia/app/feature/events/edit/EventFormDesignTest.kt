package fr.familia.app.feature.events.edit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.quickDays
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The event form in tiles (récit 77, DESIGN-50). Fictional first names only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi")
class EventFormDesignTest {

    @get:Rule
    val compose = createComposeRule()

    private var form = previewEventState().form

    private fun show(state: EventEditUiState.Editing) = compose.setContent {
        FamiliaTheme {
            EventEditScreen(state, onChange = { change -> form = change(form) }, onSave = {}, onDelete = {}, onBack = {})
        }
    }

    @Test
    fun `a new event says « Ajouter au planning » and has no « Supprimer »`() {
        show(previewEventState(isNew = true))
        compose.onAllNodesWithText("Ajouter au planning").assertCountEquals(1)
        compose.onAllNodesWithText("Supprimer l'événement").assertCountEquals(0)
    }

    @Test
    fun `an event edited says « Enregistrer », with « Supprimer » at the bottom`() {
        show(previewEventState(isNew = false))
        compose.onAllNodesWithText("Enregistrer").assertCountEquals(1)
        compose.onNodeWithText("Supprimer l'événement").performScrollTo()
        compose.onAllNodesWithText("Supprimer l'événement").assertCountEquals(1)
    }

    @Test
    fun `« Demain » picks the day after today`() {
        show(previewEventState(isNew = true))
        compose.onNodeWithText("Demain").performClick()
        assertEquals(familyToday().plusDays(1), form.date)
    }

    @Test
    fun `the place and the target are pills and rounds, touched once`() {
        show(previewEventState(isNew = true))
        compose.onNodeWithText("Une adresse").performScrollTo().performClick()
        assertEquals(PlaceKind.Address, form.placeKind)
        compose.onNodeWithText("Noé").performScrollTo().performClick()
        assertEquals(EventTarget.ForChild("c"), form.target)
    }

    @Test
    fun `the quick days are today and the 3 after it`() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(listOf(20, 21, 22, 23), quickDays(today).map { it.dayOfMonth })
    }

    @Test
    fun `several rounds can be ticked when the row is for several people`() {
        val ticked = mutableListOf<String>()
        compose.setContent {
            FamiliaTheme {
                PeopleRow(
                    choices = listOf(PersonChoice("lou", "Lou", false, memberId = "lou"), PersonChoice("sam", "Sam", true, memberId = "sam")),
                    multiple = true,
                    onToggle = { ticked += it },
                )
            }
        }
        compose.onNodeWithText("Lou").performClick()
        compose.onNodeWithText("Sam").performClick()
        assertEquals(listOf("lou", "sam"), ticked)
    }
}
