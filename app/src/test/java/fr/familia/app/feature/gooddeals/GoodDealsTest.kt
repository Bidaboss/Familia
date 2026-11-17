package fr.familia.app.feature.gooddeals

import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.linkSiteName
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gooddeals.list.GoodDealsUiState
import fr.familia.app.feature.gooddeals.list.GoodDealsViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A bon plan kept in memory, as Firestore would. */
class FakeGoodDeals(initial: List<GoodDeal> = emptyList()) : GoodDealRepository {
    val deals = MutableStateFlow(initial)
    val saved = mutableListOf<Pair<GoodDeal, Boolean>>()
    override fun observeAll(): Flow<List<GoodDeal>> = deals
    override fun observe(id: String): Flow<GoodDeal?> = deals.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: String): GoodDeal? = deals.value.firstOrNull { it.id == id }
    override fun newId(): String = "d-new"
    override suspend fun save(deal: GoodDeal, isNew: Boolean) {
        saved += deal to isNew
        deals.value = deals.value.filter { it.id != deal.id } + deal
    }
    override suspend fun delete(id: String) {
        deals.value = deals.value.filter { it.id != id }
    }
    override suspend fun setInterested(id: String, memberId: String, interested: Boolean) {
        deals.value = deals.value.map {
            if (it.id != id) it else it.copy(interestedIds = if (interested) it.interestedIds + memberId else it.interestedIds - memberId)
        }
    }
    override suspend fun setRecommended(id: String, memberId: String, recommended: Boolean) = Unit
}

/** Bons plans in Entraide (PRD, récit 45). Fictional family of TestFamily. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodDealsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-25T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 25)

    private fun deal(id: String, author: String = "adult1", days: Long = 3, interested: List<String> = emptyList()) =
        GoodDeal(
            id, GoodDealKind.Deal, "Bon plan $id", endsAt = now.plus(days, ChronoUnit.DAYS), interestedIds = interested,
            authorId = author, authorName = author.replaceFirstChar { it.uppercase() },
        )

    // --- The list --------------------------------------------------------------------------------

    @Test
    fun `the list shows the running bons plans, the one ending first on top`() = runTest {
        val address = deal("a1").copy(kind = GoodDealKind.Address, endsAt = null)
        val repo = FakeGoodDeals(listOf(deal("late", days = 10), deal("ended", days = -1), deal("soon", days = 1, interested = listOf("adult2")), address))
        val viewModel = GoodDealsViewModel(repo, FakeMembers(), "adult2", now = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as GoodDealsUiState.Success
        assertEquals(listOf("soon", "late"), state.deals.map { it.deal.id })
        assertTrue(state.deals.first().iAmInterested)
        assertTrue(state.canPublish)
    }

    @Test
    fun `a child sees the bons plans but has no Publier`() = runTest {
        val viewModel = GoodDealsViewModel(FakeGoodDeals(listOf(deal("d1"))), FakeMembers(), "kid1", now = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value as GoodDealsUiState.Success
        assertEquals(1, state.deals.size)
        assertFalse(state.canPublish)
    }

    // --- Small helpers ---------------------------------------------------------------------------

    @Test
    fun `a link is shown by its site`() {
        assertEquals("exemple.fr", linkSiteName("https://www.exemple.fr/promo?x=1"))
    }

    @Test
    fun `the last day of a bon plan is the day before its midnight end`() {
        val end = dealEnd(LocalDate.of(2026, 12, 2))
        assertEquals(LocalDate.of(2026, 12, 2), dealLastDay(end))
        assertTrue(dealDayAllowed(today, today))
        assertFalse(dealDayAllowed(today.minusDays(1), today))
        assertFalse(dealDayAllowed(today.plusDays(91), today))
    }
}
