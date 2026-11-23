package fr.familia.app.feature.gifts

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.LinkPreview
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.detail.linkHost
import fr.familia.app.feature.gifts.edit.GiftListEditUiState
import fr.familia.app.feature.gifts.edit.GiftListEditViewModel
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import fr.familia.app.feature.gifts.item.GiftItemForm
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.item.normalizeLink
import fr.familia.app.feature.gifts.item.validateItem
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    fun `only my own wishes go out of the app, never the ideas of the family nor a list of an occasion`() {
        val mine = list("wishes-adult1", "adult1", kind = GiftListKind.Wishes, viewers = emptyList())
        assertTrue(canShareWishesOutside(mine, TestFamily.adult, family))
        assertFalse(canShareWishesOutside(mine, TestFamily.otherAdult, family)) // Someone else's.
        assertFalse(canShareWishesOutside(mine.copy(revealed = true), TestFamily.adult, family))
        val ideas = list("ideas-adult1", "adult1", kind = GiftListKind.Ideas, viewers = emptyList(), creator = "adult2")
        assertFalse(canShareWishesOutside(ideas, TestFamily.adult, family))
        assertFalse(canShareWishesOutside(ideas, TestFamily.otherAdult, family))
        assertFalse(canShareWishesOutside(list("noel", "adult1"), TestFamily.adult, family))
    }

    @Test
    fun `a child with the app shares their own wishes, and their adults do too`() {
        val kidWishes = list("wishes-kid1", "kid1", kind = GiftListKind.Wishes, viewers = emptyList())
        assertTrue(canShareWishesOutside(kidWishes, TestFamily.kid, family))
        assertTrue(canShareWishesOutside(kidWishes, TestFamily.adult, family))
        assertFalse(canShareWishesOutside(kidWishes, TestFamily.otherAdult, family))
        assertFalse(canShareWishesOutside(list("wishes-adult1", "adult1", kind = GiftListKind.Wishes, viewers = emptyList()), TestFamily.kid, family))
    }

    @Test
    fun `the wishes of a child without the app are shared by their adults only`() {
        val babyWishes = list("wishes-kid2", "kid2", kind = GiftListKind.Wishes, viewers = emptyList())
        assertTrue(canShareWishesOutside(babyWishes, TestFamily.otherAdult, family))
        assertFalse(canShareWishesOutside(babyWishes, TestFamily.adult, family))
    }

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
    fun `the others are sorted by household, as in Famille, adults before children`() {
        // Retour d'essai du 17/11 : « range les membres par foyers ». « Foyer Deux » comes before « Foyer Un ».
        val state = giftsState(family, emptyList(), "admin1", today) as GiftsUiState.Success
        assertEquals(listOf("admin1", "adult2", "kid2", "adult1", "kid1"), state.sections.map { it.member.id })
        assertEquals(listOf("Foyer Un", "Foyer Deux", "Foyer Deux", "Foyer Un", "Foyer Un"), state.sections.map { it.householdName })
    }

    @Test
    fun `the tab shows the member, then their children, then everyone else`() {
        val lists = listOf(list("w", "adult1", GiftListKind.Wishes, viewers = emptyList()))
        val state = giftsState(family, lists, "adult2", today) as GiftsUiState.Success
        assertEquals(listOf("adult2", "kid2", "admin1", "adult1", "kid1"), state.sections.map { it.member.id })
        assertTrue(state.sections[1].keptByMe)
        assertEquals("w", state.sections.first { it.member.id == "adult1" }.wishes?.id)
        assertTrue(state.canPrepareSurprise)
    }

    @Test
    fun `everyone shows with their wishes and the ideas of the family, and no list for an occasion`() {
        val lists = listOf(
            list("w", "adult1", GiftListKind.Wishes, viewers = emptyList()),
            list("own", "admin1", viewers = listOf("admin1", "adult2")),
            list("old", "adult2", revealed = true),
            list("ideas-adult1", "adult1", GiftListKind.Ideas, viewers = emptyList(), creator = "admin1"),
        )
        val state = giftsState(family, lists, "adult2", today) as GiftsUiState.Success
        assertEquals(family.members.size, state.sections.size)
        assertTrue(state.archived.isEmpty())
        assertEquals("ideas-adult1", state.sections.first { it.member.id == "adult1" }.ideas?.id)
    }

    @Test
    fun `the ideas of the family open empty for the others, never for the person`() = runTest {
        val other = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult2", "ideas-adult1", today = flowOf(today))
        val state = other.uiState.first { it !is GiftListUiState.Loading } as GiftListUiState.Success
        assertEquals(GiftListKind.Ideas, state.list.kind)
        assertTrue(state.canAddItems)
        assertFalse(state.keptFromMe)
        val mine = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult1", "ideas-adult1", today = flowOf(today))
        assertEquals(GiftListUiState.Missing, mine.uiState.first { it !is GiftListUiState.Loading })
    }

    @Test
    fun `the card of the wishes counts what its person sees, never the ideas of the family`() {
        val lists = listOf(
            list("wishes-adult1", "adult1", GiftListKind.Wishes, viewers = emptyList()),
            list("ideas-adult1", "adult1", GiftListKind.Ideas, viewers = emptyList(), creator = "admin1"),
        )
        fun item(id: String, visible: Boolean = true) = GiftItem(id, id, null, null, null, "adult1", "Adult1", visible)
        val base = (giftsState(family, lists, "adult2", today) as GiftsUiState.Success).copy(
            listItems = mapOf(
                "wishes-adult1" to listOf(item("noel"), item("anniv"), item("libre"), item("cachee", visible = false)),
                "ideas-adult1" to listOf(item("idee")),
            ),
        )
        val shown = withWishPreviews(base) as GiftsUiState.Success
        assertEquals(3, shown.wishPreviews.getValue("wishes-adult1").count)
        assertTrue("ideas-adult1" !in shown.wishPreviews)
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
    fun `the card of a wishes list tells how many and the first titles, as the reader may see them`() = runTest {
        val items = listOf("Vélo", "un atlas", "Lego", "Puzzle").mapIndexed { i, t ->
            GiftItem("i$i", t, null, null, null, "adult1", "Adult1", visibleToBeneficiary = true)
        }
        assertEquals(WishPreview(4, listOf("Lego", "Puzzle", "un atlas")), wishPreview(items))
        assertEquals(WishPreview(0, emptyList()), wishPreview(emptyList()))
        // A surprise idea added by someone else enters neither the number nor the titles.
        val surprise = GiftItem("s", "Montre", null, null, null, "adult2", "Adult2", visibleToBeneficiary = false)
        assertEquals(WishPreview(4, listOf("Lego", "Puzzle", "un atlas")), wishPreview(items + surprise))

        val gifts = FakeGifts(list("wishes-adult1", "adult1", kind = GiftListKind.Wishes, viewers = emptyList()))
        val other = GiftsViewModel(gifts, FakeMembers(), "adult2", today = flowOf(today))
        val seen = other.uiState.first { it is GiftsUiState.Success && it.wishPreviews.isNotEmpty() } as GiftsUiState.Success
        assertEquals(1, seen.wishPreviews.getValue("wishes-adult1").count)
        // Even for someone else, only what the beneficiary sees: the overview is shown to the whole family.
        assertEquals(listOf(true), gifts.itemQueries)
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
    fun `in « Voir comme », a list for the admin reads as theirs, without reservations, and says why`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        // adult1 is the admin principal looking as adult2: adult1 still reads, and is the beneficiary.
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", today = flowOf(today), readerId = "adult1")
        val state = vm.uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success
        assertTrue(state.keptFromReader)
        assertFalse(state.keptFromMe)
        assertFalse(state.canReserve)
        assertEquals(listOf(true), gifts.itemQueries)
        assertEquals(0, gifts.claimQueries)
        // Someone else's list reads as usual.
        val other = FakeGifts(list("m", "adult2", viewers = listOf("adult1", "adult2")))
        val usual = GiftListViewModel(other, FakeMembers(), "adult1", "m", today = flowOf(today), readerId = "adult1")
        assertFalse((usual.uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success).keptFromReader)
    }

    @Test
    fun `a viewer sees who reserved and the parts of a joint gift`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        gifts.claims.value = listOf(GiftClaim("i1", ClaimType.Shared, "admin1", "Admin1", sharesTotal = 45.0, sharesCount = 2))
        gifts.shares.value = mapOf("i1" to listOf(GiftShare("adult2", "Adult2", 20.0, false), GiftShare("admin1", "Admin1", 25.0, false)))
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", today = flowOf(today))
        val state = vm.uiState.first { it is GiftListUiState.Success && it.items.isNotEmpty() } as GiftListUiState.Success
        val claim = state.items.single().claim!!
        assertEquals("Admin1", claim.holderName)
        assertEquals(20.0, claim.myShare!!.amount, 0.001)
        // A participant sees their own part and the total, never the part of another (PRD rév. 151).
        assertEquals(listOf("adult2"), claim.shares.map { it.memberId })
        assertEquals(45.0, claim.total, 0.001)
        val holder = GiftListViewModel(gifts, FakeMembers(), "admin1", "l", today = flowOf(today))
        val held = holder.uiState.first { it is GiftListUiState.Success && it.items.isNotEmpty() } as GiftListUiState.Success
        assertEquals(2, held.items.single().claim!!.shares.size)
        // One query per screen, and neither hides the surprise ideas.
        assertEquals(listOf(false, false), gifts.itemQueries)
    }

    @Test
    fun `a shop link names its site`() {
        assertEquals("amazon.fr", linkHost("https://www.amazon.fr/dp/123"))
        assertEquals("fnac.com", linkHost("https://fnac.com"))
        assertNull(linkHost("pas un lien"))
    }

    @Test
    fun `a joint gift whose parts are refused keeps the list on screen`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        gifts.claims.value = listOf(GiftClaim("i1", ClaimType.Shared, "adult2", "Adult2"))
        gifts.refuseShares = true
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", today = flowOf(today))
        val state = vm.uiState.first { it !is GiftListUiState.Loading }
        assertTrue(state is GiftListUiState.Success)
        val claim = (state as GiftListUiState.Success).items.single().claim!!
        assertEquals(ClaimType.Shared, claim.type)
        assertTrue(claim.shares.isEmpty())
    }

    @Test
    fun `a joint gift opened before the server kept its total shows at least my part`() = runTest {
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        gifts.claims.value = listOf(GiftClaim("i1", ClaimType.Shared, "admin1", "Admin1"))
        gifts.shares.value = mapOf("i1" to listOf(GiftShare("adult2", "Adult2", 20.0, false), GiftShare("admin1", "Admin1", 25.0, false)))
        val vm = GiftListViewModel(gifts, FakeMembers(), "adult2", "l", today = flowOf(today))
        val state = vm.uiState.first { it is GiftListUiState.Success && it.items.isNotEmpty() } as GiftListUiState.Success
        assertEquals(20.0, state.items.single().claim!!.total, 0.001)
    }

    @Test
    fun `an idea being written survives Android closing the app`() = runTest {
        val saved = SavedStateHandle()
        val gifts = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        val first = GiftItemEditViewModel(gifts, FakeMembers(), "adult2", "l", null, saved = saved)
        first.uiState.first { it is GiftItemEditUiState.Editing }
        first.update { it.copy(title = "Un vélo", price = "120") }
        val again = GiftItemEditViewModel(gifts, FakeMembers(), "adult2", "l", null, saved = saved)
        val state = again.uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing
        assertEquals(GiftItemForm(title = "Un vélo", price = "120"), state.form)
        assertTrue(state.hasChanges)
    }

    @Test
    fun `a photo goes to « shared » for a wish and to « hidden » for an idea kept from the person`() = runTest {
        val sent = mutableListOf<String>()
        val photos = SinglePhotoSaver(
            uploader = { _, photo, _ -> sent += photo },
            storage = object : PhotoStorage {
                override suspend fun upload(path: String, jpeg: ByteArray) = Unit
                override suspend fun downloadUrl(path: String) = path
                override suspend fun delete(path: String) = Unit
            },
        )
        val wishes = FakeGifts(list("wishes-adult1", "adult1", kind = GiftListKind.Wishes))
        val mine = GiftItemEditViewModel(wishes, FakeMembers(), "adult1", "wishes-adult1", null, photos = photos)
        assertTrue((mine.uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing).showPhoto)
        mine.update { it.copy(title = "Un vélo", photo = PhotoEdit.Picked("content://photo/1")) }
        mine.save()
        mine.uiState.first { it == GiftItemEditUiState.Done }
        assertTrue(wishes.savedItems.single().photo!!.startsWith("giftLists/wishes-adult1/shared/adult1/"))

        val surprise = FakeGifts(list("l", "adult1", viewers = listOf("adult1", "adult2")))
        val idea = GiftItemEditViewModel(surprise, FakeMembers(), "adult2", "l", null, photos = photos)
        idea.uiState.first { it is GiftItemEditUiState.Editing }
        idea.update { it.copy(title = "Une montre", photo = PhotoEdit.Picked("content://photo/2")) }
        idea.save()
        idea.uiState.first { it == GiftItemEditUiState.Done }
        assertTrue(surprise.savedItems.single().photo!!.startsWith("giftLists/l/hidden/adult2/"))
        assertEquals(2, sent.size)
    }

    @Test
    fun `without « Plus de photos » a wish has no photo to add`() = runTest {
        val vm = GiftItemEditViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-adult1", null)
        assertFalse((vm.uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing).showPhoto)
    }

    @Test
    fun `a link shared from another app starts a new wish, to save or drop`() = runTest {
        // adult1 has no « Mes envies » yet: the first wish creates the list.
        val shared = GiftItemForm(title = "Écharpe rouge", url = "https://boutique.example/echarpe")
        val vm = GiftItemEditViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-adult1", null, shared = shared)
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing } as GiftItemEditUiState.Editing
        assertEquals(shared, state.form)
        assertTrue(state.isNew)
        assertTrue("leaving asks before dropping the link", state.hasChanges)
    }

    @Test
    fun `the shop page fills what the member has not typed, and its picture goes with the link`() = runTest {
        val shared = GiftItemForm(title = "", url = "https://boutique.example/echarpe")
        val previews = LinkPreviewRepository { LinkPreview("Écharpe rouge", "https://cdn.boutique.example/e.jpg", 24.9) }
        val vm = GiftItemEditViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-adult1", null, shared = shared, previews = previews)
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing && !it.previewLoading } as GiftItemEditUiState.Editing
        assertEquals("Écharpe rouge", state.form.title)
        assertEquals("24,90", state.form.price)
        assertEquals("https://cdn.boutique.example/e.jpg", state.imageUrl)

        vm.update { it.copy(url = "https://autre.example/x") }
        assertNull((vm.uiState.value as GiftItemEditUiState.Editing).imageUrl)
    }

    @Test
    fun `a list draft is checked again against the family`() = runTest {
        val saved = SavedStateHandle(
            mapOf(
                "draft.title" to "Noël chez Mamie",
                "draft.occasion" to "Christmas",
                "draft.date" to LocalDate.of(2026, 12, 25).toEpochDay(),
                "draft.beneficiary" to "adult2",
                "draft.viewers" to arrayListOf("adult1", "gone"),
            ),
        )
        val vm = GiftListEditViewModel(FakeGifts(null), FakeMembers(), "adult2", null, saved = saved)
        val state = vm.uiState.first { it is GiftListEditUiState.Editing } as GiftListEditUiState.Editing
        assertEquals("Noël chez Mamie", state.form.title)
        assertEquals(LocalDate.of(2026, 12, 25), state.form.date)
        // A member who left is dropped, and the beneficiary of an own list always sees it.
        assertEquals(setOf("adult1", "adult2"), state.form.viewerIds)
        val stranger = GiftListEditViewModel(FakeGifts(null), FakeMembers(), "adult2", null, saved = SavedStateHandle(mapOf("draft.title" to "X", "draft.beneficiary" to "gone")))
        val fresh = stranger.uiState.first { it is GiftListEditUiState.Editing } as GiftListEditUiState.Editing
        assertEquals("", fresh.form.title)
    }

    @Test
    fun `an empty « Mes envies » shows to everyone, and only its keeper adds to it`() = runTest {
        val vm = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult2", "wishes-kid2", today = flowOf(today))
        val state = vm.uiState.first { it !is GiftListUiState.Loading }
        assertTrue(state is GiftListUiState.Success && state.list.kind == GiftListKind.Wishes && state.canAddItems)
        val other = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-kid2", today = flowOf(today))
        val seen = other.uiState.first { it !is GiftListUiState.Loading } as GiftListUiState.Success
        assertFalse(seen.canAddItems)
        assertFalse(seen.canReserve)
        val nobody = GiftListViewModel(FakeGifts(null), FakeMembers(), "adult1", "wishes-stranger", today = flowOf(today))
        assertEquals(GiftListUiState.Missing, nobody.uiState.first { it !is GiftListUiState.Loading })
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
    /** The server refuses the parts, like right after « Cadeau commun ». */
    var refuseShares = false

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
    override fun observeShares(listId: String, itemIds: List<String>, memberId: String, allOf: Set<String>): Flow<Map<String, List<GiftShare>>> =
        if (refuseShares) flow { throw IllegalStateException("PERMISSION_DENIED") } else shares.map { all ->
            all.filterKeys { it in itemIds }.mapValues { (itemId, parts) -> if (itemId in allOf) parts else parts.filter { it.memberId == memberId } }
        }
    override fun newListId(): String = "new-list"
    override fun newItemId(listId: String): String = "new-item"
    override suspend fun saveList(list: GiftList, isNew: Boolean) = Unit
    override suspend fun deleteList(listId: String) = Unit
    val savedItems = mutableListOf<GiftItem>()
    override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) {
        savedItems += item
    }
    override suspend fun deleteItem(listId: String, itemId: String) = Unit
    /** What the server does with a reservation: nothing by default, or a refusal. */
    var onClaim: suspend (GiftClaim) -> Unit = {}
    override suspend fun claim(listId: String, claim: GiftClaim) = onClaim(claim)
    override suspend fun release(listId: String, itemId: String) = Unit
    override suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean) = Unit
    override suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean) = Unit
    override suspend fun leaveShare(listId: String, itemId: String, memberId: String) = Unit
}
