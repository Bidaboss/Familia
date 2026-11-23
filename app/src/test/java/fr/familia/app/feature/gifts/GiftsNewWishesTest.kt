package fr.familia.app.feature.gifts

import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The « nouvelles » of the Cadeaux tab. Fictional family of [TestFamily]. */
class GiftsNewWishesTest {

    private val family = TestFamily.family
    private val today = LocalDate.of(2026, 11, 21)

    private fun wishes(beneficiary: String) =
        GiftList("wishes-$beneficiary", GiftListKind.Wishes, "Mes envies", null, null, beneficiary, emptyList(), beneficiary, beneficiary)

    private fun item(id: String, author: String, at: Long?, visible: Boolean = true) =
        GiftItem(id, id, null, null, null, author, author, visible, addedAt = at?.let { Instant.ofEpochMilli(it) })

    private fun state(): GiftsUiState.Success {
        val lists = listOf(wishes("adult1"), wishes("adult2"))
        val base = giftsState(family, lists, "adult1", today) as GiftsUiState.Success
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
}
