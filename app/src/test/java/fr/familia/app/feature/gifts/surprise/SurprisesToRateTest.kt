package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.ProposalKind
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.SurpriseProposal
import fr.familia.app.core.model.SurpriseToRate
import fr.familia.app.core.model.toRateCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Accueil « À répondre » : « Surprise de X : n idées à noter ». */
class SurprisesToRateTest {
    private fun idea(id: String, author: String, removed: Boolean = false) =
        SurpriseProposal("idea-$id", ProposalKind.Idea, "Idée $id", 10.0, null, author, "Author", removed)

    private fun wish(id: String, removed: Boolean) =
        SurpriseProposal("wish-$id", ProposalKind.Wish, "Envie $id", 10.0, null, "adult2", "Adult2", removed)

    @Test
    fun `every wish still there and the ideas of the others, not the noted ones`() {
        val proposals = listOf(wish("w2", removed = true), idea("e1", "admin1"), idea("e2", "adult1"), idea("e3", "admin1", removed = true))
        // w1 and e1 to note, w3 already noted; w2 and e3 taken out, e2 is mine.
        assertEquals(2, toRateCount(listOf("w1", "w2", "w3"), proposals, mapOf("wish-w3" to 4), "adult1"))
    }

    @Test
    fun `the soonest first, never the day itself nor a surprise I declined`() = runTest {
        val today = LocalDate.of(2026, 11, 25)
        fun surprise(id: String, date: LocalDate) = Surprise(id, "adult2", "S $id", SurpriseOccasion.Other, date, listOf("adult1", "admin1"), "admin1", "Admin1")
        val repo = FakeSurprises(listOf(surprise("late", today.plusDays(30)), surprise("soon", today.plusDays(3)), surprise("day", today), surprise("out", today.plusDays(5))))
        repo.proposals.value = listOf("late", "soon", "day", "out").associateWith { listOf(idea("e-$it", "admin1")) }
        repo.rows.value = mapOf("out" to listOf(SurpriseMemberRow("adult1", declined = true)))
        assertEquals(
            listOf(SurpriseToRate("soon", "adult2", 1), SurpriseToRate("late", "adult2", 1)),
            observeSurprisesToRate(repo, null, "adult1", today).first(),
        )
        repo.ratings.value = mapOf("soon/adult1" to mapOf("idea-e-soon" to 5), "late/adult1" to mapOf("idea-e-late" to 3))
        assertTrue(observeSurprisesToRate(repo, null, "adult1", today).first().isEmpty())
    }
}
