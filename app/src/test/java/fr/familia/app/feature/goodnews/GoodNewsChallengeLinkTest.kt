package fr.familia.app.feature.goodnews

import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.goodnews.edit.GoodNewsEditUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** A Bonne nouvelle linked to the challenge of a foyer (récit 35 bis). */
class GoodNewsChallengeLinkTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val january = YearMonth.of(2027, 1)

    private fun tree(challenge: MonthChallenge?) = object : TreeRepository {
        override fun observeTree(): Flow<FamilyTree> = flowOf(FamilyTree())
        override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = flowOf(challenge)
    }

    private val hosted = MonthChallenge(january, "15 photos de nos balades", "📸", 15, 2, false, hostHouseholdId = "h2")

    private fun editor(repo: FakeGoodNews, challenge: MonthChallenge?, linked: Boolean = false) =
        GoodNewsEditViewModel(repo, FakeMembers(), FakeUploader(), "adult1", null, flowOf(true), tree(challenge), january, linked)

    private fun editing(vm: GoodNewsEditViewModel) = vm.uiState.value as GoodNewsEditUiState.Editing

    @Test
    fun `from Participer au défi, the good news counts for the challenge of the foyer`() = runTest {
        val repo = FakeGoodNews()
        val vm = editor(repo, hosted, linked = true)
        assertTrue(editing(vm).form.forChallenge)
        vm.setText("Balade en forêt")
        vm.addPhotos(listOf("content://a"))
        vm.save()
        assertEquals("2027-01", repo.published.single().forChallenge)
    }

    @Test
    fun `unticked, or opened from elsewhere, it does not`() = runTest {
        val repo = FakeGoodNews()
        val vm = editor(repo, hosted)
        assertFalse(editing(vm).form.forChallenge)
        vm.setForChallenge(true)
        vm.setForChallenge(false)
        vm.setText("Premier vélo")
        vm.addPhotos(listOf("content://a"))
        vm.save()
        assertNull(repo.published.single().forChallenge)
    }

    @Test
    fun `no switch for a challenge of Familia, or one already achieved`() = runTest {
        val familia = MonthChallenge(january, "10 bonnes nouvelles", "✨", 10, 2, false)
        assertNull(editing(editor(FakeGoodNews(), familia, linked = true)).challenge)
        assertFalse(editing(editor(FakeGoodNews(), familia, linked = true)).form.forChallenge)
        assertNull(editing(editor(FakeGoodNews(), hosted.copy(achieved = true), linked = true)).challenge)
    }
}
