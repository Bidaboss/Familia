package fr.familia.app.feature.restore

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.joinme.FakeJoinMe
import fr.familia.app.feature.joinme.FakeLocator
import fr.familia.app.feature.joinme.JoinMeUiState
import fr.familia.app.feature.joinme.JoinMeViewModel
import fr.familia.app.feature.polls.FakePolls
import fr.familia.app.feature.polls.edit.DateChoice
import fr.familia.app.feature.polls.edit.PollEditUiState
import fr.familia.app.feature.polls.edit.PollEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Android stops Familia in the background (appareil photo, WhatsApp) and starts it again: what was
 * typed comes back, and what had left is never sent twice. A new view model on the same
 * SavedStateHandle plays the new process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoppedInBackgroundTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun `a poll being typed comes back as it was`() = runTest {
        val handle = SavedStateHandle()
        val first = PollEditViewModel(FakePolls(), FakeMembers(), "adult1", today = { today }, savedState = handle)
        first.update {
            it.copy(
                question = "Quel jour pour la galette ?",
                dates = listOf(DateChoice(today.plusDays(40), LocalTime.of(16, 0)), DateChoice(today.plusDays(41))),
                askedIds = setOf("adult2", "kid2"),
            )
        }

        val again = PollEditViewModel(FakePolls(), FakeMembers(), "adult1", today = { today }, savedState = handle)
        val form = (again.uiState.value as PollEditUiState.Editing).form
        assertEquals("Quel jour pour la galette ?", form.question)
        assertEquals(listOf(DateChoice(today.plusDays(40), LocalTime.of(16, 0)), DateChoice(today.plusDays(41))), form.dates)
        assertEquals(setOf("adult2", "kid2"), form.askedIds)
        assertTrue((again.uiState.value as PollEditUiState.Editing).hasChanges)
    }

    @Test
    fun `a poll that had left is not offered again`() = runTest {
        val handle = SavedStateHandle()
        val repo = FakePolls()
        val first = PollEditViewModel(repo, FakeMembers(), "adult1", today = { today }, savedState = handle)
        first.update { it.copy(question = "Galette ?", dates = listOf(DateChoice(today.plusDays(40)), DateChoice(today.plusDays(41)))) }
        first.save()

        val again = PollEditViewModel(repo, FakeMembers(), "adult1", today = { today }, savedState = handle)
        assertEquals(PollEditUiState.Done, again.uiState.value)
        assertEquals(1, repo.created.size)
    }

    @Test
    fun `a share being written comes back with its place, its note and who is ticked`() = runTest {
        val handle = SavedStateHandle()
        val first = JoinMeViewModel(FakeMembers(), FakeJoinMe(), FakeLocator(), "adult1", savedState = handle)
        first.locate()
        first.changeNote("On est au fond, côté terrasse")
        first.toggle("adult2")

        val again = JoinMeViewModel(FakeMembers(), FakeJoinMe(), FakeLocator(), "adult1", savedState = handle)
        val state = again.uiState.value as JoinMeUiState.Editing
        assertEquals("On est au fond, côté terrasse", state.note)
        assertEquals(setOf("adult2"), state.recipientIds)
        assertEquals(46.4915, state.place?.lat ?: 0.0, 0.0)
        assertTrue(state.canSend)
    }

    @Test
    fun `a share that had left is not sent twice`() = runTest {
        val handle = SavedStateHandle()
        val repo = FakeJoinMe()
        val first = JoinMeViewModel(FakeMembers(), repo, FakeLocator(), "adult1", savedState = handle)
        first.locate()
        first.toggle("adult2")
        first.send()

        val again = JoinMeViewModel(FakeMembers(), repo, FakeLocator(), "adult1", savedState = handle)
        assertEquals(JoinMeUiState.Sent, again.uiState.value)
        assertEquals(1, repo.shares.value.size)
    }
}
