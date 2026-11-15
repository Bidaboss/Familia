package fr.familia.app.feature.gifts

import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.item.GiftItemForm
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.item.normalizeLink
import fr.familia.app.feature.gifts.item.validateItem
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Fictional family of [TestFamily]: adult1 keeps nobody else's lists, adult2 keeps kid2's (child without the app). */
class GiftsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val family = TestFamily.family
    private val today = LocalDate.of(2026, 11, 15)

    private fun list(
        id: String,
        beneficiary: String,
        kind: GiftListKind = GiftListKind.Own,
        viewers: List<String> = listOf(beneficiary),
        creator: String = beneficiary,
        revealed: Boolean = false,
    ) = GiftList(id, kind, "Liste $id", GiftOccasion.Christmas, LocalDate.of(2026, 12, 25), beneficiary, viewers, creator, creator, revealed)

    @Test
    fun `a member keeps their own lists and those of their children without the app only`() {
        assertTrue(keepsListsOf(TestFamily.adult, TestFamily.adult))
        assertTrue(keepsListsOf(TestFamily.otherAdult, TestFamily.otherKid))
        assertFalse(keepsListsOf(TestFamily.adult, TestFamily.kid)) // A child with the app keeps their own.
        assertFalse(keepsListsOf(TestFamily.adult, TestFamily.otherKid))
        assertEquals(listOf("adult2", "kid2"), keptMembers(TestFamily.otherAdult, family).map { it.id })
    }

    @Test
    fun `the surprise of a beneficiary lasts until the reveal`() {
        val mine = list("l", "adult1")
        assertTrue(isKeptFrom(mine, TestFamily.adult))
        assertFalse(isKeptFrom(mine, TestFamily.otherAdult))
        assertFalse(isKeptFrom(mine.copy(revealed = true), TestFamily.adult))
    }

    @Test
    fun `only the keeper's side adds items the beneficiary sees`() {
        val mine = list("l", "adult1")
        assertTrue(addsVisibleItem(mine, TestFamily.adult, family))
        assertFalse(addsVisibleItem(mine, TestFamily.otherAdult, family))
        assertFalse(addsVisibleItem(list("s", "adult1", GiftListKind.Surprise, creator = "adult2"), TestFamily.otherAdult, family))
        assertTrue(addsVisibleItem(list("k", "kid2", creator = "adult2"), TestFamily.otherAdult, family))
    }

    @Test
    fun `a child reserves alone, never a joint gift`() {
        val mine = list("l", "adult1")
        assertTrue(canReserve(mine, TestFamily.kid))
        assertFalse(canShare(mine, TestFamily.kid))
        assertTrue(canShare(mine, TestFamily.otherAdult))
        assertFalse(canReserve(mine, TestFamily.adult))
        assertFalse(canReserve(mine.copy(revealed = true), TestFamily.otherAdult))
    }

    @Test
    fun `viewers follow the rules of each kind of list`() {
        val own = normalizedViewers(GiftListKind.Own, TestFamily.kid, TestFamily.kid, setOf("adult2"))
        assertEquals(listOf("adult1", "adult2", "kid1"), own) // The child's responsible adult is always there.
        val surprise = normalizedViewers(GiftListKind.Surprise, TestFamily.adult, TestFamily.otherAdult, setOf("adult1", "admin1"))
        assertEquals(listOf("admin1", "adult2"), surprise)
    }

    @Test
    fun `the tab shows the member, then their children, then the others who have lists`() {
        val lists = listOf(
            list("w", "adult1", GiftListKind.Wishes, viewers = emptyList()),
            list("own", "admin1", viewers = listOf("admin1", "adult2")),
            list("old", "adult2", revealed = true),
        )
        val state = giftsState(family, lists, "adult2", today) as GiftsUiState.Success
        assertEquals(listOf("adult2", "kid2", "admin1", "adult1"), state.sections.map { it.member.id })
        assertTrue(state.sections[1].keptByMe)
        assertEquals("w", state.sections[3].wishes?.id)
        assertEquals(listOf("old"), state.archived.map { it.first.id })
        assertTrue(state.canPrepareSurprise)
    }

    @Test
    fun `amounts and links typed by a member are understood`() {
        assertEquals(19.9, parseEuros("19,90 €")!!, 0.001)
        assertEquals(30.0, parseEuros(" 30 ")!!, 0.001)
        assertNull(parseEuros("trente"))
        assertNull(parseEuros("-2"))
        assertEquals("https://example.com/a", normalizeLink("example.com/a"))
        assertNull(normalizeLink("pas un lien"))
        assertEquals(
            setOf(GiftItemFormError.TitleMissing, GiftItemFormError.BadPrice),
            validateItem(GiftItemForm(title = " ", price = "abc")),
        )
    }

    @Test
    fun `the beneficiary never asks the server for the reservations`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult1", "l", today = flowOf(today))
        val state = vm.uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success
        assertTrue(state.keptFromMe)
        assertFalse(state.canReserve)
        assertEquals(listOf(true), gifts.itemQueries)
        assertEquals(0, gifts.claimQueries)
    }

    @Test
    fun `a viewer sees who reserved and the parts of a joint gift`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        gifts.claims.value = listOf(GiftClaim("i1", ClaimType.Shared, "admin1", "Admin1"))
        gifts.shares.value = mapOf("i1" to listOf(GiftShare("adult2", "Adult2", 20.0, false)))
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", today = flowOf(today))
        val state = vm.uiState.first { it is GiftListUiState.Success && it.items.isNotEmpty() } as GiftListUiState.Success
        val claim = state.items.single().claim!!
        assertEquals("Admin1", claim.holderName)
        assertEquals(20.0, claim.myShare!!.amount, 0.001)
        assertEquals(listOf(false), gifts.itemQueries)
    }

    @Test
    fun `an empty « Mes envies » shows for the member who keeps it`() = runTest {
        val vm = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult2", "wishes-kid2", today = flowOf(today))
        val state = vm.uiState.first { it !is GiftListUiState.Loading }
        assertTrue(state is GiftListUiState.Success && state.list.kind == GiftListKind.Wishes && state.canAddItems)
        val other = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-kid2", today = flowOf(today))
        assertEquals(GiftListUiState.Missing, other.uiState.first { it !is GiftListUiState.Loading })
    }
}

/** In-memory gift lists: one list, one item, and the queries the screens made. */
class FakeGifts(list: GiftList?) : GiftRepository {
    val list = MutableStateFlow(list)
    val items = MutableStateFlow(
        listOf(GiftItem("i1", "Un livre", null, 15.0, null, "adult1", "Adult1", visibleToBeneficiary = true)),
    )
    val claims = MutableStateFlow<List<GiftClaim>>(emptyList())
    val shares = MutableStateFlow<Map<String, List<GiftShare>>>(emptyMap())
    val itemQueries = mutableListOf<Boolean>()
    var claimQueries = 0

    override fun observeVisibleLists(memberId: String): Flow<List<GiftList>> = list.map { listOfNotNull(it) }
    override fun observeList(listId: String): Flow<GiftList?> = list
    override fun observeItems(listId: String, hiddenFromMe: Boolean): Flow<List<GiftItem>> {
        itemQueries += hiddenFromMe
        return items
    }
    override fun observeClaims(listId: String): Flow<List<GiftClaim>> {
        claimQueries++
        return claims
    }
    override fun observeShares(listId: String, itemIds: List<String>): Flow<Map<String, List<GiftShare>>> =
        shares.map { all -> all.filterKeys { it in itemIds } }
    override fun newListId(): String = "new-list"
    override fun newItemId(listId: String): String = "new-item"
    override suspend fun saveList(list: GiftList, isNew: Boolean) = Unit
    override suspend fun deleteList(listId: String) = Unit
    override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) = Unit
    override suspend fun deleteItem(listId: String, itemId: String) = Unit
    override suspend fun claim(listId: String, claim: GiftClaim) = Unit
    override suspend fun release(listId: String, itemId: String) = Unit
    override suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean) = Unit
    override suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean) = Unit
    override suspend fun leaveShare(listId: String, itemId: String, memberId: String) = Unit
}
