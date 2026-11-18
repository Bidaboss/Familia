package fr.familia.app.feature.tree.challenges

import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.ChallengeMeasure
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.feature.tree.TreeViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** « Comment ça marche » (PRD récit 65): what counts, the goal, until when, and the useful button. */
class ChallengeDetailsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val december = YearMonth.of(2026, 12)
    private val today = LocalDate.of(2026, 12, 9)

    private fun challenge(measure: ChallengeMeasure, progress: Int = 7, achieved: Boolean = false, host: String? = null, month: YearMonth = december) =
        MonthChallenge(month, "20 photos de Noël", "🎄", 20, progress, achieved, hostHouseholdId = host, measure = measure)

    @Test
    fun `a challenge of photos says what counts, where the family is and until when`() {
        val e = explain(challenge(ChallengeMeasure.Photos), today, hostNote = null, canShareGoodNews = true)
        assertEquals(ChallengeMeasure.Photos, e.measure)
        assertEquals(7, e.progress)
        assertEquals(20, e.target)
        assertEquals(LocalDate.of(2026, 12, 31), e.endsOn)
        assertEquals(ChallengeAction.ShareGoodNews, e.action)
    }

    @Test
    fun `the challenge of a foyer asks for my photo and carries its word`() {
        val e = explain(challenge(ChallengeMeasure.Linked, host = "h2"), today, hostNote = "  Vos plus beaux sapins  ", canShareGoodNews = true)
        assertEquals(ChallengeAction.AddPhoto, e.action)
        assertEquals("Vos plus beaux sapins", e.hostNote)
        // Without a foyer, no word: the automatic text.
        assertNull(explain(challenge(ChallengeMeasure.Photos), today, hostNote = "Un mot", canShareGoodNews = true).hostNote)
        assertNull(explain(challenge(ChallengeMeasure.Linked, host = "h2"), today, hostNote = "  ", canShareGoodNews = true).hostNote)
    }

    @Test
    fun `achieved, it says so instead of a button, and a month gone by has no end date`() {
        assertEquals(ChallengeAction.Done, explain(challenge(ChallengeMeasure.GoodNews, 20, achieved = true), today, null, true).action)
        val past = explain(challenge(ChallengeMeasure.GoodNews, 20, achieved = true, month = YearMonth.of(2026, 11)), today, null, false)
        assertNull(past.endsOn)
        assertEquals(ChallengeAction.Done, past.action)
        // Without Bonnes nouvelles, no button that leads nowhere.
        assertEquals(ChallengeAction.None, explain(challenge(ChallengeMeasure.GoodNews), today, null, canShareGoodNews = false).action)
    }

    @Test
    fun `before the first challenge and the first question, the sheet gives the day they start`() {
        val before = LocalDate.of(2026, 11, 21)
        assertEquals(ChallengeSheet.ChallengePrinciple(LocalDate.of(2026, 12, 1)), challengePrinciple(before))
        assertEquals(ChallengeSheet.QuestionPrinciple(LocalDate.of(2026, 11, 30)), questionPrinciple(before))
        assertEquals(ChallengeSheet.ChallengePrinciple(null), challengePrinciple(today))
        assertEquals(ChallengeSheet.QuestionPrinciple(null), questionPrinciple(today))
    }

    @Test
    fun `the word of the foyer comes only from the foyer that launched the challenge, once published`() = runTest {
        val hosted = MutableStateFlow(
            HostedChallenge(december, "h2", HostedStatus.Draft, HostedKind.Photo, "Nos sapins", "🎄", HostedIllustration.Star, 10, note = "Vos plus beaux sapins"),
        )
        val repo = object : TreeRepository {
            override fun observeTree(): Flow<FamilyTree> = flowOf(FamilyTree())
            override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = flowOf(challenge(ChallengeMeasure.Linked, host = "h2"))
            override fun observeHosted(month: YearMonth): Flow<HostedChallenge?> = hosted
        }
        val vm = TreeViewModel(repo, today)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value?.hostNote)
        hosted.value = hosted.value.copy(status = HostedStatus.Published)
        assertEquals("Vos plus beaux sapins", vm.uiState.value?.hostNote)
        hosted.value = hosted.value.copy(householdId = "h3")
        assertNull(vm.uiState.value?.hostNote)
    }
}
