package fr.familia.app.feature.wishes

import fr.familia.app.core.data.BirthdayCardRepository
import fr.familia.app.core.data.WRITE_WAIT_MILLIS
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
class BirthdayCardsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 21)
    private val now = Instant.parse("2026-11-21T12:00:00Z")

    private fun member(id: String, birth: LocalDate? = null) = Member(
        id = id, firstName = id.replaceFirstChar { it.uppercase() }, email = null, birthDate = birth, householdId = "h1",
        profile = MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null,
    )

    private val camille = member("camille", LocalDate.of(1990, 11, 21))
    private val ines = member("ines")

    private class Cards : BirthdayCardRepository {
        val all = MutableStateFlow<List<BirthdayCard>>(emptyList())

        /** Offline: the card lands in the cache at once, and the server answers when this completes. */
        var server: CompletableDeferred<Unit>? = null
        override fun observeFor(memberId: String, year: Int): Flow<List<BirthdayCard>> =
            all.map { l -> l.filter { it.forMemberId == memberId && it.year == year } }
        override suspend fun save(card: BirthdayCard, isNew: Boolean) {
            all.value = all.value.filterNot { it.id == card.id } + card
            server?.await()
        }
        override suspend fun remove(card: BirthdayCard) {
            all.value = all.value.filterNot { it.id == card.id }
        }
    }

    private class News(private val createdAt: Instant?) : GoodNewsRepository {
        override fun observePost(id: String): Flow<GoodNews?> = flowOf(
            createdAt?.let {
                GoodNews(id, "Joyeux anniversaire", emptyList(), "familia", "Familia", "camille", "h1", createdAt = it)
            },
        )
        override fun observeLatest(count: Int): Flow<List<GoodNews>> = flowOf(emptyList())
        override fun newId(): String = "x"
        override suspend fun publish(news: GoodNews) = Unit
        override suspend fun edit(id: String, text: String, mentionIds: List<String>) = Unit
        override suspend fun react(id: String, memberId: String, reaction: Reaction?) = Unit
        override suspend fun setHidden(id: String, memberId: String, hidden: Boolean) = Unit
        override suspend fun remove(id: String) = Unit
    }

    @Test
    fun `the last birthday, today included, and none without a date`() {
        assertEquals(2026, BirthdayWishes.lastBirthdayYear(LocalDate.of(1990, 11, 21), today))
        assertEquals(2025, BirthdayWishes.lastBirthdayYear(LocalDate.of(1990, 11, 22), today))
        assertEquals(2026, BirthdayWishes.lastBirthdayYear(LocalDate.of(1990, 1, 2), today))
        assertNull(BirthdayWishes.lastBirthdayYear(null, today))
        assertNull(BirthdayWishes.lastBirthdayYear(LocalDate.of(2030, 1, 1), today))
    }

    @Test
    fun `on the day I write my card, change it, then remove it`() = runTest {
        val cards = Cards()
        val vm = BirthdayCardsViewModel(cards, News(now.minusSeconds(7200)), ines, camille, today, clock = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.canWrite)
        vm.send("  Bon anniversaire !  ")
        assertEquals("Bon anniversaire !", vm.uiState.value.mine?.text)
        assertEquals("2026-camille-ines", cards.all.value.single().id)
        vm.send("Joyeux anniversaire !")
        assertEquals(listOf("Joyeux anniversaire !"), cards.all.value.map { it.text })
        vm.remove()
        assertTrue(cards.all.value.isEmpty())
    }

    @Test
    fun `offline the card shows at once, Envoyer comes back, and a refusal later is said`() = runTest {
        val cards = Cards().apply { server = CompletableDeferred() }
        val vm = BirthdayCardsViewModel(cards, News(now.minusSeconds(7200)), ines, camille, today, clock = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.send("Bon anniversaire !")
        assertEquals("Bon anniversaire !", vm.uiState.value.mine?.text)
        assertTrue(vm.uiState.value.saving)
        advanceTimeBy(WRITE_WAIT_MILLIS + 1)
        assertFalse(vm.uiState.value.saving)
        assertFalse(vm.uiState.value.failed)
        // The network is back and the server refuses it (the 24 h are over, say).
        cards.server?.completeExceptionally(IllegalStateException("PERMISSION_DENIED"))
        runCurrent()
        assertTrue(vm.uiState.value.failed)
    }

    @Test
    fun `no card on my own page, after 24 h, nor in Voir comme`() = runTest {
        val mine = BirthdayCardsViewModel(Cards(), News(now), camille, camille, today, clock = { now })
        val late = BirthdayCardsViewModel(Cards(), News(now.minusSeconds(25 * 3600)), ines, camille, today, clock = { now })
        val viewAs = BirthdayCardsViewModel(Cards(), News(now), ines, camille, today, mayWrite = false, clock = { now })
        for (vm in listOf(mine, late, viewAs)) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            assertFalse(vm.uiState.value.canWrite)
        }
    }

    @Test
    fun `the year of a birthday news, read from its id`() {
        assertEquals(2025, BirthdayCard.yearOfNews(BirthdayCard.newsId(2025, "camille")))
        assertNull(BirthdayCard.yearOfNews("n1"))
        assertNull(BirthdayCard.yearOfNews("birthday-x-camille"))
    }

    @Test
    fun `the news of a past birthday shows the cards of that year, read only`() = runTest {
        val cards = Cards()
        cards.all.value = listOf(BirthdayCard("camille", "ines", "Inès", "L'an dernier", 2025), BirthdayCard("camille", "ines", "Inès", "Cette année", 2026))
        val vm = BirthdayCardsViewModel(cards, News(now), ines, camille, today, birthdayYear = 2025, clock = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("L'an dernier"), vm.uiState.value.cards.map { it.text })
        assertFalse(vm.uiState.value.canWrite)
    }

    @Test
    fun `the cards of the last birthday stay on the page, without the composer`() = runTest {
        val cards = Cards()
        cards.all.value = listOf(BirthdayCard("camille", "ines", "Inès", "Bravo", 2026))
        val vm = BirthdayCardsViewModel(cards, News(null), ines, camille, today.plusDays(3), clock = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("Bravo"), vm.uiState.value.cards.map { it.text })
        assertFalse(vm.uiState.value.canWrite)
        assertFalse(vm.uiState.value.isEmpty)
    }
}
