package fr.familia.app.feature.gifts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.listsSeenBy
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.edit.nearestOccasionOf
import fr.familia.app.feature.gifts.surprise.surprisePickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * « Préparer une surprise » depuis un seul bouton (PRD rév. 287, récit 63): the button, the choice of
 * the person, the nearest occasion, and « Surprises en préparation », never seen by the person
 * celebrated. Fictional family only (TestFamily).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class SurpriseButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 15)

    /** adult1 has a birthday in five days; adult2 prepares a surprise for it. */
    private val family = TestFamily.family.copy(
        members = TestFamily.family.members.map { if (it.id == "adult1") it.copy(birthDate = LocalDate.of(1990, 11, 20)) else it },
    )
    private val surprise = GiftList(
        "s1", GiftListKind.Surprise, "Surprise s1", GiftOccasion.Birthday, LocalDate.of(2026, 11, 20),
        "adult1", listOf("admin1", "adult2"), "adult2", "Adult2", false,
    )

    private fun stateFor(reader: String, button: Boolean = true) =
        giftsState(family, listsSeenBy(listOf(surprise), reader), reader, today, surpriseButton = button) as GiftsUiState.Success

    private fun show(state: GiftsUiState.Success, onPicker: () -> Unit = {}) {
        compose.setContent {
            FamiliaTheme {
                GiftsScreen(state, onOpenList = {}, onNewList = {}, onRetry = {}, onOpenSurprisePicker = onPicker)
            }
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `the giver sees the surprise in progress`() {
        val lines = preparingLines(stateFor("adult2"))
        assertEquals(listOf("s1"), lines.map { it.key })
    }

    @Test
    fun `the person celebrated sees nothing`() {
        assertTrue(preparingLines(stateFor("adult1")).isEmpty())
        show(stateFor("adult1"))
        assertEquals(0, count("Surprises en préparation"))
        assertEquals(0, count("Surprise s1"))
    }

    @Test
    fun `the section and the button show for the giver`() {
        var opened = 0
        show(stateFor("adult2"), onPicker = { opened++ })
        assertEquals(1, count("Surprises en préparation"))
        // In « Surprises en préparation », and maybe again among the lists of the person further down.
        assertTrue(count("Surprise s1") >= 1)
        compose.onNodeWithContentDescription("Préparer une surprise").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `switched off, the tab stays as it was`() {
        show(stateFor("adult2", button = false))
        assertEquals(0, count("Surprises en préparation"))
        assertEquals(0, compose.onAllNodesWithContentDescription("Préparer une surprise").fetchSemanticsNodes().size)
        assertEquals(1, count("Préparer une surprise"))
    }

    @Test
    fun `with the button, every other member is read for a surprise idea`() {
        val state = giftsState(family, emptyList(), "adult2", today, singleList = true, surpriseButton = true) as GiftsUiState.Success
        assertEquals(setOf("admin1", "adult1", "kid1", "kid2"), surpriseIdeasToRead(state).toSet())
        val ideas = preparingLines(state.copy(surprisePreparedFor = setOf("adult1")))
        assertEquals(listOf("ideas-adult1"), ideas.map { it.key })
    }

    @Test
    fun `the choice of the person leaves me out, the next birthday first`() {
        val pick = surprisePickState(family, "adult2", today)
        assertEquals("adult1", pick.next?.member?.id)
        val everyone = pick.groups.flatMap { group -> group.members.map { it.id } }
        assertTrue("adult2" !in everyone)
        assertEquals(listOf("Foyer Deux", "Foyer Un"), pick.groups.map { it.name })
    }

    @Test
    fun `the nearest occasion is chosen`() {
        val adult1 = family.members.first { it.id == "adult1" }
        assertEquals(GiftOccasion.Birthday to LocalDate.of(2026, 11, 20), nearestOccasionOf(adult1, today))
        val later = adult1.copy(birthDate = LocalDate.of(1990, 2, 3))
        assertEquals(GiftOccasion.Christmas to LocalDate.of(2026, 12, 25), nearestOccasionOf(later, today))
        val none = adult1.copy(birthDate = null)
        assertEquals(GiftOccasion.Christmas to LocalDate.of(2027, 12, 25), nearestOccasionOf(none, LocalDate.of(2026, 12, 26)))
    }

    private fun topOf(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    /** The presentation R1 bis, with the single list: a surprise is an idea of the family. */
    private fun presentationFor(reader: String) =
        (giftsState(family, emptyList(), reader, today, singleList = true, presentation = true, surpriseButton = true) as GiftsUiState.Success)
            .copy(surprisePreparedFor = if (reader == "adult1") emptySet() else setOf("adult1"))

    @Test
    fun `the surprises in progress come first, above the birthdays`() {
        show(stateFor("adult2"))
        assertTrue(topOf("Surprises en préparation") < topOf("Anniversaires à venir"))
    }

    @Test
    fun `in the presentation, the surprises then one block for what comes`() {
        show(presentationFor("adult2"))
        assertTrue(topOf("Surprises en préparation") < topOf("À venir"))
        assertTrue(topOf("À venir") < topOf("PROCHAINE OCCASION"))
        // The next occasion (its label in capitals) is the first card of the block, never a separate section of birthdays.
        assertEquals(0, count("Anniversaires à venir"))
        assertEquals(1, count("Anniversaire de Adult1"))
    }

    @Test
    fun `after the next occasion, the birthdays and Christmas by date`() {
        val state = presentationFor("adult2")
        val next = state.next as GiftMoment.BirthdayOf
        assertEquals("adult1", next.member.id)
        // Christmas shows from the 1st of November, not on the 31st of October.
        assertTrue(upcomingAfterNext(state.copy(today = LocalDate.of(2026, 10, 31))).none { it is Upcoming.OfChristmas })
        val after = upcomingAfterNext(state)
        assertTrue(after.none { it is Upcoming.OfBirthday && it.birthday.member.id == "adult1" })
        assertTrue(after.any { it is Upcoming.OfChristmas && it.year == 2026 })
        assertEquals(after.sortedBy { it.date }, after)
    }

    @Test
    fun `the person celebrated reads their own birthday, and no surprise`() {
        show(presentationFor("adult1"))
        assertEquals(1, count("Ton anniversaire, dans 5 jours"))
        assertEquals(0, count("Surprises en préparation"))
    }

    @Test
    fun `after the next occasion, no birthday further than 30 days`() {
        val state = presentationFor("adult2")
        val far = state.birthdays.first().copy(date = today.plusDays(150))
        val edge = state.birthdays.first().copy(date = today.plusDays(30))
        val after = upcomingAfterNext(state.copy(birthdays = listOf(far)))
        assertTrue(after.none { it is Upcoming.OfBirthday })
        val withEdge = upcomingAfterNext(state.copy(birthdays = listOf(edge, far)))
        assertEquals(listOf(today.plusDays(30)), withEdge.filterIsInstance<Upcoming.OfBirthday>().map { it.date })
    }
}
