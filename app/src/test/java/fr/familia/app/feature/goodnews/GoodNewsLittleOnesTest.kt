package fr.familia.app.feature.goodnews

import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.edit.GoodNewsEditUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditViewModel
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedUiState
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** « Le coin des petits » (PRD, récit 43): the label on a good news for a child, and its filter. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodNewsLittleOnesTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-25T10:00:00Z")

    private fun editor(repo: FakeGoodNews, me: String = "adult1") =
        GoodNewsEditViewModel(repo, FakeMembers(), FakeUploader(), me, null, flowOf(true))

    private fun editing(vm: GoodNewsEditViewModel) = vm.uiState.value as GoodNewsEditUiState.Editing

    private fun GoodNewsEditViewModel.fill() {
        setText("Un dessin de la mer")
        addPhotos(listOf("content://a"))
    }

    @Test
    fun `an adult puts the good news of a child in Les petits`() = runTest {
        val repo = FakeGoodNews()
        val vm = editor(repo)
        vm.fill()
        vm.setFor("kid1")
        vm.setLittleOnes(true)
        vm.save()
        assertTrue(repo.published.single().littleOnes)
    }

    @Test
    fun `never on one's own good news, even after going back to Pour moi`() = runTest {
        val repo = FakeGoodNews()
        val vm = editor(repo)
        vm.fill()
        vm.setLittleOnes(true)
        assertFalse(editing(vm).form.littleOnes)
        vm.setFor("kid1")
        vm.setLittleOnes(true)
        vm.setFor("adult1")
        assertFalse(editing(vm).form.littleOnes)
        vm.save()
        assertFalse(repo.published.single().littleOnes)
    }

    @Test
    fun `a child publishing for themselves has no Les petits`() = runTest {
        val repo = FakeGoodNews()
        val vm = editor(repo, me = "kid1")
        vm.fill()
        vm.setLittleOnes(true)
        vm.save()
        assertFalse(repo.published.single().littleOnes)
    }

    @Test
    fun `the filter keeps only Les petits, and lets go again`() = runTest {
        val repo = FakeGoodNews(
            listOf(
                goodNews("n1", TestFamily.adult, forMemberId = "kid1").copy(littleOnes = true),
                goodNews("n2", TestFamily.adult, daysAgo = 1),
                goodNews("n3", TestFamily.adult, forMemberId = "kid1", daysAgo = 2),
            ),
        )
        val vm = GoodNewsFeedViewModel(repo, FakeMembers(), "adult2") { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(3, (vm.uiState.value as GoodNewsFeedUiState.Success).items.size)
        vm.showLittleOnesOnly(true)
        val filtered = vm.uiState.value as GoodNewsFeedUiState.Success
        assertTrue(filtered.littleOnesOnly)
        assertEquals(listOf("n1"), filtered.items.map { it.news.id })
        vm.showLittleOnesOnly(false)
        assertEquals(3, (vm.uiState.value as GoodNewsFeedUiState.Success).items.size)
    }
}
