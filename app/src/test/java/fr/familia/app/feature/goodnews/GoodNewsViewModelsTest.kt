package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailUiState
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailViewModel
import fr.familia.app.feature.goodnews.edit.GoodNewsEditUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditViewModel
import fr.familia.app.feature.goodnews.edit.GoodNewsFormError
import fr.familia.app.feature.goodnews.edit.PublishFailure
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedUiState
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedViewModel
import fr.familia.app.feature.goodnews.home.GoodNewsHomeUiState
import fr.familia.app.feature.goodnews.home.GoodNewsHomeViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoodNewsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- The feed --------------------------------------------------------------------------------

    @Test
    fun `the feed shows the newest first, by pages of 20`() = runTest {
        val repo = FakeGoodNews((1..25).map { goodNews("n$it", TestFamily.adult, daysAgo = it.toLong()) })
        val viewModel = GoodNewsFeedViewModel(repo, FakeMembers(), "adult2") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val first = viewModel.uiState.value as GoodNewsFeedUiState.Success
        assertEquals(GoodNews.PAGE_SIZE, first.items.size)
        assertEquals("n1", first.items.first().news.id)
        assertTrue(first.canLoadMore)
        viewModel.loadMore()
        val second = viewModel.uiState.value as GoodNewsFeedUiState.Success
        assertEquals(25, second.items.size)
        assertFalse(second.canLoadMore)
    }

    @Test
    fun `the member it is for hides it for themselves, the others still see it`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult, forMemberId = "adult2"), goodNews("n2", TestFamily.adult)))
        val viewModel = GoodNewsFeedViewModel(repo, FakeMembers(), "adult2") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val before = viewModel.uiState.value as GoodNewsFeedUiState.Success
        assertTrue(before.items.first { it.news.id == "n1" }.canHide)
        assertFalse(before.items.first { it.news.id == "n2" }.canHide)
        viewModel.hide("n1")
        assertEquals(listOf("adult2"), repo.posts.value.first { it.id == "n1" }.hiddenByIds)
        assertEquals(listOf("n2"), (viewModel.uiState.value as GoodNewsFeedUiState.Success).items.map { it.news.id })
        val other = GoodNewsFeedViewModel(repo, FakeMembers(), TestFamily.adult.id) { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect {} }
        assertEquals(2, (other.uiState.value as GoodNewsFeedUiState.Success).items.size)
    }

    @Test
    fun `a reaction is set, changed, then taken back`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult)))
        val viewModel = GoodNewsFeedViewModel(repo, FakeMembers(), "adult2") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.react("n1", Reaction.Heart)
        assertEquals(mapOf("adult2" to Reaction.Heart), repo.posts.value.single().reactions)
        viewModel.react("n1", Reaction.Party)
        assertEquals(mapOf("adult2" to Reaction.Party), repo.posts.value.single().reactions)
        viewModel.react("n1", Reaction.Party)
        assertTrue(repo.posts.value.single().reactions.isEmpty())
    }

    @Test
    fun `a failed action says so`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult)), failWith = IllegalStateException("offline"))
        val viewModel = GoodNewsFeedViewModel(repo, FakeMembers(), "adult1") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.remove("n1")
        assertTrue((viewModel.uiState.value as GoodNewsFeedUiState.Success).actionFailed)
        viewModel.dismissError()
        assertFalse((viewModel.uiState.value as GoodNewsFeedUiState.Success).actionFailed)
    }

    // --- One good news ---------------------------------------------------------------------------

    @Test
    fun `a good news removed meanwhile is missing`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.kid)))
        val viewModel = GoodNewsDetailViewModel(repo, FakeMembers(), "adult1", "n1") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue((viewModel.uiState.value as GoodNewsDetailUiState.Success).item.canRemove)
        viewModel.remove()
        assertEquals(GoodNewsDetailUiState.Missing, viewModel.uiState.value)
    }

    // --- The home card ---------------------------------------------------------------------------

    @Test
    fun `the home card keeps the last three of the last 14 days`() = runTest {
        val repo = FakeGoodNews(
            listOf(
                goodNews("old", TestFamily.adult, daysAgo = 15),
                goodNews("a", TestFamily.adult, daysAgo = 1),
                goodNews("b", TestFamily.adult, daysAgo = 2),
                goodNews("c", TestFamily.adult, daysAgo = 3),
                goodNews("d", TestFamily.adult, daysAgo = 4),
            ),
        )
        val viewModel = GoodNewsHomeViewModel(repo, FakeMembers(), "adult2") { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(listOf("a", "b", "c"), (viewModel.uiState.value as GoodNewsHomeUiState.Ready).items.map { it.news.id })
        repo.posts.value = listOf(goodNews("old", TestFamily.adult, daysAgo = 15))
        assertTrue((viewModel.uiState.value as GoodNewsHomeUiState.Ready).items.isEmpty())
    }

    // --- Publishing ------------------------------------------------------------------------------

    private fun editor(repo: FakeGoodNews, uploader: FakeUploader = FakeUploader(), me: String = "adult1", postId: String? = null, online: Boolean = true) =
        GoodNewsEditViewModel(repo, FakeMembers(), uploader, me, postId, flowOf(online))

    @Test
    fun `publishing sends each photo and its thumbnail, then writes the good news`() = runTest {
        val repo = FakeGoodNews()
        val uploader = FakeUploader()
        val viewModel = editor(repo, uploader)
        viewModel.setText("  Premier vélo  ")
        viewModel.addPhotos(listOf("content://a", "content://b"))
        viewModel.setFor("kid1")
        viewModel.toggleMention("adult2")
        viewModel.save()
        assertEquals(GoodNewsEditUiState.Done, viewModel.uiState.value)
        val news = repo.published.single()
        assertEquals("Premier vélo", news.text)
        assertEquals("kid1", news.forMemberId)
        assertEquals("h1", news.householdId)
        assertEquals("Adult1", news.authorName)
        assertEquals(listOf("adult2"), news.mentionIds)
        assertEquals(2, news.photoIds.size)
        assertEquals(
            listOf(
                Triple("content://a", news.photoPath(news.photoIds[0]), news.thumbnailPath(news.photoIds[0])),
                Triple("content://b", news.photoPath(news.photoIds[1]), news.thumbnailPath(news.photoIds[1])),
            ),
            uploader.sent,
        )
        assertTrue(news.photoPath(news.photoIds[0]).startsWith("goodNews/adult1/${news.id}/"))
    }

    @Test
    fun `nothing is sent without a text and a photo`() = runTest {
        val repo = FakeGoodNews()
        val viewModel = editor(repo)
        viewModel.save()
        val state = viewModel.uiState.value as GoodNewsEditUiState.Editing
        assertEquals(setOf(GoodNewsFormError.TextMissing, GoodNewsFormError.NoPhoto), state.errors)
        assertTrue(repo.published.isEmpty())
    }

    @Test
    fun `a child publishes only for themselves`() = runTest {
        val viewModel = editor(FakeGoodNews(), me = "kid1")
        viewModel.setFor("adult1")
        assertEquals("kid1", (viewModel.uiState.value as GoodNewsEditUiState.Editing).form.forMemberId)
    }

    @Test
    fun `offline, or with a photo that cannot be read, nothing is published`() = runTest {
        val offlineRepo = FakeGoodNews()
        val offline = editor(offlineRepo, online = false)
        offline.setText("Bravo")
        offline.addPhotos(listOf("content://a"))
        offline.save()
        assertEquals(PublishFailure.NeedsNetwork, (offline.uiState.value as GoodNewsEditUiState.Editing).failure)
        assertTrue(offlineRepo.published.isEmpty())

        val repo = FakeGoodNews()
        val broken = editor(repo, FakeUploader(unreadable = true))
        broken.setText("Bravo")
        broken.addPhotos(listOf("content://a"))
        broken.save()
        val state = broken.uiState.value as GoodNewsEditUiState.Editing
        assertEquals(PublishFailure.PhotoUnreadable, state.failure)
        assertNull(state.sendingPhoto)
        assertTrue(repo.published.isEmpty())
    }

    @Test
    fun `the author changes the text and the members named, not the photos`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult)))
        val viewModel = editor(repo, postId = "n1")
        val editing = viewModel.uiState.value as GoodNewsEditUiState.Editing
        assertFalse(editing.isNew)
        assertFalse(editing.canAddPhotos)
        viewModel.addPhotos(listOf("content://x"))
        viewModel.setText("Nouveau texte")
        viewModel.toggleMention("kid1")
        viewModel.save()
        assertEquals(GoodNewsEditUiState.Done, viewModel.uiState.value)
        val news = repo.posts.value.single()
        assertEquals("Nouveau texte", news.text)
        assertEquals(listOf("kid1"), news.mentionIds)
        assertEquals(listOf("p1"), news.photoIds)
    }

    @Test
    fun `someone else's good news cannot be changed`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult)))
        assertEquals(GoodNewsEditUiState.Unavailable, editor(repo, me = "admin1", postId = "n1").uiState.value)
    }
}
