package fr.familia.app.feature.polls

import fr.familia.app.core.model.DateAnswer
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.polls.detail.PollDetailUiState
import fr.familia.app.feature.polls.detail.PollDetailViewModel
import fr.familia.app.feature.polls.edit.AskShortcut
import fr.familia.app.feature.polls.edit.DateChoice
import fr.familia.app.feature.polls.edit.PollEditUiState
import fr.familia.app.feature.polls.edit.PollEditViewModel
import fr.familia.app.feature.polls.edit.PollFormError
import fr.familia.app.feature.polls.list.PollsUiState
import fr.familia.app.feature.polls.list.PollsViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PollViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-25T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 25)

    // --- The list --------------------------------------------------------------------------------

    @Test
    fun `the list puts first the polls I still answer, for me or my children`() = runTest {
        val repo = FakePolls(
            listOf(
                poll("done", authorId = "adult2"),
                poll("todo", authorId = "adult2", askedIds = listOf("kid1", "adult2")),
                poll("old", authorId = "adult2", closed = true),
                poll("other", authorId = "adult2", askedIds = listOf("kid2")),
            ),
        )
        repo.votes.value = mapOf(
            "done" to listOf(
                PollVote("adult1", listOf(DateAnswer.Yes, DateAnswer.No), votedBy = "adult1"),
                PollVote("kid1", listOf(DateAnswer.Yes, DateAnswer.Yes), votedBy = "adult1"),
            ),
        )
        val viewModel = PollsViewModel(repo, FakeMembers(), "adult1", now = { now }, today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollsUiState.Success
        assertEquals(listOf("todo", "done"), state.open.map { it.poll.id })
        assertEquals(listOf("Kid1"), state.open[0].childrenToAnswer)
        assertFalse(state.open[0].meToAnswer)
        assertEquals(2, state.open[1].answeredCount)
        assertEquals(listOf("old"), state.closed.map { it.poll.id })
        assertTrue(state.canAsk)
    }

    @Test
    fun `a child answers but does not ask`() = runTest {
        val viewModel = PollsViewModel(FakePolls(listOf(poll("p"))), FakeMembers(), "kid1", now = { now }, today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollsUiState.Success
        assertFalse(state.canAsk)
        assertTrue(state.open.single().meToAnswer)
    }

    // --- The polls of an event (récit 2 quater) ---------------------------------------------------

    @Test
    fun `a poll launched from an event asks its guests and is linked to it`() = runTest {
        val repo = FakePolls()
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult, EventTarget.ForHousehold("h1"))))
        val viewModel = PollEditViewModel(repo, FakeMembers(), "adult1", today = { today }, eventId = "e1", events = events)
        val editing = viewModel.uiState.value as PollEditUiState.Editing
        assertEquals(setOf("admin1", "adult1", "kid1"), editing.form.askedIds)
        viewModel.update { it.copy(question = "Quel jour ?", dates = listOf(DateChoice(today.plusDays(3)), DateChoice(today.plusDays(4)))) }
        viewModel.save()
        assertEquals("e1", repo.created.single().eventId)
    }

    @Test
    fun `the page of an event lists only its own polls`() = runTest {
        val repo = FakePolls(listOf(poll("free"), poll("ofEvent").copy(eventId = "e1"), poll("ofOther").copy(eventId = "e2")))
        val viewModel = PollsViewModel(repo, FakeMembers(), "adult1", now = { now }, today = flowOf(today), eventId = "e1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollsUiState.Success
        assertEquals(listOf("ofEvent"), (state.open + state.closed).map { it.poll.id })
        val all = PollsViewModel(repo, FakeMembers(), "adult1", now = { now }, today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { all.uiState.collect {} }
        assertEquals(3, (all.uiState.value as PollsUiState.Success).let { it.open.size + it.closed.size })
    }

    // --- The form --------------------------------------------------------------------------------

    @Test
    fun `a new poll asks the whole family, and its viewers include the parents`() = runTest {
        val repo = FakePolls()
        val viewModel = PollEditViewModel(repo, FakeMembers(), "adult1", today = { today })
        viewModel.update { it.copy(question = " Noël ? ", dates = listOf(DateChoice(today.plusDays(20)), DateChoice(today.plusDays(21)))) }
        // Only the other household's child.
        viewModel.applyShortcut(AskShortcut("x", setOf("kid2")))
        viewModel.save()
        assertEquals(PollEditUiState.Done, viewModel.uiState.value)
        val created = repo.created.single()
        assertEquals("Noël ?", created.question)
        assertEquals(listOf("kid2"), created.askedIds)
        assertEquals(listOf("adult1", "kid2", "adult2"), created.viewerIds)
        assertEquals(listOf("2026-12-15", "2026-12-16"), created.options)
        assertNull(created.closesAt)
    }

    @Test
    fun `the form starts with everyone ticked and shows its errors on send`() = runTest {
        val viewModel = PollEditViewModel(FakePolls(), FakeMembers(), "adult1", today = { today })
        val editing = viewModel.uiState.value as PollEditUiState.Editing
        assertEquals(setOf("admin1", "adult1", "adult2", "kid1", "kid2"), editing.form.askedIds)
        // My household first.
        assertEquals("Foyer Un", editing.groups.first().name)
        viewModel.save()
        val errors = (viewModel.uiState.value as PollEditUiState.Editing).errors
        assertEquals(setOf(PollFormError.QuestionMissing, PollFormError.TooFewOptions), errors)
    }

    @Test
    fun `a child cannot open the form`() = runTest {
        val viewModel = PollEditViewModel(FakePolls(), FakeMembers(), "kid1", today = { today })
        assertEquals(PollEditUiState.Unavailable, viewModel.uiState.value)
    }

    @Test
    fun `a free poll offers between 2 and 6 answers`() = runTest {
        val viewModel = PollEditViewModel(FakePolls(), FakeMembers(), "adult1", today = { today })
        viewModel.update { it.copy(kind = Poll.Kind.Choice) }
        repeat(6) { viewModel.addOption() }
        assertEquals(6, (viewModel.uiState.value as PollEditUiState.Editing).form.choices.size)
        repeat(6) { viewModel.removeOption(0) }
        assertEquals(2, (viewModel.uiState.value as PollEditUiState.Editing).form.choices.size)
    }

    // --- One poll --------------------------------------------------------------------------------

    private fun detail(repo: FakePolls, me: String, events: FakeEvents = FakeEvents()) =
        PollDetailViewModel(repo, FakeMembers(), events, me, "p", now = { now })

    @Test
    fun `a parent answers for their child, each date needed`() = runTest {
        val repo = FakePolls(listOf(poll("p", authorId = "adult2")))
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollDetailUiState.Success
        assertEquals(listOf("adult2", "kid2"), state.answers.map { it.memberId })

        viewModel.setAnswer("kid2", 0, DateAnswer.Yes)
        viewModel.send("kid2")
        assertTrue((viewModel.uiState.value as PollDetailUiState.Success).answers[1].incomplete)
        assertTrue(repo.votes.value.isEmpty())

        viewModel.setAnswer("kid2", 1, DateAnswer.Maybe)
        viewModel.send("kid2")
        val vote = repo.votes.value.getValue("p").single()
        assertEquals(PollVote("kid2", listOf(DateAnswer.Yes, DateAnswer.Maybe), votedBy = "adult2"), vote)
        val after = viewModel.uiState.value as PollDetailUiState.Success
        assertTrue(after.answers[1].isSaved)
        assertEquals(listOf("Kid2"), after.results[0].yesNames)
        assertEquals(listOf("Kid2"), after.results[1].maybeNames)
    }

    @Test
    fun `with one answer only, a new tap replaces the previous one`() = runTest {
        val repo = FakePolls(listOf(poll("p", kind = Poll.Kind.Choice)))
        val viewModel = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.toggleChoice("adult1", 0)
        viewModel.toggleChoice("adult1", 2)
        viewModel.send("adult1")
        assertEquals(listOf(2), repo.votes.value.getValue("p").single().choices)
    }

    @Test
    fun `nobody answers a closed poll, and only its author manages it`() = runTest {
        val repo = FakePolls(listOf(poll("p", authorId = "adult2", closesAt = now.minusSeconds(60))))
        val viewModel = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollDetailUiState.Success
        assertFalse(state.isOpen)
        assertTrue(state.answers.isEmpty())
        assertFalse(state.canManage)
        // The admin principal manages every poll.
        val owner = detail(repo, "admin1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.uiState.collect {} }
        assertTrue((owner.uiState.value as PollDetailUiState.Success).canManage)
    }

    @Test
    fun `choosing a date adds the event, then closes the poll on it`() = runTest {
        val repo = FakePolls(listOf(poll("p")))
        val events = FakeEvents()
        val viewModel = detail(repo, "adult1", events)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue((viewModel.uiState.value as PollDetailUiState.Success).canChooseDate)
        viewModel.chooseDate(1, "  Repas de Noël ")
        val event = events.saved.single().first
        assertEquals("Repas de Noël", event.title)
        assertEquals(LocalDate.of(2026, 12, 20), event.date)
        assertEquals(12, event.time?.hour)
        assertEquals(EventTarget.ForFamily, event.target)
        assertEquals("adult1", event.authorId)
        assertEquals(Triple("p", 1, "e-new"), repo.chosen.single())
        val after = viewModel.uiState.value as PollDetailUiState.Success
        assertFalse(after.canChooseDate)
        assertTrue(after.results[1].isChosen)
    }

    @Test
    fun `a poll of my household only makes an event for my household`() = runTest {
        val repo = FakePolls(listOf(poll("p", askedIds = listOf("adult1", "kid1"))))
        val events = FakeEvents()
        val viewModel = detail(repo, "adult1", events)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.chooseDate(0, "")
        assertEquals(EventTarget.ForHousehold("h1"), events.saved.single().first.target)
        assertEquals("Question p", events.saved.single().first.title)
    }

    @Test
    fun `the table groups the answers by household`() = runTest {
        val repo = FakePolls(listOf(poll("p")))
        repo.votes.value = mapOf("p" to listOf(PollVote("adult2", listOf(DateAnswer.No, DateAnswer.Yes), votedBy = "adult2")))
        val viewModel = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as PollDetailUiState.Success
        assertEquals(listOf("Foyer Un", "Foyer Deux"), state.table.map { it.household })
        assertEquals(listOf(DateAnswer.No, DateAnswer.Yes), state.table[1].rows.first { it.firstName == "Adult2" }.answers)
        assertEquals(setOf(1), state.results.filter { it.isLeader }.map { it.index }.toSet())
        assertFalse("Adult2" in state.notAnsweredNames)
    }

    @Test
    fun `a deleted poll is missing`() = runTest {
        val repo = FakePolls(listOf(poll("p")))
        val viewModel = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.delete()
        assertEquals(PollDetailUiState.Missing, viewModel.uiState.value)
    }
}
