package fr.familia.app.feature.dailynudge

import fr.familia.app.core.model.Reaction
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.FakeGoodNews
import fr.familia.app.feature.goodnews.NOW
import fr.familia.app.feature.goodnews.goodNews
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** « Le petit geste du jour » (PRD, récit 43). */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyNudgeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val me = TestFamily.otherAdult
    private val family = TestFamily.family

    @Test
    fun `the newest photo of someone else, not reacted to yet`() {
        val news = listOf(
            goodNews("old", TestFamily.adult, daysAgo = 2),
            goodNews("new", TestFamily.adult, forMemberId = "kid1"),
        )
        assertEquals(DailyNudge.ReactToPhoto("new", "Kid1"), pickDailyNudge(news, me, family, NOW))
    }

    @Test
    fun `nothing for my own news, one about me, one I reacted to or hid`() {
        val news = listOf(
            goodNews("mine", me),
            goodNews("forMe", TestFamily.adult, forMemberId = me.id),
            goodNews("reacted", TestFamily.adult).copy(reactions = mapOf(me.id to Reaction.Heart)),
            goodNews("hidden", TestFamily.adult).copy(hiddenByIds = listOf(me.id)),
        )
        assertNull(pickDailyNudge(news, me, family, NOW))
    }

    @Test
    fun `nothing older than three days, nor Familia's own news`() {
        val news = listOf(
            goodNews("old", TestFamily.adult, daysAgo = 4),
            goodNews("birthday", TestFamily.adult, photos = 0).copy(birthdayOf = "kid1"),
            goodNews("challenge", TestFamily.adult, photos = 0).copy(challengeMonth = "2026-11"),
        )
        assertNull(pickDailyNudge(news, me, family, NOW))
    }

    @Test
    fun `it goes once I have reacted, and the view model never writes`() = runTest {
        val repo = FakeGoodNews(listOf(goodNews("n1", TestFamily.adult)))
        val vm = DailyNudgeViewModel(repo, FakeMembers(), me.id) { NOW }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.nudge.collect {} }
        assertEquals(DailyNudge.ReactToPhoto("n1", "Adult1"), vm.nudge.value)
        assertEquals(0, repo.published.size)
        repo.react("n1", me.id, Reaction.Clap)
        assertNull(vm.nudge.value)
    }
}
