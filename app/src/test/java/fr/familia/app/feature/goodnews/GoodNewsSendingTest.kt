package fr.familia.app.feature.goodnews

import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.goodnews.edit.GoodNewsEditUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditViewModel
import fr.familia.app.feature.goodnews.edit.PublishFailure
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Sending the photo of the meal on a weak or cut network: it waits, goes on, and never doubles. */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodNewsSendingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val online = MutableStateFlow(true)
    private val repo = FakeGoodNews()
    private val uploader = FakeUploader()

    private fun editor() = GoodNewsEditViewModel(repo, FakeMembers(), uploader, "adult1", null, online).apply {
        setText("Le repas")
        addPhotos(listOf("content://a", "content://b"))
    }

    private val GoodNewsEditViewModel.editing get() = uiState.value as GoodNewsEditUiState.Editing

    @Test
    fun `without network the send waits, then goes by itself when the network is back`() = runTest {
        online.value = false
        val viewModel = editor()
        viewModel.save()
        assertTrue(viewModel.editing.offline)
        assertEquals(1, viewModel.editing.sendingPhoto)
        assertTrue(uploader.tries.isEmpty())

        online.value = true
        advanceUntilIdle()
        assertEquals(GoodNewsEditUiState.Done, viewModel.uiState.value)
        assertEquals(1, repo.published.size)
        assertEquals(listOf("content://a", "content://b"), uploader.sent.map { it.first })
    }

    @Test
    fun `a network cut after the first photo waits, then sends only the second`() = runTest {
        val viewModel = editor()
        uploader.gate = CompletableDeferred()
        viewModel.save()
        // The first photo is halfway: the bar shows a quarter of the whole.
        assertEquals(0.25f, viewModel.editing.progress!!, 0.001f)

        uploader.failures += RuntimeException("Network cut")
        online.value = false
        uploader.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.editing.offline)
        assertEquals(2, viewModel.editing.sendingPhoto)

        online.value = true
        advanceUntilIdle()
        assertEquals(GoodNewsEditUiState.Done, viewModel.uiState.value)
        assertEquals(listOf("content://a", "content://b", "content://b"), uploader.tries)
        assertEquals(1, repo.published.size)
    }

    @Test
    fun `after a failure, publishing again keeps the same good news and the photos already sent`() = runTest {
        val viewModel = editor()
        val held = CompletableDeferred<Unit>()
        uploader.gate = held
        viewModel.save()
        // The first photo goes; the second fails three times with the network on.
        uploader.gate = null
        uploader.failures += listOf(RuntimeException("1"), RuntimeException("2"), RuntimeException("3"))
        held.complete(Unit)
        advanceUntilIdle()
        val failed = viewModel.editing
        assertEquals(PublishFailure.Failed, failed.failure)
        assertNull(failed.sendingPhoto)
        assertEquals("Le repas", failed.form.text)
        assertTrue(repo.published.isEmpty())

        viewModel.dismissFailure()
        viewModel.save()
        advanceUntilIdle()
        assertEquals(GoodNewsEditUiState.Done, viewModel.uiState.value)
        val news = repo.published.single()
        assertEquals(listOf("content://a", "content://b"), uploader.sent.map { it.first })
        assertEquals(news.photoPath(news.photoIds[0]), uploader.sent[0].second)
        assertEquals(news.photoPath(news.photoIds[1]), uploader.sent[1].second)
    }

    @Test
    fun `cancelling the send gives the form back and publishes nothing`() = runTest {
        val viewModel = editor()
        uploader.gate = CompletableDeferred()
        viewModel.save()
        assertTrue(viewModel.editing.isSaving)

        viewModel.cancelSending()
        val state = viewModel.editing
        assertFalse(state.isSaving)
        assertNull(state.failure)
        assertEquals(2, state.form.photos.size)
        advanceUntilIdle()
        assertTrue(repo.published.isEmpty())
    }
}
