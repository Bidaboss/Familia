package fr.familia.app.feature.gifts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.TestFamily
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
 * The new Cadeaux tab (PRODUIT-119 and 120, maquettes C1 à C5). Fictional family only (TestFamily):
 * admin1 and adult1 in h1 with kid1, adult2 in h2 with kid2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class GiftsTabV2Test {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)

    /** adult2 celebrates in nine days, kid2 in June. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map {
            when (it.id) {
                "adult2" -> it.copy(birthDate = LocalDate.of(1988, 11, 27))
                "kid2" -> it.copy(birthDate = LocalDate.of(2016, 6, 3))
                else -> it
            }
        },
    )

    private fun stateFor(reader: String, day: LocalDate = today, tabV2: Boolean = true, singleList: Boolean = true): GiftsUiState.Success =
        (giftsState(family, emptyList(), reader, day, singleList = singleList, surpriseButton = true, tabV2 = tabV2) as GiftsUiState.Success)

    private fun show(
        state: GiftsUiState.Success,
        someListsHidden: Boolean = false,
        onOpenList: (String) -> Unit = {},
        onOpenMember: (String) -> Unit = {},
        onPicker: () -> Unit = {},
        onReceived: (() -> Unit)? = null,
    ) {
        compose.setContent {
            FamiliaTheme {
                GiftsScreen(
                    state, onOpenList = onOpenList, onNewList = {}, onRetry = {}, someListsHidden = someListsHidden,
                    onOpenSurprisePicker = onPicker, onOpenMyPage = {}, onOpenMember = onOpenMember, onOpenReceived = onReceived,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun button(label: String) = compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `the new tab needs its switch and the single list`() {
        assertTrue(stateFor("adult1").tabV2)
        assertFalse(stateFor("adult1", tabV2 = false).tabV2)
        assertFalse(stateFor("adult1", singleList = false).tabV2)
    }

    @Test
    fun `my household first, the others after`() {
        val state = stateFor("adult2")
        val blocks = householdBlocks(state.sections.filter { !it.isMe }, "h2")
        assertEquals(listOf("h2", "h1"), blocks.map { it.key })
        assertTrue(blocks.first().mine)
        assertEquals(listOf("kid2"), blocks.first().sections.map { it.member.id })
    }

    @Test
    fun `the birthdays of the next 30 days, nearest first, never mine`() {
        assertEquals(listOf("adult2"), birthdaysSoon(stateFor("adult1")).map { it.member.id })
        assertTrue(birthdaysSoon(stateFor("adult2")).none { it.member.id == "adult2" })
        assertTrue(birthdaysSoon(stateFor("adult1", day = LocalDate.of(2026, 7, 1))).isEmpty())
    }

    @Test
    fun `one row per person, by household, and nothing folded`() {
        show(stateFor("adult1"))
        assertEquals(1, count("Foyer Deux"))
        assertEquals(1, count("Toute la famille"))
        assertEquals(1, count("Admin1"))
        assertEquals(1, count("Kid2"))
        assertEquals(0, count("Idées"))
        assertEquals(0, count("Ses envies"))
    }

    @Test
    fun `touching a person opens their page, not their list`() {
        val members = mutableListOf<String>()
        val lists = mutableListOf<String>()
        show(stateFor("adult1"), onOpenList = { lists += it }, onOpenMember = { members += it })
        compose.onNodeWithText("Kid2").performClick()
        compose.waitForIdle()
        assertEquals(listOf("kid2"), members)
        assertTrue(lists.isEmpty())
    }

    @Test
    fun `the card says how many wishes, never ideas nor reservations`() {
        val base = stateFor("adult1")
        val wishes = fr.familia.app.core.model.GiftList(
            "wishes-admin1", fr.familia.app.core.model.GiftListKind.Wishes, "Mes envies", null, null, "admin1", emptyList(), "admin1", "Admin1",
        )
        val state = base.copy(
            sections = base.sections.map { if (it.member.id == "admin1") it.copy(wishes = wishes) else it },
            wishPreviews = mapOf("wishes-admin1" to WishPreview(6, listOf("Un livre"))),
        )
        show(state)
        assertEquals(1, count("6 envies"))
        assertTrue(count("Pas encore d'envie") > 0)
        assertEquals(0, count("idée"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Réserv"))
    }

    @Test
    fun `the pill says how many new wishes, only where there are some`() {
        val base = stateFor("adult1")
        val wishes = fr.familia.app.core.model.GiftList(
            "wishes-admin1", fr.familia.app.core.model.GiftListKind.Wishes, "Mes envies", null, null, "admin1", emptyList(), "admin1", "Admin1",
        )
        val state = base.copy(
            sections = base.sections.map { if (it.member.id == "admin1") it.copy(wishes = wishes) else it },
            wishPreviews = mapOf("wishes-admin1" to WishPreview(3, emptyList())),
            newWishes = mapOf("wishes-admin1" to 2),
        )
        show(state)
        assertEquals(1, count("2 nouvelles"))
        assertEquals(1, count("nouvelle"))
    }

    @Test
    fun `the birthday soon is a pill on a row that opens their page`() {
        val members = mutableListOf<String>()
        show(stateFor("adult1"), onOpenMember = { members += it })
        assertEquals(1, count("Bientôt"))
        assertEquals(1, count("dans 9 jours"))
        compose.onNodeWithText("dans 9 jours").performClick()
        compose.waitForIdle()
        assertEquals(listOf("adult2"), members)
    }

    @Test
    fun `out of season, no « Bientôt »`() {
        show(stateFor("adult1", day = LocalDate.of(2026, 3, 1)))
        assertEquals(0, count("Bientôt"))
    }

    @Test
    fun `a surprise in progress leads the tab, with « Préparer une surprise » in the same bar`() {
        val opened = mutableListOf<String>()
        show(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), onOpenList = { opened += it })
        assertEquals(1, count("1 surprise en préparation"))
        assertEquals(1, count("Préparer une surprise"))
        assertTrue(top("1 surprise en préparation") < top("Mes envies"))
        compose.onNodeWithText("1 surprise en préparation").performClick()
        compose.waitForIdle()
        assertEquals(listOf("ideas-adult2"), opened)
    }

    @Test
    fun `no floating button any more, and no surprise bar without the button`() {
        show(stateFor("adult1").copy(surpriseButton = false))
        assertEquals(0, count("Préparer une surprise"))
        assertEquals(0, count("en préparation"))
    }

    @Test
    fun `in « Voir comme », nothing of a surprise`() {
        show(stateFor("adult1").copy(surprisePreparedFor = setOf("adult2")), someListsHidden = true)
        assertEquals(0, count("en préparation"))
        assertEquals(0, count("Préparer une surprise"))
        assertEquals(0, count("Surprise"))
    }

    @Test
    fun `in « Voir comme » the person celebrated, nothing prepared nor given shows`() {
        show(stateFor("adult2").copy(surprisePreparedFor = setOf("adult1")), someListsHidden = true)
        assertEquals(0, count("Surprise"))
        assertEquals(0, count("préparation"))
        assertEquals(0, count("réserv"))
        assertEquals(0, count("Réserv"))
        assertEquals(0, count("idée"))
    }

    @Test
    fun `a child sees the wishes of the others, never a surprise nor ideas`() {
        show(stateFor("kid1"))
        assertEquals(1, count("Mes envies"))
        assertEquals(0, count("Surprise"))
        assertEquals(0, count("Idées"))
        assertEquals(0, count("Préparer une surprise"))
        assertEquals(1, count("Adult2"))
    }

    @Test
    fun `the lines at the bottom lead to what was received`() {
        var received = 0
        show(stateFor("adult1"), onReceived = { received++ })
        compose.onNodeWithText("Cadeaux reçus").performClick()
        assertEquals(1, received)
        compose.onAllNodesWithText("Cadeaux reçus").fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }

    @Test
    fun `without the feature, no line of received gifts`() {
        show(stateFor("adult1"))
        assertEquals(0, count("Cadeaux reçus"))
    }

    @Test
    fun `at 130 percent every target is big enough and named`() = compose.checkAccessibility {
        GiftsScreen(
            stateFor("adult1").copy(surprisePreparedFor = setOf("adult2"), newWishes = mapOf("wishes-admin1" to 1)),
            onOpenList = {}, onNewList = {}, onRetry = {}, onOpenSurprisePicker = {}, onOpenMyPage = {}, onOpenReceived = {},
        )
    }

    @Test
    fun `at 130 percent, a child too`() = compose.checkAccessibility {
        GiftsScreen(stateFor("kid1"), onOpenList = {}, onNewList = {}, onRetry = {}, onOpenMyPage = {})
    }
}
