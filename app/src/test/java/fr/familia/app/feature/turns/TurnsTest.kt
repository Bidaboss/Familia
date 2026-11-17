package fr.familia.app.feature.turns

import fr.familia.app.core.data.TurnRepository
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Turn
import fr.familia.app.core.model.TurnSwap
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import java.time.Instant

/** « À qui le tour ? » (récit 58): the rotation itself, the rights, and the screens' ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val family = TestFamily.family.copy(
        households = TestFamily.family.households + Household("h3", "Foyer Trois"),
    )

    private fun turn(current: Int = 0, swap: TurnSwap? = null, by: String = "adult2") =
        Turn("t1", "Qui reçoit Noël", listOf("h1", "h2", "h3"), current, emptyList(), swap, by)

    private class FakeTurns(initial: List<Turn>) : TurnRepository {
        val turns = MutableStateFlow(initial)
        val created = mutableListOf<Pair<String, List<String>>>()
        val edited = mutableListOf<Triple<String, List<String>, Int>>()
        override fun observeAll(): Flow<List<Turn>> = turns
        override fun observe(id: String): Flow<Turn?> = turns.map { l -> l.firstOrNull { it.id == id } }
        override fun newId(): String = "t-new"
        override suspend fun create(id: String, title: String, householdIds: List<String>, me: Member) {
            created += title to householdIds
        }
        override suspend fun edit(id: String, title: String, householdIds: List<String>, current: Int) {
            edited += Triple(title, householdIds, current)
        }
        private fun change(t: Turn, next: Turn) {
            turns.value = turns.value.map { if (it.id == t.id) next else it }
        }
        override suspend fun done(turn: Turn, label: String) = change(turn, turn.done(label, Instant.EPOCH))
        override suspend fun skip(turn: Turn) = change(turn, turn.skipped())
        override suspend fun askSwap(turn: Turn, me: Member, toHouseholdId: String) = change(turn, turn.copy(swap = turn.swapAsk(me, toHouseholdId)))
        override suspend fun acceptSwap(turn: Turn) = change(turn, turn.swapAccepted())
        override suspend fun dropSwap(id: String) {
            turns.value = turns.value.map { if (it.id == id) it.copy(swap = null) else it }
        }
        /** Offline: the change lands in the cache at once, and the server answers when this completes. */
        var server: CompletableDeferred<Unit>? = null
        override suspend fun delete(id: String) {
            turns.value = turns.value.filter { it.id != id }
            server?.await()
        }
    }

    // --- The rotation ---------------------------------------------------------------------------

    @Test
    fun `done goes to the next household, round the rotation, with one line of history`() {
        val after = turn(current = 2).done("  Noël 2026  ", Instant.EPOCH)
        assertEquals(0, after.current)
        assertEquals("h3", after.history.single().householdId)
        assertEquals("Noël 2026", after.history.single().label)
    }

    @Test
    fun `a pass changes place with the next household, and the next one is now first`() {
        val after = turn().skipped()
        assertEquals(listOf("h2", "h1", "h3"), after.householdIds)
        assertEquals("h2", after.currentHouseholdId)
        assertEquals("h1", after.nextHouseholdId)
    }

    @Test
    fun `an exchange is proposed for my own household only, and accepted it swaps the two`() {
        val ask = turn().swapAsk(TestFamily.otherAdult, "h1")!!
        assertEquals(TurnSwap("h2", "h1", 1, 0, "adult2", "Adult2"), ask)
        assertNull(turn().swapAsk(TestFamily.otherAdult, "h2"))
        assertNull(turn(swap = ask).swapAsk(TestFamily.adult, "h3"))
        assertEquals(listOf("h2", "h1", "h3"), turn(swap = ask).swapAccepted().householdIds)
        assertNull(turn(swap = ask).swapAccepted().swap)
    }

    // --- The rights -----------------------------------------------------------------------------

    @Test
    fun `the household whose turn it is says done or passes, a child only reads`() {
        val mine = turnRights(turn(), TestFamily.adult, family.ownerId)
        assertTrue(mine.done && mine.skip && mine.createEvent && mine.askSwap)
        assertFalse(mine.edit)
        val other = turnRights(turn(), TestFamily.otherAdult, family.ownerId)
        // adult2 wrote it: they may change it and say « C'est fait », not pass the turn of another household.
        assertTrue(other.edit && other.done)
        assertFalse(other.skip || other.createEvent)
        val kid = turnRights(turn(), TestFamily.kid, family.ownerId)
        assertFalse(kid.done || kid.skip || kid.askSwap || kid.edit)
        assertTrue(turnRights(turn(), TestFamily.admin, family.ownerId).edit)
    }

    @Test
    fun `the other household answers an exchange, mine can withdraw it`() {
        val ask = turn().swapAsk(TestFamily.otherAdult, "h1")!!
        assertTrue(turnRights(turn(swap = ask), TestFamily.adult, family.ownerId).answerSwap)
        assertTrue(turnRights(turn(swap = ask), TestFamily.otherAdult, family.ownerId).withdrawSwap)
        assertFalse(turnRights(turn(swap = ask), TestFamily.adult, family.ownerId).askSwap)
    }

    // --- The list -------------------------------------------------------------------------------

    @Test
    fun `the list shows whose turn it is, mine first`() = runTest {
        val repo = FakeTurns(listOf(turn().copy(id = "a", title = "Anniversaires", current = 1), turn().copy(id = "z", title = "Zoo")))
        val vm = TurnsViewModel(repo, FakeMembers(family), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as TurnsUiState.Success
        assertEquals(listOf("z", "a"), state.rows.map { it.turn.id })
        assertEquals("Foyer Un", state.rows.first().nowName)
        assertTrue(state.canCreate)
        val kid = TurnsViewModel(repo, FakeMembers(family), "kid1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { kid.uiState.collect {} }
        assertFalse((kid.uiState.value as TurnsUiState.Success).canCreate)
    }

    // --- The form -------------------------------------------------------------------------------

    @Test
    fun `a new rotation needs a title and two households, in the order chosen`() = runTest {
        val repo = FakeTurns(emptyList())
        val vm = TurnEditViewModel(repo, FakeMembers(family), "adult1", null)
        vm.save()
        assertEquals(setOf(TurnFormError.TitleMissing, TurnFormError.TooFewHouseholds), (vm.uiState.value as TurnEditUiState.Editing).errors)
        vm.setTitle("Repas du dimanche")
        vm.toggle("h2")
        vm.toggle("h1")
        vm.toggle("h3")
        vm.moveUp("h1")
        vm.toggle("h3")
        vm.save()
        assertEquals(TurnEditUiState.Done, vm.uiState.value)
        assertEquals("Repas du dimanche" to listOf("h1", "h2"), repo.created.single())
    }

    @Test
    fun `only the author or the admin principal changes it, keeping whose turn it is`() = runTest {
        val repo = FakeTurns(listOf(turn(current = 1)))
        assertEquals(TurnEditUiState.Unavailable, TurnEditViewModel(repo, FakeMembers(family), "adult1", "t1").uiState.value)
        val vm = TurnEditViewModel(repo, FakeMembers(family), "adult2", "t1")
        vm.moveDown("h2")
        vm.save()
        assertEquals(Triple("Qui reçoit Noël", listOf("h1", "h3", "h2"), 2), repo.edited.single())
    }

    // --- The detail -----------------------------------------------------------------------------

    @Test
    fun `the detail says whose turn it is and who comes next, the history newest first`() = runTest {
        val repo = FakeTurns(listOf(turn().done("Noël 2025", Instant.EPOCH)))
        val vm = TurnDetailViewModel(repo, FakeMembers(family), "adult2", "t1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val s = vm.uiState.value as TurnDetailUiState.Success
        assertEquals("Foyer Deux", s.nowName)
        assertEquals("Foyer Trois", s.nextName)
        assertEquals("Foyer Un", s.history.single().name)
        assertEquals(listOf("h1", "h3"), s.swapTargets.map { it.id }.sorted())
        vm.done("Noël 2026")
        val after = vm.uiState.value as TurnDetailUiState.Success
        assertEquals("Foyer Trois", after.nowName)
        assertEquals(listOf("Noël 2026", "Noël 2025"), after.history.map { it.done.label })
    }

    @Test
    fun `an exchange proposed, then accepted by the other household`() = runTest {
        val repo = FakeTurns(listOf(turn()))
        val asker = TurnDetailViewModel(repo, FakeMembers(family), "adult2", "t1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { asker.uiState.collect {} }
        asker.askSwap("h1")
        assertEquals("Foyer Un", (asker.uiState.value as TurnDetailUiState.Success).swapOtherName)
        val other = TurnDetailViewModel(repo, FakeMembers(family), "adult1", "t1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect {} }
        assertTrue((other.uiState.value as TurnDetailUiState.Success).rights.answerSwap)
        other.acceptSwap()
        val s = other.uiState.value as TurnDetailUiState.Success
        assertEquals("Foyer Deux", s.nowName)
        assertNull(s.turn.swap)
    }

    @Test
    fun `offline, Supprimer closes the screen once the deletion waits for the network`() = runTest {
        val repo = FakeTurns(listOf(turn())).apply { server = CompletableDeferred() }
        val vm = TurnDetailViewModel(repo, FakeMembers(family), "adult2", "t1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        var closed = false
        vm.delete { closed = true }
        assertFalse(closed)
        advanceTimeBy(WRITE_TIMEOUT_MS + 1)
        assertTrue(closed)
    }

    @Test
    fun `a rotation removed meanwhile says so`() = runTest {
        val vm = TurnDetailViewModel(FakeTurns(emptyList()), FakeMembers(family), "adult1", "t1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(TurnDetailUiState.Missing, vm.uiState.value)
    }
}
