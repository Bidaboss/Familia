package fr.familia.app.feature.gifts

import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.SharedWish
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * « Cadeau commun avec… » (PRD, récit 73). Fictional family of TestFamily: admin1, adult1 and kid1 in
 * h1, adult2 and kid2 in h2.
 */
class SharedWishTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 19)

    private fun wishes(beneficiary: String, co: List<String> = emptyList()) =
        GiftList(
            "wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary,
            coBeneficiaryIds = co,
        )

    private val shared = GiftItem("tent", "Une tente", null, 120.0, null, "adult1", "Adult1", visibleToBeneficiary = true, coBeneficiaryId = "admin1")
    private val plain = GiftItem("book", "Un livre", null, 15.0, null, "adult1", "Adult1", visibleToBeneficiary = true)

    /** Records which reservations are read one by one, and shares [sharedWithAdmin] with admin1. */
    private class SharingGifts(
        list: GiftList,
        private val fake: FakeGifts = FakeGifts(list),
        private val sharedWithAdmin: List<SharedWish> = emptyList(),
    ) : GiftRepository by fake {
        val read = mutableListOf<List<String>>()
        val wholeList get() = fake.claimQueries
        val items get() = fake.items
        val claims get() = fake.claims
        val saved get() = fake.savedItems

        override fun observeClaimsOf(listId: String, itemIds: List<String>): Flow<List<GiftClaim>> {
            read += itemIds
            return flowOf(fake.claims.value.filter { it.itemId in itemIds })
        }

        override fun observeWishesSharedWith(memberId: String, listIds: List<String>): Flow<List<SharedWish>> =
            flowOf(if (memberId == "admin1" && "wishes-adult1" in listIds) sharedWithAdmin else emptyList())
    }

    private fun listGifts(): SharingGifts {
        val gifts = SharingGifts(wishes("adult1", co = listOf("admin1")))
        gifts.items.value = listOf(shared, plain)
        gifts.claims.value = listOf(
            GiftClaim("tent", ClaimType.Solo, "adult2", "Adult2"),
            GiftClaim("book", ClaimType.Solo, "adult2", "Adult2"),
        )
        return gifts
    }

    private suspend fun listState(gifts: GiftRepository, meId: String, listId: String): GiftListUiState.Success =
        GiftListViewModel(gifts, FakeMembers(), meId, listId, today = flowOf(today))
            .uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success

    @Test
    fun `whoever shares a wish never reads its reservation, but reads the others one by one`() = runTest {
        val gifts = listGifts()
        val state = listState(gifts, "admin1", "wishes-adult1")
        val tent = state.items.first { it.item.id == "tent" }
        assertNull(tent.claim)
        assertTrue(tent.sharedWithMe)
        assertNotNull(state.items.first { it.item.id == "book" }.claim)
        // Never the whole list of reservations, and never the one of the shared wish.
        assertEquals(0, gifts.wholeList)
        assertTrue(gifts.read.all { "tent" !in it })
    }

    @Test
    fun `whoever shares a wish edits it, the rest of the family sees who reserved it`() = runTest {
        val mine = listState(listGifts(), "admin1", "wishes-adult1").items.first { it.item.id == "tent" }
        assertTrue(mine.canEdit)
        val other = listState(listGifts(), "adult2", "wishes-adult1").items.first { it.item.id == "tent" }
        assertFalse(other.canEdit)
        assertFalse(other.sharedWithMe)
        assertEquals("Adult2", other.claim?.holderName)
        assertEquals("Admin1", other.sharedWithName)
    }

    @Test
    fun `the wish shows in the list of the other, under « Cadeaux communs »`() = runTest {
        val gifts = SharingGifts(wishes("admin1"), sharedWithAdmin = listOf(SharedWish("wishes-adult1", shared)))
        gifts.items.value = emptyList()
        val state = listState(gifts, "adult2", "wishes-admin1")
        assertEquals(listOf("tent"), state.sharedWishes.map { it.item.id })
        assertEquals("wishes-adult1", state.sharedWishes.single().listId)
        assertEquals("Adult1", state.sharedWishes.single().ownerName)
    }

    private suspend fun editing(vm: GiftItemEditViewModel): GiftItemEditUiState.Editing =
        vm.uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing

    @Test
    fun `a wish is shared with a member of the household only, children included`() = runTest {
        val vm = GiftItemEditViewModel(FakeGifts(wishes("adult1")), FakeMembers(), "adult1", "wishes-adult1", null)
        assertEquals(setOf("admin1", "kid1"), editing(vm).coChoices.map { it.memberId }.toSet())
        val other = GiftItemEditViewModel(FakeGifts(wishes("adult2")), FakeMembers(), "adult2", "wishes-adult2", null)
        assertEquals(listOf("kid2"), editing(other).coChoices.map { it.memberId })
    }

    @Test
    fun `the chosen member is saved with the wish, « Personne » saves none`() = runTest {
        val gifts = SharingGifts(wishes("adult1"))
        val vm = GiftItemEditViewModel(gifts, FakeMembers(), "adult1", "wishes-adult1", null)
        editing(vm)
        vm.update { it.copy(title = "Une tente", coBeneficiaryId = "admin1") }
        vm.save()
        vm.uiState.first { it is GiftItemEditUiState.Done }
        assertEquals("admin1", gifts.saved.last().coBeneficiaryId)

        val alone = SharingGifts(wishes("adult1"))
        val again = GiftItemEditViewModel(alone, FakeMembers(), "adult1", "wishes-adult1", null)
        editing(again)
        again.update { it.copy(title = "Un livre", coBeneficiaryId = "adult2") }
        again.save()
        again.uiState.first { it is GiftItemEditUiState.Done }
        // Someone of another household is never saved.
        assertNull(alone.saved.last().coBeneficiaryId)
    }

    @Test
    fun `no shared wish on the ideas of the family`() = runTest {
        val ideas = GiftList("ideas-adult1", GiftListKind.Ideas, "Idées de la famille", null, null, "adult1", emptyList(), "admin1", "Admin1")
        val vm = GiftItemEditViewModel(FakeGifts(ideas), FakeMembers(), "admin1", "ideas-adult1", null)
        assertTrue(editing(vm).coChoices.isEmpty())
    }

    @Test
    fun `once shared, the author no longer changes who shares the wish`() = runTest {
        val gifts = SharingGifts(wishes("adult1", co = listOf("admin1")))
        gifts.items.value = listOf(shared)
        val vm = GiftItemEditViewModel(gifts, FakeMembers(), "adult1", "wishes-adult1", "tent")
        val state = editing(vm)
        assertTrue(state.coChoices.isEmpty())
        assertEquals("Admin1", state.sharedWith)
        vm.update { it.copy(price = "99", coBeneficiaryId = null) }
        vm.save()
        vm.uiState.first { it is GiftItemEditUiState.Done }
        assertEquals("admin1", gifts.saved.last().coBeneficiaryId)
    }

    @Test
    fun `whoever shares the wish edits it without changing who shares it, and cannot remove it`() = runTest {
        val gifts = SharingGifts(wishes("adult1", co = listOf("admin1")))
        gifts.items.value = listOf(shared)
        val vm = GiftItemEditViewModel(gifts, FakeMembers(), "admin1", "wishes-adult1", "tent")
        val state = editing(vm)
        assertEquals("Adult1", state.sharedByOther)
        assertTrue(state.coChoices.isEmpty())
        vm.update { it.copy(price = "99") }
        vm.save()
        vm.uiState.first { it is GiftItemEditUiState.Done }
        assertEquals("admin1", gifts.saved.last().coBeneficiaryId)
        assertEquals("adult1", gifts.saved.last().authorId)
    }
}
