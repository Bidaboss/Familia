package fr.familia.app.feature.helprequests

import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.helprequests.detail.HelpActionError
import fr.familia.app.feature.helprequests.list.HelpRequestsUiState
import fr.familia.app.feature.helprequests.list.HelpRequestsViewModel
import fr.familia.app.feature.helprequests.todo.HelpRequestTodosViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** « J'y vais » / « Non » on the Accueil and « On a besoin de toi » in Entraide (présentation R1 bis). */
@OptIn(ExperimentalCoroutinesApi::class)
class HelpRequestTodosTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 12, 1)

    private fun requests() = FakeHelpRequests(
        listOf(
            helpRequest("late", TestFamily.adult, listOf("adult2", "admin1"), date = today.plusDays(5)),
            helpRequest("soon", TestFamily.kid, listOf("adult1", "adult2"), date = today.plusDays(1)),
            helpRequest("only", TestFamily.adult, listOf("adult2"), date = today.plusDays(2)),
            helpRequest("taken", TestFamily.adult, listOf("adult2"), date = today.plusDays(1), status = HelpStatus.Taken, volunteer = TestFamily.admin),
            helpRequest("past", TestFamily.adult, listOf("adult2"), date = today.minusDays(1)),
            helpRequest("notMe", TestFamily.adult, listOf("admin1")),
        ),
    )

    private fun todos(repo: FakeHelpRequests) = HelpRequestTodosViewModel(repo, FakeMembers(), "adult2", today = flowOf(today))

    @Test
    fun `À faire holds the open requests that wait for me, the soonest first`() = runTest {
        val viewModel = todos(requests())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.todos.collect {} }
        assertEquals(listOf("soon", "only", "late"), viewModel.todos.value.map { it.request.id })
        val soon = viewModel.todos.value.first()
        assertEquals("kid1", soon.author?.id)
        assertNull(soon.helpedName)
    }

    @Test
    fun `« J'y vais » takes the request, which leaves À faire at once`() = runTest {
        val repo = requests()
        val viewModel = todos(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.todos.collect {} }
        viewModel.take(viewModel.todos.value.first { it.request.id == "late" }.request)
        assertEquals("adult2", repo.requests.value.first { it.id == "late" }.volunteerId)
        assertEquals(listOf("soon", "only"), viewModel.todos.value.map { it.request.id })
        assertNull(viewModel.error.value)
    }

    @Test
    fun `« J'y vais » on a request someone took meanwhile says who`() = runTest {
        val repo = requests()
        val viewModel = todos(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.todos.collect {} }
        val late = viewModel.todos.value.first { it.request.id == "late" }.request
        repo.take("late", "admin1", "Admin1")
        viewModel.take(late)
        assertEquals(HelpActionError.AlreadyTaken("Admin1"), viewModel.error.value)
        viewModel.dismissError()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `« Non » from the only person warned answers « Je ne peux pas » to the author`() = runTest {
        val repo = requests()
        val viewModel = todos(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.todos.collect {} }
        viewModel.decline(viewModel.todos.value.first { it.request.id == "only" }.request)
        val only = repo.requests.value.first { it.id == "only" }
        assertEquals("adult2", only.declinedBy)
        assertTrue(only.unavailableIds.isEmpty())
        assertEquals(listOf("soon", "late"), viewModel.todos.value.map { it.request.id })
    }

    @Test
    fun `« Non » when others were warned too only hides it for me, and the badge drops`() = runTest {
        val repo = requests()
        val viewModel = todos(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.todos.collect {} }
        viewModel.decline(viewModel.todos.value.first { it.request.id == "late" }.request)
        val late = repo.requests.value.first { it.id == "late" }
        assertNull(late.declinedBy)
        assertEquals(listOf("adult2"), late.unavailableIds)
        assertEquals(HelpStatus.Open, late.status)
        assertEquals(listOf("soon", "only"), viewModel.todos.value.map { it.request.id })
        assertEquals(2, badgeCount(repo.requests.value, TestFamily.otherAdult, today))
        // The others warned still see it: « late », and « notMe » asked of them only.
        assertEquals(2, badgeCount(repo.requests.value, TestFamily.admin, today))
    }

    @Test
    fun `Entraide puts what waits for me first, then my requests, the taken ones and my no`() = runTest {
        val repo = requests()
        repo.markUnavailable("late", "adult2")
        val viewModel = HelpRequestsViewModel(repo, FakeMembers(), "adult2", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as HelpRequestsUiState.Success
        assertEquals(listOf("soon", "only"), state.forMe.map { it.request.id })
        assertTrue(state.mine.isEmpty())
        assertEquals(listOf("taken"), state.taken.map { it.request.id })
        assertEquals("admin1", state.taken.single().volunteer?.id)
        assertEquals(listOf("late"), state.saidNo.map { it.request.id })
    }

    @Test
    fun `« Je m'en occupe » from Entraide takes the request`() = runTest {
        val repo = requests()
        val viewModel = HelpRequestsViewModel(repo, FakeMembers(), "adult2", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as HelpRequestsUiState.Success
        viewModel.take(state.forMe.first().request)
        val after = viewModel.uiState.value as HelpRequestsUiState.Success
        assertEquals(listOf("only", "late"), after.forMe.map { it.request.id })
        assertTrue(after.taken.any { it.request.id == "soon" && it.iAmVolunteer })
        assertNull(viewModel.error.value)
    }
}
