package fr.familia.app.feature.souvenirs

import fr.familia.app.core.data.SouvenirRepository
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.members.profile.MemberProfileUiState
import fr.familia.app.feature.members.profile.MemberProfileViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Souvenirs in memory, given to adult1; the family sees only those shown, as the rules say. */
private class FakeSouvenirs(souvenirs: List<Souvenir>, private val counters: SouvenirCounters = SouvenirCounters()) : SouvenirRepository {
    val souvenirs = MutableStateFlow(souvenirs)
    override fun observeMine(memberId: String): Flow<List<Souvenir>> = souvenirs
    override fun observeShown(memberId: String): Flow<List<Souvenir>> = souvenirs.map { list -> list.filter { it.shown } }
    override fun observeCounters(memberId: String): Flow<SouvenirCounters> = MutableStateFlow(counters)
    override suspend fun setShown(memberId: String, key: String, shown: Boolean) {
        souvenirs.value = souvenirs.value.map { if (it.key == key) it.copy(shown = shown) else it }
    }
}

/** The « Bravo » already shown, in memory instead of the phone's preferences. */
private class FakeCelebrations(var keys: Set<String> = emptySet()) : SouvenirCelebrations {
    override fun celebrated(memberId: String): Set<String> = keys
    override fun markCelebrated(memberId: String, keys: Set<String>) { this.keys = this.keys + keys }
}

class SouvenirsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val at = Instant.parse("2026-12-02T10:00:00Z")
    private val earned = listOf(Souvenir("firstGoodNews", at, shown = false), Souvenir("oneYear", at, shown = true))

    @Test
    fun `every key of the server has its medal`() {
        val server = listOf("firstGoodNews", "reactions10", "helpGiven5", "firstArrival", "firstWeeklyAnswer", "weeklyAnswers10", "oneYear")
        assertEquals(server.toSet(), SouvenirKind.entries.map { it.key }.toSet())
    }

    @Test
    fun `the next souvenir of each family, never a total`() {
        assertEquals(
            listOf(SouvenirKind.Reactions10, SouvenirKind.HelpGiven5, SouvenirKind.FirstArrival),
            nextSouvenirs(earned),
        )
        assertEquals(3, SouvenirKind.HelpGiven5.left(SouvenirCounters(helpGiven = 2)))
        assertEquals(null, SouvenirKind.FirstArrival.left(SouvenirCounters()))
    }

    @Test
    fun `on my page every souvenir`() = runTest {
        val repo = FakeSouvenirs(earned, SouvenirCounters(reactions = 4))
        val vm = MemberProfileViewModel(FakeMembers(), repo, "adult1", "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val mine = vm.uiState.value as MemberProfileUiState.Ready
        assertTrue(mine.isMe)
        assertEquals(2, mine.souvenirs.size)
        assertEquals(4, mine.counters.reactions)
    }

    @Test
    fun `on someone else's page only what they show, and no switch`() = runTest {
        val vm = MemberProfileViewModel(FakeMembers(), FakeSouvenirs(earned), "adult2", "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val theirs = vm.uiState.value as MemberProfileUiState.Ready
        assertFalse(theirs.isMe)
        assertEquals(listOf("oneYear"), theirs.souvenirs.map { it.key })
        assertEquals(SouvenirCounters(), theirs.counters)
    }

    @Test
    fun `the Bravo shows once, for the newest souvenir, and a new one shows it again`() = runTest {
        val repo = FakeSouvenirs(listOf(Souvenir("firstGoodNews", at, shown = false), Souvenir("oneYear", at.plusSeconds(60), shown = false)))
        val celebrations = FakeCelebrations()
        val vm = MemberProfileViewModel(FakeMembers(), repo, "adult1", "adult1", celebrations)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("oneYear", (vm.uiState.value as MemberProfileUiState.Ready).bravo?.key)

        vm.dismissBravo()
        assertEquals(null, (vm.uiState.value as MemberProfileUiState.Ready).bravo)
        assertEquals(setOf("firstGoodNews", "oneYear"), celebrations.keys)

        repo.souvenirs.value = repo.souvenirs.value + Souvenir("helpGiven5", at.plusSeconds(120), shown = false)
        assertEquals("helpGiven5", (vm.uiState.value as MemberProfileUiState.Ready).bravo?.key)
    }

    @Test
    fun `no Bravo on someone else's page`() = runTest {
        val vm = MemberProfileViewModel(FakeMembers(), FakeSouvenirs(earned), "adult2", "adult1", FakeCelebrations())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(null, (vm.uiState.value as MemberProfileUiState.Ready).bravo)
    }
}
