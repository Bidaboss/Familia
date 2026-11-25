package fr.familia.app.feature.gifts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.GiftContribution
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.detail.ContributionView
import fr.familia.app.feature.gifts.detail.GiftListActions
import fr.familia.app.feature.gifts.detail.GiftListScreen
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.GiftListViewModel
import fr.familia.app.feature.gifts.detail.ItemRow
import fr.familia.app.feature.gifts.detail.ItemSheetContent
import fr.familia.app.feature.gifts.detail.contributionGauge
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * « Déjà acheté, participation bienvenue » (PRD rév. 309, récit 68). Fictional family of TestFamily:
 * adult1 and adult2 adults, kid1 a child with the app, kid2 a child without the app kept by adult2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class AlreadyBoughtTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)

    private fun wishes(beneficiary: String) =
        GiftList("wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary)

    private val bike = GiftItem(
        "bike", "Un vélo", null, 120.0, null, "adult1", "Adult1", visibleToBeneficiary = true,
        alreadyBought = true, contributedTotal = 45,
    )

    /** The parts of the wishes, and what the screen asked and wrote. */
    private class Gifts(list: GiftList, item: GiftItem, private val fake: FakeGifts = FakeGifts(list)) : GiftRepository by fake {
        init {
            fake.items.value = listOf(item, GiftItem("book", "Un livre", null, 15.0, null, item.authorId, "Auteur", true))
        }
        val parts = MutableStateFlow(listOf(GiftContribution("adult2", "Adult2", 25), GiftContribution("admin1", "Admin1", 20)))
        val asked = mutableListOf<Pair<List<String>, Boolean>>()
        val written = mutableListOf<String>()
        override fun observeContributions(listId: String, itemIds: List<String>, memberId: String, all: Boolean): Flow<Map<String, List<GiftContribution>>> {
            asked += itemIds to all
            // As the repository reads them (récit 93): the keeper gets first names alone, the others their own part.
            return parts.map { list ->
                itemIds.associateWith { if (all) list.map { it.copy(amount = 0) } else list.filter { it.memberId == memberId } }
            }
        }
        override suspend fun setContribution(listId: String, itemId: String, contribution: GiftContribution, previous: Int) {
            written += "set $itemId ${contribution.memberId} ${contribution.amount} after $previous"
        }
        override suspend fun removeContribution(listId: String, itemId: String, memberId: String, previous: Int) {
            written += "remove $itemId $memberId $previous"
        }
    }

    private fun viewModel(gifts: Gifts, meId: String, listId: String, on: Boolean = true, readerId: String = meId) =
        GiftListViewModel(gifts, FakeMembers(), meId, listId, today = flowOf(today), readerId = readerId, alreadyBought = on)

    private suspend fun GiftListViewModel.success() = uiState.first { it is GiftListUiState.Success } as GiftListUiState.Success

    @Test
    fun `another adult takes part, never reserves, and sees only the total and their own part`() = runTest {
        val gifts = Gifts(wishes("adult1"), bike)
        val vm = viewModel(gifts, "adult2", "wishes-adult1")
        val state = vm.success()
        val row = state.items.first { it.item.id == "bike" }
        assertTrue(state.canContribute)
        assertFalse(state.seesContributors)
        assertEquals(ContributionView(total = 45, mine = 25, parts = emptyList()), row.contributions)
        assertNull(state.items.first { it.item.id == "book" }.contributions)
        assertEquals(listOf(listOf("bike") to false), gifts.asked)

        vm.contribute("bike", 30)
        vm.withdrawContribution("bike")
        assertEquals(listOf("set bike adult2 30 after 25", "remove bike adult2 25"), gifts.written)
    }

    @Test
    fun `the person sees every first name and no amount, and does not take part`() = runTest {
        val gifts = Gifts(wishes("adult1"), bike)
        val state = viewModel(gifts, "adult1", "wishes-adult1").success()
        assertTrue(state.seesContributors)
        assertFalse(state.canContribute)
        val parts = state.items.first { it.item.id == "bike" }.contributions?.parts
        assertEquals(listOf("Admin1", "Adult2"), parts?.map { it.memberName })
        assertEquals(listOf(0, 0), parts?.map { it.amount })
        assertEquals(listOf(listOf("bike") to true), gifts.asked)
    }

    @Test
    fun `the adult of a child without the app sees the parts of their wishes`() = runTest {
        val gifts = Gifts(wishes("kid2"), bike.copy(authorId = "adult2"))
        val state = viewModel(gifts, "adult2", "wishes-kid2").success()
        assertTrue(state.seesContributors)
        assertFalse(state.canContribute)
    }

    @Test
    fun `a child does not take part`() = runTest {
        val state = viewModel(Gifts(wishes("adult1"), bike), "kid1", "wishes-adult1").success()
        assertFalse(state.canContribute)
    }

    @Test
    fun `in « Voir comme » no part is read, nor any name shown`() = runTest {
        val gifts = Gifts(wishes("adult1"), bike)
        val state = viewModel(gifts, "adult1", "wishes-adult1", readerId = "admin1").success()
        assertFalse(state.seesContributors)
        assertTrue(gifts.asked.isEmpty())
        assertEquals(45, state.items.first { it.item.id == "bike" }.contributions?.total)
    }

    @Test
    fun `switch off, nothing changes`() = runTest {
        val gifts = Gifts(wishes("adult1"), bike)
        val state = viewModel(gifts, "adult2", "wishes-adult1", on = false).success()
        assertFalse(state.canContribute)
        assertTrue(state.items.all { it.contributions == null })
        assertTrue(gifts.asked.isEmpty())
    }

    @Test
    fun `the gauge fills with the total, up to the price`() {
        assertEquals(0.375f, contributionGauge(45, 120.0)!!, 0.001f)
        assertEquals(1f, contributionGauge(200, 120.0)!!, 0.001f)
        assertNull(contributionGauge(45, null))
        assertNull(contributionGauge(45, 0.0))
    }

    private suspend fun editor(gifts: GiftRepository, itemId: String?, on: Boolean = true): GiftItemEditViewModel {
        val vm = GiftItemEditViewModel(gifts, FakeMembers(), "adult1", "wishes-adult1", itemId, alreadyBought = on)
        vm.uiState.first { it is GiftItemEditUiState.Editing }
        return vm
    }

    private fun GiftItemEditViewModel.editing() = uiState.value as GiftItemEditUiState.Editing

    @Test
    fun `the person checks the box on a wish, and it is saved`() = runTest {
        val fake = FakeGifts(wishes("adult1"))
        val vm = editor(fake, null)
        assertTrue(vm.editing().showAlreadyBought)
        vm.update { it.copy(title = "Un vélo", alreadyBought = true) }
        vm.save()
        vm.uiState.first { it == GiftItemEditUiState.Done }
        assertTrue(fake.savedItems.single().alreadyBought)
        assertFalse(editor(fake, null, on = false).editing().showAlreadyBought)
    }

    @Test
    fun `once someone takes part, the box stays checked`() = runTest {
        val fake = FakeGifts(wishes("adult1"))
        fake.items.value = listOf(bike)
        val vm = editor(fake, "bike")
        assertTrue(vm.editing().alreadyBoughtLocked)
        vm.update { it.copy(alreadyBought = false, title = "Un vélo rouge") }
        vm.save()
        vm.uiState.first { it == GiftItemEditUiState.Done }
        assertTrue(fake.savedItems.single().alreadyBought)
    }

    private fun sheet(seesContributors: Boolean, canContribute: Boolean, mine: Int? = null) {
        val row = ItemRow(
            bike, canEdit = false, isSurpriseIdea = false, claim = null,
            contributions = ContributionView(45, mine, if (seesContributors) listOf(GiftContribution("adult2", "Camille")) else emptyList()),
        )
        val beneficiary = fr.familia.app.feature.events.TestFamily.adult
        val state = GiftListUiState.Success(
            list = wishes("adult1"), beneficiary = beneficiary, today = today,
            keptFromMe = seesContributors, isMine = seesContributors, canManage = false, canAddItems = false,
            canReserve = !seesContributors, canShare = !seesContributors, items = listOf(row), myName = "Jules",
            canContribute = canContribute, seesContributors = seesContributors,
        )
        compose.setContent {
            FamiliaTheme {
                ItemSheetContent(row, state, GiftListActions(), onEdit = {})
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `the sheet of another adult says « Je participe », never « Je le prends »`() {
        sheet(seesContributors = false, canContribute = true)
        assertEquals(1, count("Déjà acheté, participation bienvenue"))
        assertEquals(1, count("Je participe"))
        assertEquals(0, count("Je le prends"))
        assertEquals(0, count("Cadeau commun"))
        assertEquals(0, count("Camille"))
    }

    @Test
    fun `the sheet of the person names who takes part`() {
        sheet(seesContributors = true, canContribute = false)
        assertEquals(1, count("Camille"))
        // The name, never what it gave (récit 93).
        assertEquals(0, count("Camille :"))
        assertEquals(0, count("Je participe"))
    }

    @Test
    fun `the row of a wish already bought says so, and has no « Je le prends »`() {
        val row = ItemRow(bike, canEdit = false, isSurpriseIdea = false, claim = null, contributions = ContributionView(45))
        val state = GiftListUiState.Success(
            list = wishes("adult1"), beneficiary = fr.familia.app.feature.events.TestFamily.adult, today = today,
            keptFromMe = false, isMine = false, canManage = false, canAddItems = false, canReserve = true, canShare = true,
            items = listOf(row), myName = "Jules", canContribute = true,
        )
        compose.setContent { FamiliaTheme { GiftListScreen(state, GiftListActions()) } }
        compose.waitForIdle()
        assertEquals(0, count("Je le prends"))
        assertEquals(1, count("Déjà acheté · participation souhaitée"))
    }
}
