package fr.familia.app.feature.events.comments

import fr.familia.app.core.data.CommentPage
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.eventCommentOrder
import fr.familia.app.feature.events.FakeMembers
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

private class FakeComments(initial: List<EventComment> = emptyList(), private val failWith: Exception? = null) : EventCommentRepository {
    val comments = MutableStateFlow(initial)
    val mutes = MutableStateFlow(emptySet<String>())
    private var next = 0
    override fun observe(eventId: String, limit: Int): Flow<CommentPage> = comments.map { all ->
        val sorted = all.sortedWith(eventCommentOrder)
        CommentPage(sorted.takeLast(limit), hasMore = sorted.size > limit)
    }
    override suspend fun add(eventId: String, text: String, me: Member, mentionIds: List<String>) {
        failWith?.let { throw it }
        comments.value = comments.value + EventComment("n${next++}", text, me.id, me.firstName, null, mentionIds = mentionIds)
    }
    override suspend fun edit(eventId: String, commentId: String, text: String, mentionIds: List<String>) {
        failWith?.let { throw it }
        comments.value = comments.value.map { if (it.id == commentId) it.copy(text = text, editedAt = Instant.EPOCH, mentionIds = mentionIds) else it }
    }
    override suspend fun delete(eventId: String, commentId: String) {
        failWith?.let { throw it }
        comments.value = comments.value.filter { it.id != commentId }
    }
    override fun observeMuted(eventId: String, memberId: String): Flow<Boolean> = mutes.map { memberId in it }
    override suspend fun setMuted(eventId: String, memberId: String, muted: Boolean) {
        failWith?.let { throw it }
        mutes.value = if (muted) mutes.value + memberId else mutes.value - memberId
    }
}

private fun comment(id: String, author: String, minute: Long) =
    EventComment(id, "Texte $id", author, author.replaceFirstChar { it.uppercase() }, Instant.ofEpochSecond(minute * 60))

@OptIn(ExperimentalCoroutinesApi::class)
class EventCommentsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeComments, me: String) =
        EventCommentsViewModel(repo, FakeMembers(), me, "e1", writeTimeoutMillis = 1_000)

    @Test
    fun `a member sends a trimmed comment, signed with their name, and the draft empties`() = runTest {
        val repo = FakeComments()
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.rows.isEmpty())
        vm.type("   ")
        assertFalse(vm.uiState.value.canSend)
        vm.type("  On apporte les chaises  ")
        vm.send()
        val row = vm.uiState.value.rows.single()
        assertEquals("On apporte les chaises", row.comment.text)
        assertEquals("Adult1", row.comment.authorName)
        assertEquals("", vm.uiState.value.draft)
        assertTrue(row.canEdit)
        assertTrue(row.canRemove)
    }

    @Test
    fun `the members named with an at sign go with the comment, then the draft forgets them`() = runTest {
        val repo = FakeComments()
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("@Adult2 tu viens ?", listOf("adult2", "adult2"))
        assertEquals(listOf("adult2"), vm.uiState.value.draftMentionIds)
        vm.send()
        assertEquals(listOf("adult2"), vm.uiState.value.rows.single().comment.mentionIds)
        assertEquals(emptyList<String>(), vm.uiState.value.draftMentionIds)
    }

    @Test
    fun `changing a comment changes who is named`() = runTest {
        val repo = FakeComments(listOf(comment("c1", "adult1", 1)))
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val row = vm.uiState.value.rows.single()
        assertTrue(vm.edit(row, row.comment.text, listOf("adult2")))
        assertEquals(listOf("adult2"), vm.uiState.value.rows.single().comment.mentionIds)
    }

    @Test
    fun `the draft never goes past 500 characters`() = runTest {
        val vm = viewModel(FakeComments(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("a".repeat(600))
        assertEquals(EventComment.MAX_TEXT, vm.uiState.value.draft.length)
    }

    @Test
    fun `rights follow the rules - author, admin principal, chef of the author's household`() = runTest {
        // adult1 and kid1 live in h1 with admin1 (admin principal and chef); adult2 lives in h2.
        val repo = FakeComments(listOf(comment("a", "adult1", 1), comment("b", "adult2", 2), comment("c", "gone", 3)))
        fun rights(me: String): List<Pair<Boolean, Boolean>> {
            val vm = viewModel(repo, me)
            var rows: List<EventCommentRow> = emptyList()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { rows = it.rows } }
            return rows.map { it.canEdit to it.canRemove }
        }
        assertEquals(listOf(true to true, false to false, false to false), rights("adult1"))
        assertEquals(listOf(false to true, false to true, false to true), rights("admin1"))
        assertEquals(listOf(false to false, true to true, false to false), rights("adult2"))
    }

    @Test
    fun `30 comments show first, the most recent, and « Voir plus » adds the older ones`() = runTest {
        val repo = FakeComments((1..45L).map { comment("c$it", "adult2", it) })
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(30, vm.uiState.value.rows.size)
        assertEquals("c16", vm.uiState.value.rows.first().comment.id)
        assertEquals("c45", vm.uiState.value.rows.last().comment.id)
        assertTrue(vm.uiState.value.hasMore)
        vm.showMore()
        assertEquals(45, vm.uiState.value.rows.size)
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test
    fun `its author edits a comment, never someone else's, and an empty text is refused`() = runTest {
        val repo = FakeComments(listOf(comment("a", "adult1", 1), comment("b", "adult2", 2)))
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val (mine, theirs) = vm.uiState.value.rows
        assertFalse(vm.edit(mine, "   "))
        assertTrue(vm.edit(mine, " Finalement 12 h "))
        assertEquals("Finalement 12 h", vm.uiState.value.rows[0].comment.text)
        assertFalse(vm.edit(theirs, "Non"))
        assertEquals("Texte b", vm.uiState.value.rows[1].comment.text)
    }

    @Test
    fun `remove and mute`() = runTest {
        val repo = FakeComments(listOf(comment("a", "adult1", 1)))
        val vm = viewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.remove(vm.uiState.value.rows.single())
        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.muted)
        vm.setMuted(true)
        assertTrue(vm.uiState.value.muted)
        vm.setMuted(false)
        assertFalse(vm.uiState.value.muted)
    }

    @Test
    fun `a failed send keeps the draft and says so`() = runTest {
        val vm = viewModel(FakeComments(failWith = IllegalStateException("hors ligne")), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.type("Bonjour")
        vm.send()
        assertEquals(EventCommentProblem.SendFailed, vm.uiState.value.problem)
        assertEquals("Bonjour", vm.uiState.value.draft)
        vm.dismissProblem()
        assertNull(vm.uiState.value.problem)
    }
}
