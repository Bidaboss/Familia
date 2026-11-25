package fr.familia.app.feature.gifts

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.ReadOnlyGiftRepository
import fr.familia.app.core.viewas.ViewAsGuard
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.GiftListActions
import fr.familia.app.feature.gifts.detail.GiftListScreen
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The gift steps of the demo of 21 November (docs/scenario-demo.md), on the real screens and
 * ViewModels: a wish added, reserved from another account, a joint gift with a part, the person
 * who is celebrated seeing none of it, and « Voir comme » that person. [DemoGiftStore] answers each
 * account as the rules of firestore.rules do. Fictional family only (TestFamily).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class GiftsDemoJourneyTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 21)

    /** adult1 is the person celebrated; adult2 and admin1 give. */
    private val fete = "adult1"
    private val wishes = wishesListId(fete)
    private val store = DemoGiftStore()

    private fun listVm(reader: String, repo: GiftRepository = store.repoFor(reader), viewing: String = reader) =
        GiftListViewModel(repo, FakeMembers(), viewing, wishes, today = flowOf(today), readerId = reader)

    private fun show(vm: GiftListViewModel) {
        compose.setContent {
            FamiliaTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                GiftListScreen(
                    state,
                    GiftListActions(onTake = vm::take, onOpenJointGift = vm::openJointGift, onSetShare = vm::setShare),
                )
            }
        }
        compose.waitUntil(TIMEOUT) { vm.uiState.value is GiftListUiState.Success }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertNoText(text: String) {
        compose.waitForIdle()
        assertEquals("« $text » must not show", 0, compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)
    }

    /** Adds an item through the real form, as [author], on the wishes of the person celebrated. */
    private fun addItem(author: String, title: String) {
        val vm = GiftItemEditViewModel(store.repoFor(author), FakeMembers(), author, wishes, null)
        idleUntil { vm.uiState.value is GiftItemEditUiState.Editing }
        vm.update { it.copy(title = title, price = "30") }
        vm.save()
        idleUntil { vm.uiState.value == GiftItemEditUiState.Done }
    }

    /** Before any screen is shown: runs the main looper until [done]. */
    private fun idleUntil(done: () -> Boolean) {
        val end = System.currentTimeMillis() + TIMEOUT
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > end) fail("not reached in time")
            Thread.sleep(5)
        }
    }

    @Test
    fun `a wish added shows on my own list`() {
        addItem(fete, "Une écharpe")
        show(listVm(fete))
        waitForText("Une écharpe")
        compose.onNodeWithText("Une écharpe").assertIsDisplayed()
    }

    @Test
    fun `another member reserves it from their own account`() {
        addItem(fete, "Une écharpe")
        show(listVm("adult2"))
        waitForText("Une écharpe")
        // The list of wishes (correctif E3): a row opens the sheet of the wish, where one takes it.
        compose.onNodeWithText("Une écharpe").performClick()
        waitForText("Je le prends")
        compose.onNodeWithText("Je le prends").performClick()
        waitForText("Tu l'as réservé")
        assertEquals(ClaimType.Solo, store.claimsOf(wishes).single().type)
    }

    @Test
    fun `the person celebrated sees neither the reservation, nor the idea, nor the joint gift`() {
        addItem(fete, "Une écharpe")
        addItem(fete, "Un livre")
        // A surprise idea added by someone else, a reservation and a joint gift with two parts.
        addItem("adult2", "Un puzzle")
        val items = store.itemsOf(wishes)
        store.seedClaim(wishes, GiftClaim(items.first { it.title == "Une écharpe" }.id, ClaimType.Solo, "adult2", "Adult2"))
        val book = items.first { it.title == "Un livre" }.id
        store.seedClaim(wishes, GiftClaim(book, ClaimType.Shared, "admin1", "Admin1"))
        store.seedShare(wishes, book, GiftShare("admin1", "Admin1", 20.0, received = false))

        show(listVm(fete))
        waitForText("Un livre")
        compose.onNodeWithText("Une écharpe").assertIsDisplayed()
        assertNoText("Un puzzle")
        assertNoText("Réservé")
        assertNoText("Cadeau commun")
        assertNoText("parts annoncées")
        // The rules refuse the reservations to the person: the screen never even asks.
        assertEquals(0, store.claimReadsBy(fete))
    }

    @Test
    fun `a joint gift gathers a part, seen by those who give`() {
        addItem(fete, "Un livre")
        val giver = listVm("adult2")
        show(giver)
        waitForText("Un livre")
        val book = store.itemsOf(wishes).single().id
        compose.onNodeWithText("Un livre").performClick()
        giver.openJointGift(book)
        waitForText("Cadeau commun, organisé par Adult2", substring = true)
        giver.setShare(book, 25.0)
        waitForText("parts annoncées", substring = true)
        assertEquals(25.0, store.claimsOf(wishes).single().sharesTotal, 0.001)
    }

    @Test
    fun `« Voir comme » the person celebrated shows their list as they see it`() {
        addItem(fete, "Une écharpe")
        addItem("adult2", "Un puzzle")
        store.seedClaim(wishes, GiftClaim(store.itemsOf(wishes).first { it.title == "Une écharpe" }.id, ClaimType.Solo, "adult2", "Adult2"))

        // The admin principal reads, with their own rights, through the read-only repository.
        val viewAs = ReadOnlyGiftRepository(store.repoFor("admin1"), ViewAsGuard(), adminId = "admin1", viewedId = fete)
        show(listVm("admin1", viewAs, viewing = fete))
        waitForText("Une écharpe")
        assertNoText("Un puzzle")
        assertNoText("Réservé")
        assertNoText("Je le prends")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}

/**
 * In-memory gift lists shared by every account, answering each one as firestore.rules does: the
 * person of a list never reads its reservations or parts before the reveal, and reads its items
 * only with the filter « visibleToBeneficiary » (the query the rules can prove).
 */
class DemoGiftStore {
    private val lists = MutableStateFlow<Map<String, GiftList>>(emptyMap())
    private val items = MutableStateFlow<Map<String, List<GiftItem>>>(emptyMap())
    private val claims = MutableStateFlow<Map<String, List<GiftClaim>>>(emptyMap())
    private val shares = MutableStateFlow<Map<Pair<String, String>, List<GiftShare>>>(emptyMap())
    private val claimReads = mutableMapOf<String, Int>()
    private var nextId = 0

    fun itemsOf(listId: String): List<GiftItem> = items.value[listId].orEmpty()
    fun claimsOf(listId: String): List<GiftClaim> = claims.value[listId].orEmpty()
    fun claimReadsBy(memberId: String): Int = claimReads[memberId] ?: 0

    fun seedClaim(listId: String, claim: GiftClaim) {
        claims.update { it + (listId to (it[listId].orEmpty().filterNot { c -> c.itemId == claim.itemId } + claim)) }
    }

    fun seedShare(listId: String, itemId: String, share: GiftShare) {
        val key = listId to itemId
        shares.update { it + (key to (it[key].orEmpty().filterNot { s -> s.memberId == share.memberId } + share)) }
        // The server keeps the total of the parts on the reservation (updateSharesTotal).
        val parts = shares.value[key].orEmpty()
        claims.update { all ->
            all + (listId to all[listId].orEmpty().map { c ->
                if (c.itemId == itemId) c.copy(sharesTotal = parts.sumOf { it.amount }, sharesCount = parts.size) else c
            })
        }
    }

    fun repoFor(reader: String): GiftRepository = object : GiftRepository {
        private fun kept(list: GiftList?) = list != null && list.beneficiaryId == reader && !list.revealed

        private fun sees(list: GiftList) = when (list.kind) {
            GiftListKind.Wishes -> true
            GiftListKind.Ideas -> list.beneficiaryId != reader
            else -> reader in list.viewerIds || (list.beneficiaryId == reader && list.revealed)
        }

        private fun denied(): Nothing = throw IllegalStateException("PERMISSION_DENIED")

        override fun observeVisibleLists(memberId: String): Flow<List<GiftList>> = lists.map { all -> all.values.filter(::sees) }

        override fun observeList(listId: String): Flow<GiftList?> = lists.map { all ->
            all[listId]?.also { if (!sees(it)) denied() }
        }

        override fun observeItems(listId: String, hiddenFromMe: Boolean): Flow<List<GiftItem>> =
            combine(lists, items) { all, byList ->
                if (kept(all[listId]) && !hiddenFromMe) denied()
                byList[listId].orEmpty().filter { !hiddenFromMe || it.visibleToBeneficiary }
            }

        override fun observeClaims(listId: String): Flow<List<GiftClaim>> {
            claimReads[reader] = claimReadsBy(reader) + 1
            if (kept(lists.value[listId])) return flow { denied() }
            return claims.map { it[listId].orEmpty() }
        }

        override fun observeShares(listId: String, itemIds: List<String>, memberId: String, allOf: Set<String>): Flow<Map<String, List<GiftShare>>> {
            if (kept(lists.value[listId])) return flow { denied() }
            return shares.map { all ->
                itemIds.associateWith { id ->
                    all[listId to id].orEmpty().let { parts -> if (id in allOf) parts else parts.filter { it.memberId == memberId } }
                }
            }
        }

        override fun newListId(): String = "list-${nextId++}"
        override fun newItemId(listId: String): String = "item-${nextId++}"

        override suspend fun saveList(list: GiftList, isNew: Boolean) {
            lists.update { it + (list.id to list) }
        }

        override suspend fun deleteList(listId: String) {
            lists.update { it - listId }
        }

        override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) {
            items.update { it + (listId to (it[listId].orEmpty().filterNot { i -> i.id == item.id } + item)) }
        }

        override suspend fun deleteItem(listId: String, itemId: String) {
            items.update { it + (listId to it[listId].orEmpty().filterNot { i -> i.id == itemId }) }
        }

        override suspend fun claim(listId: String, claim: GiftClaim) {
            if (kept(lists.value[listId])) denied()
            seedClaim(listId, claim)
        }

        override suspend fun release(listId: String, itemId: String) {
            claims.update { it + (listId to it[listId].orEmpty().filterNot { c -> c.itemId == itemId }) }
        }

        override suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean) {
            if (kept(lists.value[listId])) denied()
            seedShare(listId, itemId, share)
        }

        override suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean) = Unit
        override suspend fun leaveShare(listId: String, itemId: String, memberId: String) = Unit
        override suspend fun setClaimOccasion(listId: String, itemId: String, occasion: GiftOccasion) = Unit
    }
}
