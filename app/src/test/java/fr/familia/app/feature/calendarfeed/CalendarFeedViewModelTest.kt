package fr.familia.app.feature.calendarfeed

import fr.familia.app.core.data.CalendarFeedRepository
import fr.familia.app.core.data.calendarFeedUrl
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarFeedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class FakeRepo(var refuse: Boolean = false) : CalendarFeedRepository {
        val tokens = MutableStateFlow<String?>(null)
        var renewed = 0
        override fun observeToken(memberId: String): Flow<String?> = tokens
        override suspend fun renew(memberId: String) {
            if (refuse) error("refused")
            renewed++
            tokens.value = "t$renewed"
        }
        override suspend fun remove(memberId: String) {
            if (refuse) error("refused")
            tokens.value = null
        }
    }

    @Test
    fun `no link at first, then the link once created`() = runTest {
        val repo = FakeRepo()
        val vm = CalendarFeedViewModel(repo, "m1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(CalendarFeedState.Ready(null), vm.state.value)
        vm.renew()
        assertEquals(CalendarFeedState.Ready(calendarFeedUrl("t1")), vm.state.value)
        vm.renew()
        assertEquals(CalendarFeedState.Ready(calendarFeedUrl("t2")), vm.state.value)
    }

    @Test
    fun `a refused link is said once`() = runTest {
        val vm = CalendarFeedViewModel(FakeRepo(refuse = true), "m1")
        vm.renew()
        assertTrue(vm.failed.value)
        assertFalse(vm.busy.value)
        vm.failureShown()
        assertFalse(vm.failed.value)
    }

    @Test
    fun `an unreadable link shows the error`() = runTest {
        val repo = object : CalendarFeedRepository {
            override fun observeToken(memberId: String): Flow<String?> = flow { error("denied") }
            override suspend fun renew(memberId: String) = Unit
            override suspend fun remove(memberId: String) = Unit
        }
        val vm = CalendarFeedViewModel(repo, "m1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(CalendarFeedState.Error, vm.state.value)
    }

    @Test
    fun `removing my link leaves me without one`() = runTest {
        val repo = FakeRepo()
        val vm = CalendarFeedViewModel(repo, "m1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.renew()
        vm.remove()
        assertEquals(CalendarFeedState.Ready(null), vm.state.value)
        assertFalse(vm.busy.value)
    }

    @Test
    fun `a link that could not be removed is said once`() = runTest {
        val vm = CalendarFeedViewModel(FakeRepo(refuse = true), "m1")
        vm.remove()
        assertTrue(vm.removeFailed.value)
        vm.failureShown()
        assertFalse(vm.removeFailed.value)
    }
}
