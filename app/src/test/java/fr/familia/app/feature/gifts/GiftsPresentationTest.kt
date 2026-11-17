package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The Cadeaux tab in the presentation R1 bis (DESIGN-14). Fictional family of [TestFamily]. */
class GiftsPresentationTest {

    private val family = TestFamily.family
    private val today = LocalDate.of(2026, 11, 21)

    private fun withBirthday(date: LocalDate?) = family.members.map { if (it.id == "adult2") it.copy(birthDate = date) else it.copy(birthDate = null) }

    @Test
    fun `the birthday of the day never shows as the next occasion`() {
        // The party is today: the card goes on to the next occasion.
        assertEquals(GiftMoment.Christmas(2026), nextOccasion(today, withBirthday(LocalDate.of(1980, 11, 21))))
        val tomorrow = nextOccasion(today, withBirthday(LocalDate.of(1980, 11, 22)))
        assertTrue(tomorrow is GiftMoment.BirthdayOf && tomorrow.member.id == "adult2" && tomorrow.date == LocalDate.of(2026, 11, 22))
    }

    @Test
    fun `Christmas comes next when no birthday comes before it, and the next one on Christmas day`() {
        assertEquals(GiftMoment.Christmas(2026), nextOccasion(today, withBirthday(LocalDate.of(1980, 2, 3))))
        assertEquals(GiftMoment.Christmas(2027), nextOccasion(LocalDate.of(2026, 12, 25), withBirthday(null)))
        assertEquals(LocalDate.of(2026, 12, 25), GiftMoment.Christmas(2026).day())
    }

    private fun wishes(beneficiary: String) =
        GiftList("wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary)

    private fun item(id: String, author: String, at: Long?, visible: Boolean = true) =
        GiftItem(id, id, null, null, null, author, author, visible, addedAt = at?.let { Instant.ofEpochMilli(it) })

    private fun state(): GiftsUiState.Success {
        val lists = listOf(wishes("adult1"), wishes("adult2"))
        val base = giftsState(family, lists, "adult1", today, singleList = true, presentation = true) as GiftsUiState.Success
        return base.copy(
            listItems = mapOf(
                "wishes-adult2" to listOf(
                    item("old", "adult2", 1_000),
                    item("new1", "adult2", 3_000),
                    item("new2", "adult2", 4_000),
                    item("mine", "adult1", 5_000), // Added by me: not news for me.
                    item("idea", "kid1", 6_000, visible = false), // A surprise idea: never counted.
                ),
                "wishes-adult1" to listOf(item("x", "adult2", 9_000)),
            ),
        )
    }

    @Test
    fun `the news of a list are the wishes its person sees, added by someone else since my last visit`() {
        val shown = withNewWishes(state(), mapOf("wishes-adult2" to 2_000L, "wishes-adult1" to 0L), "adult1") as GiftsUiState.Success
        assertEquals(mapOf("wishes-adult2" to 2), shown.newWishes) // Never my own list.
    }

    @Test
    fun `a list never opened on this phone has no news yet`() {
        val shown = withNewWishes(state(), emptyMap(), "adult1") as GiftsUiState.Success
        assertTrue(shown.newWishes.isEmpty())
    }

    @Test
    fun `the new presentation needs the single list, and says what comes next`() {
        val on = giftsState(family, emptyList(), "adult1", today, singleList = true, presentation = true) as GiftsUiState.Success
        assertTrue(on.presentation)
        assertEquals(nextOccasion(today, family.members), on.next)
        val withoutSingleList = giftsState(family, emptyList(), "adult1", today, singleList = false, presentation = true) as GiftsUiState.Success
        assertFalse(withoutSingleList.presentation)
        assertNull(withoutSingleList.next)
    }
}
