package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A private idea of adult1's notebook, in the fictional family of TestFamily. */
class NotebookIdeaTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)

    private suspend fun NotebookIdeaViewModel.editing() =
        uiState.first { it is NotebookIdeaUiState.Editing } as NotebookIdeaUiState.Editing

    @Test
    fun `a new idea starts at the occasion shown, for anyone but me`() = runTest {
        val vm = NotebookIdeaViewModel(FakeNotebook(), FakeMembers(), "adult1", null, christmas)
        val state = vm.editing()
        assertEquals(GiftOccasion.Christmas, state.form.occasion)
        assertEquals("2026", state.form.year)
        assertTrue(state.people.none { it.id == "adult1" })
    }

    @Test
    fun `from a member's page, the idea stays for that member`() = runTest {
        val vm = NotebookIdeaViewModel(FakeNotebook(), FakeMembers(), "adult1", null, christmas, startPersonId = "adult2")
        val state = vm.editing()
        assertEquals("adult2", state.fixedPerson?.id)
        assertEquals("adult2", state.form.personId)
        vm.update { it.copy(personId = "admin1") }
        vm.update { it.copy(otherName = "Mamie Jo", personId = null) }
        assertEquals("adult2", vm.editing().form.personId)
        assertEquals("", vm.editing().form.otherName)
    }

    @Test
    fun `from the notebook, anyone may be chosen`() = runTest {
        val vm = NotebookIdeaViewModel(FakeNotebook(), FakeMembers(), "adult1", null, christmas)
        assertNull(vm.editing().fixedPerson)
    }

    @Test
    fun `a purchase made elsewhere starts as bought, and stays a private idea`() = runTest {
        val vm = NotebookIdeaViewModel(FakeNotebook(), FakeMembers(), "adult1", null, christmas, startBought = true)
        val state = vm.editing()
        assertEquals(GiftStatus.Bought, state.form.status)
        assertEquals(GiftOccasion.Christmas, state.form.occasion)
    }

    @Test
    fun `an idea needs someone, a title and a sensible year`() = runTest {
        val notebook = FakeNotebook()
        val vm = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", null)
        vm.editing()
        vm.update { it.copy(year = "26") }
        vm.save()
        val state = vm.editing()
        assertEquals(setOf(NotebookIdeaError.PersonMissing, NotebookIdeaError.BadYear), state.errors)
        assertEquals(setOf(GiftItemFormError.TitleMissing), state.itemErrors)
        assertTrue(notebook.entries.value.isEmpty())
    }

    @Test
    fun `an idea for someone outside the family is saved with their first name only`() = runTest {
        val notebook = FakeNotebook()
        val vm = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", null, christmas)
        vm.editing()
        vm.update { it.copy(otherName = "  Mamie Jo ", title = "Une écharpe", price = "25", status = GiftStatus.Bought) }
        vm.save()
        vm.uiState.first { it == NotebookIdeaUiState.Done }
        val saved = notebook.entries.value.single()
        assertNull(saved.personId)
        assertEquals("Mamie Jo", saved.personName)
        assertEquals(NotebookSource.Idea, saved.source)
        assertEquals(25.0, saved.price!!, 0.001)
        assertEquals(christmas, saved.occasion)
        assertEquals(setOf("adult1"), notebook.askedFor)
    }

    @Test
    fun `a reservation taken up is not edited here, and a draft comes back`() = runTest {
        val claim = NotebookEntry("gift_l_i", NotebookSource.Claim, "adult2", "Adult2", christmas, "Un livre", null, 15.0, null, GiftStatus.Idea)
        val notebook = FakeNotebook(listOf(claim))
        val vm = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", "gift_l_i")
        assertEquals(NotebookIdeaUiState.Unavailable, vm.uiState.first { it !is NotebookIdeaUiState.Loading })

        val saved = SavedStateHandle()
        val first = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", null, saved = saved)
        first.editing()
        first.update { it.copy(personId = "adult2", title = "Un puzzle") }
        val again = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", null, saved = saved).editing()
        assertEquals("Un puzzle", again.form.title)
        assertEquals("adult2", again.form.personId)
        assertTrue(again.hasChanges)
    }
}
