package fr.familia.app.feature.polls

import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PollsToVoteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-11-16T10:00:00Z")

    private fun poll(id: String, asked: List<String>, closesAt: Instant? = null, closed: Boolean = false) =
        Poll(id, Poll.Kind.Choice, "Question $id", listOf("A", "B"), false, asked, asked, null, closesAt, closed, null, null, "m2", "Inès", null)

    @Test
    fun `the badge counts the open polls that ask me and wait for my own vote`() {
        val polls = listOf(
            poll("p1", listOf("m1")),
            poll("p2", listOf("m1"), closesAt = now.plusSeconds(3600)),
            poll("p3", listOf("m1")),
            poll("p4", listOf("m2")),
            poll("p5", listOf("m1"), closed = true),
            poll("p6", listOf("m1"), closesAt = now.minusSeconds(60)),
        )
        val votes = mapOf("p3" to listOf(PollVote("m1", choices = listOf(0), votedBy = "m1")))
        assertEquals(2, pollsToVoteCount("m1", polls, votes, now))
        assertEquals(0, pollsToVoteCount("m3", polls, votes, now))
    }

    @Test
    fun `the card Sondages listens to my polls once for both of its numbers`() = runTest {
        val repo = FakePolls(listOf(fr.familia.app.feature.polls.poll("p1"), fr.familia.app.feature.polls.poll("p2", closed = true)))
        val viewModel = PollsToVoteViewModel(repo, "adult1", clock = { Instant.parse("2026-11-25T10:00:00Z") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.open.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.count.collect {} }
        assertEquals(1, viewModel.open.value)
        assertEquals(1, viewModel.count.value)
        assertEquals(1, repo.mineCalls)
    }
}
