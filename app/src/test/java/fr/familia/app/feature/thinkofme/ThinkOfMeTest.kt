package fr.familia.app.feature.thinkofme

import fr.familia.app.core.data.MomentRepository
import fr.familia.app.core.model.Moment
import fr.familia.app.core.model.MomentCheer
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

private class FakeMoments(initial: List<Moment> = emptyList()) : MomentRepository {
    val moments = MutableStateFlow(initial)
    val cheers = MutableStateFlow<List<MomentCheer>>(emptyList())
    val added = mutableListOf<Moment>()

    override fun observeFor(memberId: String): Flow<List<Moment>> = moments.map { l -> l.filter { it.forMemberId == memberId } }
    override fun observeCheers(momentId: String): Flow<List<MomentCheer>> = cheers.map { l -> l.filter { it.momentId == momentId } }
    override suspend fun add(moment: Moment) {
        added += moment
        moments.value = moments.value + moment.copy(id = "m${added.size}")
    }
    override suspend fun accept(momentId: String) {
        moments.value = moments.value.map { if (it.id == momentId) it.copy(pending = false) else it }
    }
    override suspend fun remove(momentId: String) {
        moments.value = moments.value.filterNot { it.id == momentId }
    }
    override suspend fun cheer(cheer: MomentCheer) {
        cheers.value = cheers.value + cheer
    }
    override suspend fun removeCheer(cheer: MomentCheer) {
        cheers.value = cheers.value - cheer
    }
}

/** « Pensez à moi » (PRD, récit 43). */
@OptIn(ExperimentalCoroutinesApi::class)
class ThinkOfMeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 12, 12)
    private val now = Instant.parse("2026-12-12T10:00:00Z")
    private val kid = TestFamily.kid.copy(birthDate = LocalDate.of(2018, 3, 1))
    private val teen = TestFamily.kid.copy(birthDate = LocalDate.of(2010, 3, 1))

    private fun started(forId: String) =
        Moment("m1", forId, forId, forId, "Examen de piano", today, startedAt = now.minusSeconds(3_600))

    @Test
    fun `a parent notes a moment for a young child, and for a teenager it waits for them`() = runTest {
        val repo = FakeMoments()
        val forKid = ThinkOfMeViewModel(repo, TestFamily.adult, kid, today) { now }
        forKid.note(" Match de foot ", today.plusDays(3))
        assertEquals("Match de foot", repo.added.single().title)
        assertFalse(repo.added.single().pending)
        val forTeen = ThinkOfMeViewModel(repo, TestFamily.adult, teen, today) { now }
        forTeen.note("Brevet blanc", today.plusDays(5))
        assertTrue(repo.added.last().pending)
    }

    @Test
    fun `nobody else notes one, nor in the past, nor in Voir comme`() = runTest {
        val repo = FakeMoments()
        ThinkOfMeViewModel(repo, TestFamily.otherAdult, kid, today) { now }.note("Match", today)
        ThinkOfMeViewModel(repo, TestFamily.adult, kid, today) { now }.note("Match", today.minusDays(1))
        ThinkOfMeViewModel(repo, TestFamily.adult, kid, today, mayWrite = false) { now }.note("Match", today)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun `the teenager accepts what a parent noted`() = runTest {
        val repo = FakeMoments(listOf(Moment("m1", teen.id, "adult1", "Adult1", "Brevet blanc", today.plusDays(2), pending = true)))
        val vm = ThinkOfMeViewModel(repo, teen, teen, today) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.canAccept)
        vm.accept(vm.uiState.value.upcoming.single())
        assertFalse(vm.uiState.value.upcoming.single().pending)
    }

    @Test
    fun `on the day, the family writes one word, and the person sees it`() = runTest {
        val repo = FakeMoments(listOf(started(TestFamily.adult.id)))
        val other = ThinkOfMeViewModel(repo, TestFamily.otherAdult, TestFamily.adult, today) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect {} }
        assertTrue(other.uiState.value.canCheer)
        assertFalse(other.uiState.value.canManage)
        other.cheer("Tu vas y arriver !")
        assertEquals("Tu vas y arriver !", other.uiState.value.mine?.text)
        other.cheer("Encore")
        assertEquals(1, repo.cheers.value.size)
        val self = ThinkOfMeViewModel(repo, TestFamily.adult, TestFamily.adult, today) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { self.uiState.collect {} }
        assertFalse(self.uiState.value.canCheer)
        assertEquals(1, self.uiState.value.cheers.size)
    }

    @Test
    fun `nothing to write before Familia starts it, nor 24 h after`() = runTest {
        val notStarted = Moment("m1", "adult1", "adult1", "Adult1", "Examen", today)
        val vm = ThinkOfMeViewModel(FakeMoments(listOf(notStarted)), TestFamily.otherAdult, TestFamily.adult, today) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.today)
        assertTrue(vm.uiState.value.isEmpty)
        assertFalse(started("adult1").isOpen(now.plusSeconds(25 * 3_600)))
    }
}
