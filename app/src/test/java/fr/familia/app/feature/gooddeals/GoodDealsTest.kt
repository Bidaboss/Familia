package fr.familia.app.feature.gooddeals

import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.linkSiteName
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailUiState
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailViewModel
import fr.familia.app.feature.gooddeals.edit.GoodDealEditUiState
import fr.familia.app.feature.gooddeals.edit.GoodDealEditViewModel
import fr.familia.app.feature.gooddeals.edit.GoodDealFormError
import fr.familia.app.feature.gooddeals.edit.normalizeLink
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
import org.junit.Assert.assertNull
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
    override suspend fun setRecommended(id: String, memberId: String, recommended: Boolean) {
        deals.value = deals.value.map {
            if (it.id != id) it else it.copy(recommendedIds = if (recommended) it.recommendedIds + memberId else it.recommendedIds - memberId)
        }
    }
}

/** Bons plans in Entraide (PRD, récit 45). Fictional family of TestFamily. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodDealsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-25T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 25)

    private fun deal(id: String, author: String = "adult1", days: Long = 3, interested: List<String> = emptyList(), photo: String? = null) =
        GoodDeal(
            id, GoodDealKind.Deal, "Bon plan $id", endsAt = now.plus(days, ChronoUnit.DAYS), interestedIds = interested,
            authorId = author, authorName = author.replaceFirstChar { it.uppercase() }, photo = photo,
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

    // --- One bon plan ----------------------------------------------------------------------------

    @Test
    fun `a child says Ça m'intéresse and takes it back`() = runTest {
        val repo = FakeGoodDeals(listOf(deal("d1", interested = listOf("adult2"))))
        val viewModel = GoodDealDetailViewModel(repo, FakeMembers(), "kid1", "d1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        var state = viewModel.uiState.value as GoodDealDetailUiState.Ready
        assertFalse(state.canManage)
        assertEquals(listOf("Adult2"), state.interestedNames)
        viewModel.toggleInterest()
        state = viewModel.uiState.value as GoodDealDetailUiState.Ready
        assertTrue(state.iAmInterested)
        viewModel.toggleInterest()
        assertFalse((viewModel.uiState.value as GoodDealDetailUiState.Ready).iAmInterested)
    }

    @Test
    fun `the author and the admin principal manage it, and removing it empties the screen`() = runTest {
        val repo = FakeGoodDeals(listOf(deal("d1")))
        val other = GoodDealDetailViewModel(repo, FakeMembers(), "adult2", "d1")
        val admin = GoodDealDetailViewModel(repo, FakeMembers(), "admin1", "d1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { admin.uiState.collect {} }
        assertFalse((other.uiState.value as GoodDealDetailUiState.Ready).canManage)
        other.remove()
        assertEquals(1, repo.deals.value.size)
        assertTrue((admin.uiState.value as GoodDealDetailUiState.Ready).canManage)
        admin.remove()
        assertEquals(GoodDealDetailUiState.Missing, admin.uiState.value)
    }

    // --- The form --------------------------------------------------------------------------------

    private class FakeStorage : PhotoStorage {
        val deleted = mutableListOf<String>()
        override suspend fun upload(path: String, jpeg: ByteArray) = Unit
        override suspend fun downloadUrl(path: String) = "https://example.com/$path"
        override suspend fun delete(path: String) {
            deleted += path
        }
    }

    private val sent = mutableListOf<String>()
    private val storage = FakeStorage()
    private val photos = SinglePhotoSaver({ _, photo, _ -> sent += photo }, storage)

    private fun edit(repo: FakeGoodDeals, me: String = "adult1", dealId: String? = null) =
        GoodDealEditViewModel(repo, FakeMembers(), me, dealId, photos = photos, today = { today })

    @Test
    fun `a new bon plan ends in 7 days by default, at midnight in Paris`() {
        val repo = FakeGoodDeals()
        val viewModel = edit(repo)
        val state = viewModel.uiState.value as GoodDealEditUiState.Editing
        assertEquals(today.plusDays(7), state.form.lastDay)
        viewModel.update { it.copy(title = " Fraises à cueillir ", link = "exemple.fr/fraises") }
        viewModel.save()
        assertEquals(GoodDealEditUiState.Done, viewModel.uiState.value)
        val (saved, isNew) = repo.saved.single()
        assertTrue(isNew)
        assertEquals("Fraises à cueillir", saved.title)
        assertEquals("https://exemple.fr/fraises", saved.link)
        assertEquals(Instant.parse("2026-12-02T23:00:00Z"), saved.endsAt)
        assertEquals("adult1", saved.authorId)
        assertNull(saved.photo)
    }

    @Test
    fun `a title is required, the end stays within 3 months, and a link must be one`() {
        val viewModel = edit(FakeGoodDeals())
        viewModel.update { it.copy(lastDay = today.plusDays(91), link = "pas un lien") }
        viewModel.save()
        val state = viewModel.uiState.value as GoodDealEditUiState.Editing
        assertEquals(setOf(GoodDealFormError.TitleMissing, GoodDealFormError.EndInvalid, GoodDealFormError.LinkInvalid), state.errors)
    }

    @Test
    fun `a child cannot publish, nor someone else change my bon plan`() {
        assertEquals(GoodDealEditUiState.Unavailable, edit(FakeGoodDeals(), me = "kid1").uiState.value)
        assertEquals(GoodDealEditUiState.Unavailable, edit(FakeGoodDeals(listOf(deal("d1"))), me = "adult2", dealId = "d1").uiState.value)
        assertTrue(edit(FakeGoodDeals(listOf(deal("d1"))), me = "admin1", dealId = "d1").uiState.value is GoodDealEditUiState.Editing)
    }

    @Test
    fun `an ended bon plan can be fixed without moving its end`() {
        val ended = deal("d1", days = -2)
        val repo = FakeGoodDeals(listOf(ended))
        val viewModel = edit(repo, dealId = "d1")
        viewModel.update { it.copy(text = "C'est fini.") }
        viewModel.save()
        assertEquals(ended.endsAt, repo.saved.single().first.endsAt)
    }

    @Test
    fun `a new photo goes to my folder of this deal, and the old one is removed`() {
        val repo = FakeGoodDeals(listOf(deal("d1", photo = "goodDeals/d1/adult1/old")))
        val viewModel = edit(repo, dealId = "d1")
        viewModel.update { it.copy(photo = PhotoEdit.Picked("content://promo")) }
        viewModel.save()
        val photo = repo.saved.single().first.photo!!
        assertTrue(photo.matches(Regex("^goodDeals/d1/adult1/[A-Za-z0-9]{20}$")))
        assertEquals(listOf("$photo.jpg"), sent)
        assertEquals(listOf("goodDeals/d1/adult1/old.jpg", "goodDeals/d1/adult1/old-thumb.jpg"), storage.deleted)
    }

    // --- Bonnes adresses -------------------------------------------------------------------------

    private fun address(id: String, title: String, category: GoodDealCategory, place: String? = null, author: String = "adult1") =
        GoodDeal(id, GoodDealKind.Address, title, place = place, category = category, authorId = author, authorName = author.replaceFirstChar { it.uppercase() })

    @Test
    fun `addresses are filtered by category and found by name or town, without accents`() = runTest {
        val repo = FakeGoodDeals(
            listOf(
                address("a1", "Dr Martin, dentiste", GoodDealCategory.Health, "Lyon"),
                address("a2", "Garage Durand", GoodDealCategory.Car, "Villeurbanne"),
                address("a3", "Pédiatre Bernard", GoodDealCategory.Health, "Écully"),
            ),
        )
        val viewModel = GoodDealsViewModel(repo, FakeMembers(), "adult2", now = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        fun ids() = (viewModel.uiState.value as GoodDealsUiState.Success).addresses.map { it.deal.id }
        assertEquals(listOf("a1", "a2", "a3"), ids())
        viewModel.pickCategory(GoodDealCategory.Health)
        assertEquals(listOf("a1", "a3"), ids())
        viewModel.search("ecully")
        assertEquals(listOf("a3"), ids())
        viewModel.pickCategory(GoodDealCategory.Health)
        viewModel.search("pediatre ECULLY")
        assertEquals(listOf("a3"), ids())
        viewModel.search("Paris")
        val state = viewModel.uiState.value as GoodDealsUiState.Success
        assertTrue(state.addresses.isEmpty())
        assertTrue(state.hasAddresses)
    }

    @Test
    fun `an adult recommends an address too, not its author nor a child`() = runTest {
        val repo = FakeGoodDeals(listOf(address("a1", "Dr Martin", GoodDealCategory.Health)))
        val other = GoodDealDetailViewModel(repo, FakeMembers(), "adult2", "a1")
        val author = GoodDealDetailViewModel(repo, FakeMembers(), "adult1", "a1")
        val kid = GoodDealDetailViewModel(repo, FakeMembers(), "kid1", "a1")
        for (vm in listOf(other, author, kid)) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("Adult1"), (other.uiState.value as GoodDealDetailUiState.Ready).recommendedNames)
        other.toggleRecommend()
        assertTrue((other.uiState.value as GoodDealDetailUiState.Ready).iRecommend)
        assertEquals(listOf("Adult2"), (author.uiState.value as GoodDealDetailUiState.Ready).recommendedNames)
        assertFalse((author.uiState.value as GoodDealDetailUiState.Ready).canRecommend)
        kid.toggleRecommend()
        assertEquals(listOf("adult2"), repo.deals.value.single().recommendedIds)
    }

    @Test
    fun `a new address needs a category, no end and no photo`() {
        val repo = FakeGoodDeals()
        val viewModel = GoodDealEditViewModel(repo, FakeMembers(), "adult1", kind = GoodDealKind.Address, photos = photos, today = { today })
        assertNull((viewModel.uiState.value as GoodDealEditUiState.Editing).form.lastDay)
        viewModel.update { it.copy(title = "Garage Durand", phone = "appelle") }
        viewModel.save()
        assertEquals(
            setOf(GoodDealFormError.CategoryMissing, GoodDealFormError.PhoneInvalid),
            (viewModel.uiState.value as GoodDealEditUiState.Editing).errors,
        )
        viewModel.update { it.copy(category = GoodDealCategory.Car, phone = "04 78 00 00 00", place = "Villeurbanne") }
        viewModel.save()
        val saved = repo.saved.single().first
        assertEquals(GoodDealKind.Address, saved.kind)
        assertEquals(GoodDealCategory.Car, saved.category)
        assertEquals("04 78 00 00 00", saved.phone)
        assertNull(saved.endsAt)
        assertNull(saved.photo)
    }

    // --- Small helpers ---------------------------------------------------------------------------

    @Test
    fun `a link is shown by its site and completed when typed short`() {
        assertEquals("exemple.fr", linkSiteName("https://www.exemple.fr/promo?x=1"))
        assertEquals("https://exemple.fr", normalizeLink(" exemple.fr "))
        assertEquals("http://exemple.fr", normalizeLink("http://exemple.fr"))
        assertEquals("", normalizeLink("  "))
    }

    @Test
    fun `the last day of a bon plan is the day before its midnight end`() {
        val end = dealEnd(LocalDate.of(2026, 12, 2))
        assertEquals(LocalDate.of(2026, 12, 2), dealLastDay(end))
        assertTrue(dealDayAllowed(today, today))
        assertFalse(dealDayAllowed(today.minusDays(1), today))
        assertFalse(dealDayAllowed(today.plusDays(91), today))
    }

    // --- The Accueil -----------------------------------------------------------------------------

    @Test
    fun `the chip shows the newest bon plan of someone else for 2 days`() {
        fun posted(id: String, author: String, hoursAgo: Long, days: Long = 3) =
            deal(id, author = author, days = days).copy(createdAt = now.minus(hoursAgo, ChronoUnit.HOURS))
        val list = listOf(posted("old", "adult2", 49), posted("mine", "adult1", 1), posted("new", "adult2", 5), posted("ended", "adult2", 2, days = -1))
        assertEquals("new", newGoodDealFor(list, "adult1", now)?.id)
        assertNull(newGoodDealFor(list.filter { it.id != "new" }, "adult1", now))
    }

    @Test
    fun `the Accueil card shows the full photo of a bon plan, and none without one`() {
        assertEquals("goodDeals/d1/adult2/p1.jpg", goodDealHomePhoto(deal("d1", "adult2", photo = "goodDeals/d1/adult2/p1")))
        assertNull(goodDealHomePhoto(deal("d2", "adult2")))
        assertNull(goodDealHomePhoto(deal("d3", "adult2", photo = " ")))
    }
}
