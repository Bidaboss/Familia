package fr.familia.app.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentDayTest {

    @Test
    fun `the day is emitted once, then again when midnight has passed`() = runTest {
        var now = LocalDate.of(2026, 11, 14)
        val days = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            currentDayFlow(now = { now }, checkEveryMillis = 60_000).collect { days += it }
        }
        advanceTimeBy(3 * 60_000L) // Same day: no new emission.
        assertEquals(listOf(LocalDate.of(2026, 11, 14)), days)
        now = LocalDate.of(2026, 11, 15)
        advanceTimeBy(60_001)
        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)), days)
    }
}
