package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.OfferedSurprise
import fr.familia.app.core.model.sortOffered
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Administration > « Surprises offertes récemment » (brief § 9). */
class OfferedSurprisesTest {
    private fun offered(id: String, at: String?, cancelAsked: Boolean = false) =
        OfferedSurprise(id, "Surprise $id", LocalDate.of(2026, 11, 20), "Adult2", at?.let { Instant.parse(it) }, cancelAsked)

    @Test
    fun `the last offered first, one still being written last`() {
        val list = sortOffered(listOf(offered("a", "2026-11-01T10:00:00Z"), offered("b", null), offered("c", "2026-11-20T10:00:00Z")))
        assertEquals(listOf("c", "a", "b"), list.map { it.id })
    }

    @Test
    fun `cancel asks once and the card says so`() = runTest {
        val repo = FakeSurprises()
        repo.offeredList.value = listOf(offered("a", "2026-11-20T10:00:00Z"))
        repo.askCancelOffered("a")
        assertEquals(listOf("a"), repo.cancelAsked)
        assertTrue(repo.observeOffered("admin1").first().single().cancelAsked)
    }
}
