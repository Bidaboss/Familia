package fr.familia.app.feature.events.reminders

import fr.familia.app.core.data.EventReminderRepository
import fr.familia.app.core.model.EventReminders
import fr.familia.app.core.model.ReminderOffset
import fr.familia.app.core.model.ReminderOffset.Morning
import fr.familia.app.core.model.ReminderOffset.OneHour
import fr.familia.app.core.model.ReminderOffset.OneWeek
import fr.familia.app.core.model.ReminderOffset.ThreeHours
import fr.familia.app.core.model.ReminderOffset.TwoDays
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

private class FakeReminders : EventReminderRepository {
    val docs = MutableStateFlow<Map<String, Set<ReminderOffset>>>(emptyMap())
    val defaults = MutableStateFlow<Set<ReminderOffset>>(emptySet())
    override fun observe(memberId: String, eventId: String, seriesId: String?): Flow<EventReminders> =
        combine(docs, defaults) { d, def -> EventReminders(d[eventId], seriesId?.let { d["series-$it"] }, def) }
    override fun observeDefaults(memberId: String): Flow<Set<ReminderOffset>> = defaults
    override suspend fun setForEvent(memberId: String, eventId: String, offsets: Set<ReminderOffset>) {
        docs.value = docs.value + (eventId to offsets)
    }
    override suspend fun setForSeries(memberId: String, seriesId: String, eventId: String, offsets: Set<ReminderOffset>) {
        docs.value = docs.value - eventId + ("series-$seriesId" to offsets)
    }
    override suspend fun setDefaults(memberId: String, offsets: Set<ReminderOffset>) {
        defaults.value = offsets
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventRemindersTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `an all-day event has no reminder an hour before, an early one none at 8 h`() {
        assertEquals(listOf(Morning, TwoDays, OneWeek), ReminderOffset.choicesFor(null))
        assertEquals(listOf(OneHour, ThreeHours, TwoDays, OneWeek), ReminderOffset.choicesFor(LocalTime.of(7, 30)))
        assertEquals(ReminderOffset.entries, ReminderOffset.choicesFor(LocalTime.of(15, 0)))
    }

    @Test
    fun `the event wins over its series, and the defaults count only for Je viens`() {
        val r = EventReminders(own = null, series = setOf(TwoDays), defaults = setOf(OneHour))
        assertEquals(setOf(TwoDays), r.effective(coming = true))
        assertEquals(emptySet<ReminderOffset>(), r.copy(own = emptySet()).effective(coming = true))
        val onlyDefaults = EventReminders(defaults = setOf(OneHour))
        assertEquals(setOf(OneHour), onlyDefaults.effective(coming = true))
        assertTrue(onlyDefaults.byDefault(coming = true))
        assertEquals(emptySet<ReminderOffset>(), onlyDefaults.effective(coming = false))
        assertFalse(onlyDefaults.byDefault(coming = false))
    }

    @Test
    fun `this time only writes the event, every time writes the series and takes the event back`() = runTest {
        val repo = FakeReminders()
        val viewModel = EventRemindersViewModel(repo, "adult1", "s1_20261121", "s1", LocalTime.of(15, 0), coming = true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.inSeries)
        viewModel.save(setOf(OneHour), everyTime = false)
        assertEquals(setOf(OneHour), repo.docs.value["s1_20261121"])
        assertFalse(viewModel.uiState.value.fromSeries)
        viewModel.save(setOf(Morning), everyTime = true)
        assertNull(repo.docs.value["s1_20261121"])
        assertEquals(setOf(Morning), repo.docs.value["series-s1"])
        assertTrue(viewModel.uiState.value.fromSeries)
        assertEquals(setOf(Morning), viewModel.uiState.value.effective)
    }

    @Test
    fun `an event out of any series is saved on its own`() = runTest {
        val repo = FakeReminders()
        val viewModel = EventRemindersViewModel(repo, "adult1", "e1", null, null, coming = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(listOf(Morning, TwoDays, OneWeek), viewModel.uiState.value.choices)
        viewModel.save(setOf(TwoDays), everyTime = true)
        assertEquals(setOf(TwoDays), repo.docs.value["e1"])
    }

    @Test
    fun `the default reminders are turned on and off one by one`() = runTest {
        val repo = FakeReminders()
        val viewModel = DefaultRemindersViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.defaults.collect {} }
        viewModel.toggle(OneHour, true)
        viewModel.toggle(TwoDays, true)
        viewModel.toggle(OneHour, false)
        assertEquals(setOf(TwoDays), repo.defaults.value)
    }
}
