package fr.familia.app.feature.feedback

import fr.familia.app.core.data.FeedbackRepository
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.feedbackOrder
import fr.familia.app.core.model.newCount
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private class FakeFeedback(initial: List<Feedback> = emptyList(), private val failWith: Exception? = null) : FeedbackRepository {
    val all = MutableStateFlow(initial)
    private var next = 0
    var lastScreenshot: ByteArray? = null
    var removedScreenshot: String? = null
    override suspend fun send(kind: Feedback.Kind, text: String, me: Member, context: FeedbackContext, screenshot: ByteArray?) {
        failWith?.let { throw it }
        lastScreenshot = screenshot
        val id = "n${next++}"
        val shot = screenshot?.let { Feedback.screenshotPath(me.id, id) }
        all.value = all.value + Feedback(id, kind, text, me.id, me.firstName, context, null, screenshot = shot)
    }
    override fun observeAll(): Flow<List<Feedback>> = all
    override fun observeMine(memberId: String): Flow<List<Feedback>> = all.map { list -> list.filter { it.authorId == memberId } }
    override suspend fun setStatus(id: String, status: Feedback.Status, note: String?) {
        failWith?.let { throw it }
        all.value = all.value.map { if (it.id == id) it.copy(status = status, note = note) else it }
    }
    override suspend fun delete(id: String, screenshot: String?) {
        failWith?.let { throw it }
        removedScreenshot = screenshot
        all.value = all.value.filter { it.id != id }
    }
}

private val camille = Member("m1", "Camille", null, null, "h1", MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)

private val context = FeedbackContext("1.4.0", "14", "Google Pixel 8", FeedbackOrigin.Help)

private fun item(id: String, status: Feedback.Status, second: Long?, author: String = "m1") =
    Feedback(id, Feedback.Kind.Idea, "Texte $id", author, "Camille", context, second?.let { Instant.ofEpochSecond(it) }, status)

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the counter counts the new ones, the newest come first`() {
        val all = listOf(
            item("a", Feedback.Status.Done, second = 10),
            item("b", Feedback.Status.New, second = 30),
            item("c", Feedback.Status.New, second = null),
        )
        assertEquals(2, newCount(all))
        assertEquals(listOf("c", "b", "a"), feedbackOrder(all).map { it.id })
    }

    @Test
    fun `a member sends a trimmed problem, with its context, then sees the thanks`() = runTest {
        val repo = FakeFeedback()
        val vm = FeedbackFormViewModel(repo, camille, context, writeTimeoutMillis = 1_000)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("   ")
        assertFalse(vm.uiState.value.canSend)
        vm.choose(Feedback.Kind.Problem)
        vm.type("  Le planning ne charge pas  ")
        vm.send()
        val sent = repo.all.value.single()
        assertEquals(Feedback.Kind.Problem, sent.kind)
        assertEquals("Le planning ne charge pas", sent.text)
        assertEquals(context, sent.context)
        assertEquals(Feedback.Status.New, sent.status)
        assertTrue(vm.uiState.value.sent)
        assertEquals("", vm.uiState.value.text)
        vm.again()
        assertFalse(vm.uiState.value.sent)
    }

    @Test
    fun `the text never goes past 1000 characters, and a failure is said`() = runTest {
        val vm = FeedbackFormViewModel(FakeFeedback(failWith = IllegalStateException("refusé")), camille, context, writeTimeoutMillis = 1_000)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("a".repeat(1200))
        assertEquals(Feedback.MAX_TEXT, vm.uiState.value.text.length)
        vm.send()
        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.sent)
    }

    @Test
    fun `the admin answers with a state and a trimmed word, an empty word is none`() = runTest {
        val repo = FakeFeedback(listOf(item("a", Feedback.Status.New, second = 10), item("b", Feedback.Status.New, second = 30)))
        val vm = FeedbackListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.answer("a", Feedback.Status.Planned, "  Pour la prochaine version  ")
        vm.answer("b", Feedback.Status.Declined, "   ")
        val byId = repo.all.value.associateBy { it.id }
        assertEquals(Feedback.Status.Planned, byId.getValue("a").status)
        assertEquals("Pour la prochaine version", byId.getValue("a").note)
        assertEquals(Feedback.Status.Declined, byId.getValue("b").status)
        assertNull(byId.getValue("b").note)
        vm.remove("a")
        assertEquals(listOf("b"), repo.all.value.map { it.id })
    }

    @Test
    fun `a refused answer is said`() = runTest {
        val vm = FeedbackListViewModel(FakeFeedback(listOf(item("a", Feedback.Status.New, 10)), failWith = IllegalStateException("refusé")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.answer("a", Feedback.Status.Done, "")
        assertTrue(vm.actionFailed.value)
        vm.failureShown()
        assertFalse(vm.actionFailed.value)
    }

    @Test
    fun `my requests show only mine`() = runTest {
        val repo = FakeFeedback(listOf(item("a", Feedback.Status.Done, 10), item("b", Feedback.Status.New, 20, author = "m2")))
        val vm = MyFeedbackViewModel(repo, "m1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("a"), (vm.uiState.value as FeedbackListState.Ready).items.map { it.id })
    }

    @Test
    fun `a screenshot is made small, then sent with the request, and taken off on removal`() = runTest {
        val repo = FakeFeedback()
        val encoded = mutableListOf<String>()
        val vm = FeedbackFormViewModel(
            repo, camille, context,
            encodeScreenshot = { uri -> encoded += uri; ByteArray(10) },
            writeTimeoutMillis = 1_000,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.canAddScreenshot)
        vm.type("La photo ne s'affiche pas")
        vm.pickScreenshot("content://media/1")
        assertEquals("content://media/1", vm.uiState.value.screenshot)
        vm.send()
        assertEquals(listOf("content://media/1"), encoded)
        assertEquals(10, repo.lastScreenshot?.size)
        val sent = repo.all.value.single()
        assertEquals("feedback/m1/${sent.id}.jpg", sent.screenshot)
        assertNull(vm.uiState.value.screenshot)

        val list = FeedbackListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.uiState.collect {} }
        list.remove(sent.id)
        assertEquals(sent.screenshot, repo.removedScreenshot)
    }

    @Test
    fun `without Storage there is no screenshot to add`() = runTest {
        val vm = FeedbackFormViewModel(FakeFeedback(), camille, context, writeTimeoutMillis = 1_000)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertFalse(vm.uiState.value.canAddScreenshot)
        vm.pickScreenshot("content://media/1")
        assertNull(vm.uiState.value.screenshot)
    }

    @Test
    fun `the text for Claude says what and where, never who`() {
        val text = feedbackForClaude(
            Feedback(
                "f1", Feedback.Kind.Problem, "Le planning reste blanc.", "m1", "Camille", context,
                Instant.parse("2026-11-12T13:05:00Z"), Feedback.Status.Planned, "Pour la prochaine version", screenshot = "feedback/m1/f1.jpg",
            ),
            java.time.ZoneId.of("Europe/Paris"),
        )
        assertTrue(text.contains("un problème"))
        assertTrue(text.contains("12 novembre 2026 à 14h05, depuis l'Aide"))
        assertTrue(text.contains("Familia 1.4.0 · Android 14 · Google Pixel 8"))
        assertTrue(text.contains("État : Prévue."))
        assertTrue(text.contains("Déjà répondu : Pour la prochaine version"))
        assertTrue(text.contains("capture d'écran"))
        assertTrue(text.contains("Administration > Retours"))
        val shared = feedbackForClaude(
            Feedback("f1", Feedback.Kind.Problem, "Le planning reste blanc.", "m1", "Camille", context, null, Feedback.Status.New, null, screenshot = "feedback/m1/f1.jpg"),
            imageAttached = true,
        )
        assertTrue(shared.contains("est jointe."))
        assertFalse(shared.contains("Administration > Retours"))
        assertTrue(text.contains("Le planning reste blanc."))
        assertFalse(text.contains("Camille"))
    }

    @Test
    fun `the phone name does not repeat its maker`() {
        assertEquals("Google Pixel 8", deviceName("Google", "Pixel 8"))
        assertEquals("Samsung SM-S911B", deviceName("samsung", "SM-S911B"))
        assertEquals("xiaomi 13", deviceName("Xiaomi", "xiaomi 13"))
        assertEquals("Pixel", deviceName("", "Pixel"))
    }
}
