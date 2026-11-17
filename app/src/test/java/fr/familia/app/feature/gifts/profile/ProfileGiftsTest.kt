package fr.familia.app.feature.gifts.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.gifts.GiftsScreen
import fr.familia.app.feature.gifts.WishPreview
import fr.familia.app.feature.gifts.notebook.giftsFor
import fr.familia.app.feature.gifts.previewGifts
import fr.familia.app.feature.gifts.birthdaysSection
import androidx.compose.foundation.lazy.LazyColumn
import fr.familia.app.feature.gifts.surpriseIdeasToRead
import fr.familia.app.feature.presentation.LocalPresenting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * « Ses envies » and « Ce que je lui offre » on a member's page, and « Mes envies » leading there
 * from the Cadeaux tab (retours d'essai du 17/11). Fictional family only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ProfileGiftsTest {

    @get:Rule
    val compose = createComposeRule()

    private val ines = Member("b", "Inès", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    private val christmas = NotebookOccasion(GiftOccasion.Christmas, 2026)

    private fun entry(id: String, personId: String?, title: String, status: GiftStatus = GiftStatus.Idea, source: NotebookSource = NotebookSource.Idea) =
        NotebookEntry(id, source, personId, "Inès", christmas, title, null, null, null, status, listId = if (source == NotebookSource.Idea) null else "wishes-b")

    private fun surprise(id: String, beneficiary: String, revealed: Boolean = false, kind: GiftListKind = GiftListKind.Surprise) =
        GiftList(id, kind, "Surprise $id", GiftOccasion.Birthday, LocalDate.of(2027, 2, 3), beneficiary, listOf("a"), "a", "Camille", revealed)

    @Test
    fun `only the gifts for that person, the ones still to give first`() {
        val entries = listOf(
            entry("e1", "b", "Une écharpe", GiftStatus.Given),
            entry("e2", "b", "Un roman", source = NotebookSource.Claim),
            entry("e3", "c", "Un puzzle"),
            entry("e4", null, "Une plante"),
            entry("e5", "b", "Des gants", source = NotebookSource.Share),
        )
        assertEquals(listOf("e5", "e2", "e1"), giftsFor(entries, "b", singleList = false).map { it.id })
    }

    @Test
    fun `only the surprises for that person not revealed yet`() {
        val lists = listOf(surprise("s1", "b"), surprise("s2", "b", revealed = true), surprise("s3", "c"), surprise("s4", "b", kind = GiftListKind.Own))
        assertEquals(listOf("s1"), preparedSurprises(lists, "b").map { it.id })
    }

    @Test
    fun `never on my own page, in Voir comme or while presenting`() {
        assertTrue(showsGiving("a", "b", viewingAs = false, presenting = false))
        assertFalse(showsGiving("a", "a", viewingAs = false, presenting = false))
        assertFalse(showsGiving("a", "b", viewingAs = true, presenting = false))
        assertFalse(showsGiving("a", "b", viewingAs = false, presenting = true))
    }

    @Test
    fun `the card lists my gifts and surprises and opens them`() {
        val opened = mutableListOf<String>()
        var notebook = 0
        val state = GivingState(listOf(entry("e2", "b", "Un roman", source = NotebookSource.Claim)), listOf(surprise("s1", "b")))
        compose.setContent {
            FamiliaTheme {
                ProfileGivingContent(state, ines, notebookOn = true, failed = false, onOpenEntry = { opened += it.id }, onOpenList = { opened += it }, onOpenNotebook = { notebook++ }, onAddIdea = {})
            }
        }
        compose.onNodeWithText("Ce que j'offre à Inès").assertExists()
        compose.onNodeWithText("Un roman").performClick()
        compose.onNodeWithText("Surprise en cours : Surprise s1").performClick()
        compose.onNodeWithText("Tout voir dans Cadeaux").performClick()
        assertEquals(listOf("e2", "s1"), opened)
        assertEquals(1, notebook)
    }

    @Test
    fun `without the notebook, no idea to add`() {
        compose.setContent {
            FamiliaTheme {
                ProfileGivingContent(GivingState(emptyList(), emptyList()), ines, notebookOn = false, failed = false, onOpenEntry = {}, onOpenList = {}, onOpenNotebook = {}, onAddIdea = {})
            }
        }
        assertEquals(0, compose.onAllNodesWithText("Ajouter une idée").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Tout voir dans Cadeaux").fetchSemanticsNodes().size)
    }

    @Test
    fun `my wishes show their titles and let me add one`() {
        var added = 0
        compose.setContent {
            FamiliaTheme {
                ProfileWishesContent(WishPreview(2, listOf("Un livre", "Une plante")), ines, isMe = true, keptByMe = true, mayAdd = true, onOpen = {}, onAdd = { added++ })
            }
        }
        compose.onNodeWithText("Mes envies").assertExists()
        compose.onNodeWithText("Un livre", substring = true).assertExists()
        compose.onNodeWithText("Ajouter une envie").performClick()
        assertEquals(1, added)
    }

    @Test
    fun `the wishes of someone else cannot be added to`() {
        var opened = 0
        compose.setContent {
            FamiliaTheme {
                ProfileWishesContent(WishPreview(1, listOf("Un livre")), ines, isMe = false, keptByMe = false, mayAdd = false, onOpen = { opened++ }, onAdd = {})
            }
        }
        compose.onNodeWithText("Envies de Inès").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Ajouter une envie").fetchSemanticsNodes().size)
        compose.onNodeWithText("Voir toute la liste").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `in the Cadeaux tab, my wishes lead to my page`() {
        val opened = mutableListOf<String>()
        var page = 0
        compose.setContent {
            FamiliaTheme {
                GiftsScreen(previewGifts(), onOpenList = { opened += it }, onNewList = {}, onRetry = {}, onOpenMyPage = { page++ })
            }
        }
        compose.onNodeWithText("Elles sont sur ta fiche", substring = true).performClick()
        assertEquals(1, page)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `with the single list, only the birthdays of others are read for a surprise`() {
        val state = previewGifts().copy(singleList = true)
        assertEquals(listOf("b"), surpriseIdeasToRead(state))
        assertTrue(surpriseIdeasToRead(previewGifts()).isEmpty())
        val mine = state.copy(birthdays = state.birthdays.map { it.copy(member = state.sections.first { s -> s.isMe }.member) })
        assertTrue(surpriseIdeasToRead(mine).isEmpty())
        // A child without the app whose lists I keep: their adults see a surprise for them (retour du 18/11).
        val kid = state.sections.first { it.member.id == "c" }.member
        val forKid = state.copy(birthdays = state.birthdays.map { it.copy(member = kid) })
        assertEquals(listOf("c"), surpriseIdeasToRead(forKid))
    }

    private fun showBirthdays(presenting: Boolean, opened: MutableList<String>) {
        val state = previewGifts().copy(singleList = true, surprisePreparedFor = setOf("b"))
        compose.setContent {
            FamiliaTheme {
                CompositionLocalProvider(LocalPresenting provides presenting) {
                    // The birthdays alone: the whole tab may push the card out of the test screen.
                    LazyColumn { birthdaysSection(state, onAllBirthdays = {}, onPrepareSurprise = {}, onOpenList = { opened += it }) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a surprise idea shows on the birthday card and opens the ideas`() {
        val opened = mutableListOf<String>()
        showBirthdays(presenting = false, opened)
        compose.onNodeWithText("Une surprise est déjà en préparation").performClick()
        assertEquals(listOf("ideas-b"), opened)
        compose.onNodeWithText("Préparer une surprise").assertExists()
    }

    @Test
    fun `never while presenting`() {
        showBirthdays(presenting = true, mutableListOf())
        assertEquals(0, compose.onAllNodesWithText("Une surprise est déjà en préparation").fetchSemanticsNodes().size)
    }

    @Test
    fun `on the page, a surprise idea opens the ideas`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            FamiliaTheme {
                ProfileGivingContent(GivingState(emptyList(), emptyList(), surpriseIdea = true), ines, notebookOn = true, failed = false, onOpenEntry = {}, onOpenList = { opened += it }, onOpenNotebook = {}, onAddIdea = {})
            }
        }
        compose.onNodeWithText("Une surprise est déjà en préparation").performClick()
        assertEquals(listOf("ideas-b"), opened)
        assertEquals(0, compose.onAllNodesWithText("Rien pour l'instant", substring = true).fetchSemanticsNodes().size)
    }
}
