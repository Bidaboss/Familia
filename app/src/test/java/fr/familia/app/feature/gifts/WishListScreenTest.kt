package fr.familia.app.feature.gifts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.detail.GiftListActions
import fr.familia.app.feature.gifts.detail.GiftListScreen
import fr.familia.app.feature.gifts.detail.GiftListUiState
import fr.familia.app.feature.gifts.detail.ItemRow
import fr.familia.app.feature.gifts.detail.WishSurpriseState
import fr.familia.app.feature.gifts.detail.freshWishIds
import fr.familia.app.feature.gifts.detail.ideasFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * The lists of wishes redone after the prototype (correctif E2 à E4): « Mes envies » with ⋯ and no
 * reservation, someone else's with « Ses envies | Mes idées », the states and « nouveau ».
 * Fictional family of TestFamily.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class WishListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)

    private fun wishes(beneficiary: String) =
        GiftList("wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary)

    private val headphones = GiftItem(
        "casque", "Un casque", null, 49.0, null, "adult1", "Adult1", visibleToBeneficiary = true,
        addedAt = Instant.parse("2026-11-24T10:00:00Z"),
    )
    private val book = GiftItem("livre", "Un livre", null, 14.0, null, "adult1", "Adult1", visibleToBeneficiary = true)

    private fun state(isMine: Boolean, wishStates: Map<String, WishSurpriseState> = emptyMap()) = GiftListUiState.Success(
        list = wishes("adult1"), beneficiary = TestFamily.adult, today = today,
        keptFromMe = isMine, isMine = isMine, canManage = isMine, canAddItems = isMine, canReserve = false, canShare = false,
        items = listOf(headphones, book).map { ItemRow(it, canEdit = isMine, isSurpriseIdea = false, claim = null) },
        myName = if (isMine) "Adult1" else "Adult2",
        wishStates = wishStates,
    )

    private val idea = NotebookEntry(
        "i1", NotebookSource.Idea, "adult1", "Adult1", NotebookOccasion(null, null), "Un carnet", null, 12.0, null, GiftStatus.Idea,
    )

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `my wishes say who sees them, never who reserves, and ⋯ edits`() {
        val edited = mutableListOf<String>()
        compose.setContent { FamiliaTheme { GiftListScreen(state(isMine = true), GiftListActions(onEditItem = { edited += it })) } }
        assertEquals(1, count("Toute la famille voit ces envies. Toi, tu ne vois jamais qui les réserve."))
        assertEquals(1, count("+ Ajouter une envie"))
        assertEquals(0, count("Libre"))
        assertEquals(0, count("Ses envies"))
        // ⋯ opens the page of the wish (correctif E9), whose « Modifier cette envie » opens the form.
        compose.onNodeWithContentDescription("Ouvrir Un casque").performClick()
        assertEquals(1, count("Toute la famille voit cette envie."))
        compose.onNodeWithText("Modifier cette envie", substring = true).performClick()
        assertEquals(listOf("casque"), edited)
    }

    @Test
    fun `the page of my wish changes its occasion in one touch and removes it once asked`() {
        val occasions = mutableListOf<Pair<String, fr.familia.app.core.model.GiftOccasion?>>()
        val removed = mutableListOf<String>()
        compose.setContent {
            FamiliaTheme {
                GiftListScreen(
                    state(isMine = true),
                    GiftListActions(onSetWishOccasion = { id, o -> occasions += id to o }, onRemoveWish = { removed += it }),
                )
            }
        }
        compose.onNodeWithText("Un livre").performClick()
        compose.onNodeWithText("Noël").performClick()
        assertEquals(listOf("livre" to fr.familia.app.core.model.GiftOccasion.Christmas), occasions)
        compose.onNodeWithText("Retirer de mes envies").performClick()
        assertTrue(removed.isEmpty())
        compose.onNodeWithText("Retirer").performClick()
        assertEquals(listOf("livre"), removed)
        // Back on the list.
        assertEquals(1, count("Toute la famille voit ces envies."))
    }

    @Test
    fun `someone else's wishes have two tabs, their states and « nouveau »`() {
        val states = mapOf("casque" to WishSurpriseState.Free, "livre" to WishSurpriseState.Taken("Adult2", mine = true))
        compose.setContent {
            FamiliaTheme {
                GiftListScreen(state(isMine = false, states), GiftListActions(onAddIdea = {}), ideas = listOf(idea), freshIds = setOf("casque"))
            }
        }
        assertEquals(1, count("Ses envies"))
        assertEquals(1, count("Liste créée par Adult1 depuis sa fiche. Adult1 ne voit pas qui réserve."))
        assertEquals(1, count("Libre · nouveau"))
        assertEquals(1, count("Tu t'en occupes"))
        // No more « Tailles et goûts » here, nor a floating « Ajouter » (correctif E3).
        assertEquals(0, count("Tailles et goûts"))
        assertEquals(0, count("+ Ajouter une envie"))
        assertEquals(0, count("Un carnet"))
    }

    @Test
    fun `« Mes idées » shows my private ideas and adds one`() {
        var added = 0
        compose.setContent {
            FamiliaTheme {
                GiftListScreen(state(isMine = false), GiftListActions(onAddIdea = { added++ }), ideas = listOf(idea))
            }
        }
        compose.onNodeWithText("Mes idées").performClick()
        assertEquals(1, count("Seul·e toi vois tes idées pour Adult1."))
        assertEquals(1, count("Un carnet"))
        assertEquals(1, count("Seulement toi"))
        assertEquals(0, count("Un casque"))
        compose.onNodeWithText("+ Ajouter une idée").performClick()
        assertEquals(1, added)
    }

    @Test
    fun `without the notebook, no « Mes idées »`() {
        compose.setContent { FamiliaTheme { GiftListScreen(state(isMine = false), GiftListActions(), ideas = null) } }
        assertEquals(0, count("Mes idées"))
        assertEquals(1, count("Un casque"))
    }

    @Test
    fun `every target of someone else's wishes is big enough and named`() = compose.checkAccessibility {
        GiftListScreen(state(isMine = false, mapOf("casque" to WishSurpriseState.Free)), GiftListActions(onAddIdea = {}), ideas = listOf(idea))
    }

    @Test
    fun `every target of my wishes is big enough and named`() = compose.checkAccessibility {
        GiftListScreen(state(isMine = true), GiftListActions())
    }

    @Test
    fun `« nouveau » counts what others added since the visit before, never before a first one`() {
        val since = Instant.parse("2026-11-23T00:00:00Z").toEpochMilli()
        assertEquals(setOf("casque"), freshWishIds(listOf(headphones, book), since, "adult2"))
        // My own additions are never new to me.
        assertTrue(freshWishIds(listOf(headphones), since, "adult1").isEmpty())
        assertTrue(freshWishIds(listOf(headphones), null, "adult2").isEmpty())
    }

    @Test
    fun `« Mes idées » keeps only my ideas for the person, not moved nor given`() {
        val other = idea.copy(id = "i2", personId = "kid1")
        val moved = idea.copy(id = "i3", movedTo = "x")
        val given = idea.copy(id = "i4", status = GiftStatus.Given)
        val claim = idea.copy(id = "i5", source = NotebookSource.Claim)
        assertEquals(listOf("i1"), ideasFor(listOf(idea, other, moved, given, claim), "adult1").map { it.id })
    }
}
