package fr.familia.app.feature.helprequests

import fr.familia.app.core.data.AlreadyTakenException
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.helprequests.detail.HelpActionError
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailUiState
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailViewModel
import fr.familia.app.feature.helprequests.detail.canShareHelpRequest
import fr.familia.app.feature.helprequests.detail.cleanNote
import fr.familia.app.feature.helprequests.edit.HelpFormError
import fr.familia.app.feature.helprequests.edit.HelpRequestEditUiState
import fr.familia.app.feature.helprequests.edit.HelpRequestEditViewModel
import fr.familia.app.feature.helprequests.list.HelpRequestsUiState
import fr.familia.app.feature.helprequests.list.HelpRequestsViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HelpRequestViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 12, 1)

    // --- The tab ---------------------------------------------------------------------------------

    @Test
    fun `the tab lists open requests first, then taken ones, by date`() = runTest {
        val repo = FakeHelpRequests(
            listOf(
                helpRequest("late", TestFamily.adult, listOf("adult2"), date = today.plusDays(5)),
                helpRequest("taken", TestFamily.adult, listOf("adult2"), date = today.plusDays(1), status = HelpStatus.Taken, volunteer = TestFamily.admin),
                helpRequest("soon", TestFamily.kid, listOf("adult1", "adult2"), date = today.plusDays(2)),
                helpRequest("past", TestFamily.adult, listOf("adult2"), date = today.minusDays(1)),
                helpRequest("other", TestFamily.adult, listOf("admin1")),
            ),
        )
        val viewModel = HelpRequestsViewModel(repo, FakeMembers(), "adult2", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as HelpRequestsUiState.Success
        assertEquals(listOf("soon", "late"), state.open.map { it.request.id })
        assertEquals(listOf("taken"), state.taken.map { it.request.id })
        assertFalse(state.open.first().isMine)
    }

    // --- The form ---------------------------------------------------------------------------------

    private fun edit(repo: FakeHelpRequests, me: String = "adult1", requestId: String? = null, duplicateOf: String? = null) =
        HelpRequestEditViewModel(repo, FakeMembers(), me, requestId, duplicateOf, today = { today })

    private fun HelpRequestEditViewModel.editing() = uiState.value as HelpRequestEditUiState.Editing

    @Test
    fun `a new request ticks again the people of my previous one`() {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))))
        val state = edit(repo).editing()
        assertTrue(state.isNew)
        assertEquals(setOf("adult2"), state.form.notifyIds)
        assertEquals(listOf("adult1", "kid1"), state.targets.map { it.id })
        assertEquals(listOf("admin1", "adult2"), state.adults.map { it.id })
    }

    @Test
    fun `a title, a date and someone to warn are required`() {
        val viewModel = edit(FakeHelpRequests())
        viewModel.save()
        assertEquals(
            setOf(HelpFormError.TitleMissing, HelpFormError.DateMissing, HelpFormError.NobodyWarned),
            viewModel.editing().errors,
        )
        viewModel.update { it.copy(title = "Garde", date = today.minusDays(1)) }
        assertTrue(HelpFormError.DatePast in viewModel.editing().errors)
    }

    @Test
    fun `the shortcut « Tout le monde » ticks every adult, and unticks them`() {
        val viewModel = edit(FakeHelpRequests())
        val everyone = viewModel.editing().shortcuts.first()
        assertNull(everyone.label)
        viewModel.applyShortcut(everyone)
        assertEquals(setOf("admin1", "adult2"), viewModel.editing().form.notifyIds)
        viewModel.applyShortcut(everyone)
        assertEquals(emptySet<String>(), viewModel.editing().form.notifyIds)
    }

    @Test
    fun `the adults to warn are sorted by household, mine first`() {
        val groups = edit(FakeHelpRequests()).editing().groups
        assertEquals(listOf("Foyer Un", "Foyer Deux"), groups.map { it.name })
        assertEquals(listOf(listOf("admin1"), listOf("adult2")), groups.map { g -> g.members.map { it.id } })
    }

    @Test
    fun `ticking a household warns all its adults`() {
        val viewModel = edit(FakeHelpRequests())
        viewModel.setNotify(setOf("adult2"))
        assertEquals(setOf("adult2"), viewModel.editing().form.notifyIds)
        viewModel.setNotify(emptySet())
        assertEquals(emptySet<String>(), viewModel.editing().form.notifyIds)
    }

    @Test
    fun `a parent stays warned when their household is unticked`() {
        val viewModel = edit(FakeHelpRequests(), me = "kid1")
        viewModel.setNotify(setOf("adult1", "admin1"))
        assertEquals(setOf("admin1"), viewModel.editing().form.notifyIds)
        viewModel.setNotify(emptySet())
        assertEquals(setOf("adult1"), viewModel.editing().warnedIds)
    }

    @Test
    fun `a child's request always warns their parents`() {
        val repo = FakeHelpRequests()
        val viewModel = edit(repo, me = "kid1")
        assertEquals(setOf("adult1"), viewModel.editing().lockedIds)
        viewModel.update { it.copy(title = "Trajet au foot", date = today.plusDays(2)) }
        viewModel.save()
        assertEquals(HelpRequestEditUiState.Done, viewModel.uiState.value)
        val (request, isNew) = repo.saved.single()
        assertTrue(isNew)
        assertEquals(listOf("adult1"), request.notifyIds)
        assertEquals("kid1", request.authorId)
    }

    @Test
    fun `« Dupliquer » keeps everything but the date`() {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2", "admin1"))))
        val form = edit(repo, duplicateOf = "r1").editing().form
        assertEquals("Garde r1", form.title)
        assertNull(form.date)
        assertEquals(setOf("adult2", "admin1"), form.notifyIds)
    }

    @Test
    fun `widening after « Je ne peux pas » clears the answer`() {
        val declined = helpRequest("r1", TestFamily.adult, listOf("adult2")).copy(declinedBy = "adult2")
        val repo = FakeHelpRequests(listOf(declined))
        val viewModel = edit(repo, requestId = "r1")
        viewModel.toggle("admin1")
        viewModel.save()
        val (request, isNew) = repo.saved.single()
        assertFalse(isNew)
        assertEquals(listOf("admin1", "adult2"), request.notifyIds)
        assertNull(request.declinedBy)
    }

    @Test
    fun `someone who may not manage a request cannot open its form`() {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))))
        assertEquals(HelpRequestEditUiState.Unavailable, edit(repo, me = "adult2", requestId = "r1").uiState.value)
    }

    // --- One request ------------------------------------------------------------------------------

    private fun detail(repo: FakeHelpRequests, me: String) =
        HelpRequestDetailViewModel(repo, FakeMembers(), me, "r1", today = flowOf(today))

    @Test
    fun `Partager only for a request the whole family already reads`() = runTest {
        val everyone = listOf("admin1", "adult2", "kid1", "kid2")
        val repo = FakeHelpRequests(
            listOf(helpRequest("r1", TestFamily.adult, everyone), helpRequest("r2", TestFamily.adult, listOf("adult2"))),
        )
        val wide = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { wide.uiState.collect {} }
        assertTrue(canShareHelpRequest(wide.uiState.value as HelpRequestDetailUiState.Success))
        val narrow = HelpRequestDetailViewModel(repo, FakeMembers(), "adult1", "r2", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { narrow.uiState.collect {} }
        assertFalse(canShareHelpRequest(narrow.uiState.value as HelpRequestDetailUiState.Success))
        // Not the author's to send out.
        val other = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect {} }
        assertFalse(canShareHelpRequest(other.uiState.value as HelpRequestDetailUiState.Success))
    }

    @Test
    fun `« Je m'en occupe » takes the request for me`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2", "admin1"))))
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue((viewModel.uiState.value as HelpRequestDetailUiState.Success).canVolunteer)
        viewModel.take()
        val request = repo.requests.value.single()
        assertEquals(HelpStatus.Taken, request.status)
        assertEquals("adult2", request.volunteerId)
        assertEquals("Adult2", request.volunteerName)
        assertTrue((viewModel.uiState.value as HelpRequestDetailUiState.Success).canWithdraw)
    }

    @Test
    fun `the volunteer's word goes with « Je m'en occupe », and can be changed or cleared`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))))
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take("  Je passe vers 17 h  ")
        assertEquals("Je passe vers 17 h", repo.requests.value.single().volunteerNote)
        viewModel.setNote("Plutôt 18 h")
        assertEquals("Plutôt 18 h", repo.requests.value.single().volunteerNote)
        viewModel.setNote("   ")
        assertNull(repo.requests.value.single().volunteerNote)
        assertEquals(200, cleanNote("x".repeat(300))!!.length)
    }

    @Test
    fun `the second volunteer is told who was quicker`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2", "admin1"))))
        // The server already gave it to admin1, but this screen has not heard of it yet.
        val late = object : HelpRequestRepository by repo {
            override suspend fun take(id: String, volunteerId: String, volunteerName: String, note: String?) {
                throw AlreadyTakenException("Admin1")
            }
        }
        val viewModel = HelpRequestDetailViewModel(late, FakeMembers(), "adult2", "r1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take()
        assertEquals(HelpActionError.AlreadyTaken("Admin1"), (viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
        viewModel.dismissError()
        assertNull((viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
    }

    @Test
    fun `« Je ne peux pas » refused because someone took it just before says who`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2", "admin1"))))
        // Admin1 takes it at the same second; the rules then refuse my answer on a request no longer open.
        val late = object : HelpRequestRepository by repo {
            override suspend fun decline(id: String, memberId: String) {
                repo.requests.value = repo.requests.value.map {
                    it.copy(status = HelpStatus.Taken, volunteerId = "admin1", volunteerName = "Admin1")
                }
                throw IllegalStateException("refused")
            }
        }
        val viewModel = HelpRequestDetailViewModel(late, FakeMembers(), "adult2", "r1", today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.decline()
        assertEquals(HelpActionError.AlreadyTaken("Admin1"), (viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
        viewModel.dismissError()
        assertNull((viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
    }

    @Test
    fun `a refusal on a request that did not move stays the plain failure`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))), failWith = IllegalStateException("refused"))
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.decline()
        assertEquals(HelpActionError.Failed, (viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
    }

    @Test
    fun `without network, « Je m'en occupe » says so instead of waiting forever`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))), hangTake = true)
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.take()
        advanceTimeBy(11_000)
        assertEquals(HelpActionError.NeedsNetwork, (viewModel.uiState.value as HelpRequestDetailUiState.Success).error)
        assertEquals(HelpStatus.Open, repo.requests.value.single().status)
    }

    @Test
    fun `the only person warned answers « Je ne peux pas », and the author sees it`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))))
        val viewModel = detail(repo, "adult2")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.decline()
        assertEquals("adult2", repo.requests.value.single().declinedBy)
        val author = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { author.uiState.collect {} }
        assertEquals("Adult2", (author.uiState.value as HelpRequestDetailUiState.Success).declinedName)
    }

    @Test
    fun `a cancelled request is shown as missing`() = runTest {
        val repo = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.adult, listOf("adult2"))))
        val viewModel = detail(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.cancel()
        assertEquals(HelpRequestDetailUiState.Missing, viewModel.uiState.value)
    }
}
