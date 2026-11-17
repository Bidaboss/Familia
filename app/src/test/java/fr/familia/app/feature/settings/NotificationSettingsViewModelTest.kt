package fr.familia.app.feature.settings

import fr.familia.app.core.data.NotificationRepository
import fr.familia.app.core.model.NotificationFamily
import fr.familia.app.core.model.NotificationLevel
import fr.familia.app.core.model.NotificationPrefs
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class FakeNotifications(
    private val readFails: Boolean = false,
    private val writeFails: Boolean = false,
) : NotificationRepository {
    val settings = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val devices = mutableListOf<String>()
    override suspend fun registerThisDevice(memberId: String) {
        devices += memberId
    }
    override fun observeDayBeforeReminder(memberId: String): Flow<Boolean> =
        if (readFails) flow { throw IllegalStateException("offline") } else settings.map { it[memberId] ?: true }
    override suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean) {
        if (writeFails) throw IllegalStateException("refused")
        settings.value = settings.value + (memberId to enabled)
    }
    val weekly = MutableStateFlow(true)
    override fun observeWeekly(memberId: String): Flow<Boolean> = weekly
    override suspend fun setWeekly(memberId: String, enabled: Boolean) {
        weekly.value = enabled
    }
    val packing = MutableStateFlow(true)
    override fun observePackingReminder(memberId: String): Flow<Boolean> = packing
    override suspend fun setPackingReminder(memberId: String, enabled: Boolean) {
        packing.value = enabled
    }
    val recap = MutableStateFlow(true)
    override fun observeRecap(memberId: String): Flow<Boolean> = recap
    override suspend fun setRecap(memberId: String, enabled: Boolean) {
        recap.value = enabled
    }
    val wishes = MutableStateFlow(true)
    override fun observeBirthdayWishes(memberId: String): Flow<Boolean> = wishes
    override suspend fun setBirthdayWishes(memberId: String, enabled: Boolean) {
        wishes.value = enabled
    }
    val prefs = MutableStateFlow(NotificationPrefs())
    val writes = mutableListOf<Map<NotificationFamily, Boolean>>()
    override fun observePrefs(memberId: String): Flow<NotificationPrefs> = prefs
    override suspend fun setFamilies(memberId: String, families: Map<NotificationFamily, Boolean>) {
        writes += families
        val off = prefs.value.off + families.filterValues { !it }.keys - families.filterValues { it }.keys
        prefs.value = prefs.value.copy(off = off)
    }
    override suspend fun setQuietHours(memberId: String, enabled: Boolean) {
        prefs.value = prefs.value.copy(quietHours = enabled)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the reminder is on until the member turns it off`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true), vm.uiState.value)

        vm.setDayBeforeReminder(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = false), vm.uiState.value)
        assertEquals(mapOf("adult1" to false), repo.settings.value)
    }

    @Test
    fun `a refused change is reported and the switch stays as it was`() = runTest {
        val vm = NotificationSettingsViewModel(FakeNotifications(writeFails = true), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setDayBeforeReminder(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true, saveFailed = true), vm.uiState.value)
    }

    @Test
    fun `settings that cannot be read show an error`() = runTest {
        val vm = NotificationSettingsViewModel(FakeNotifications(readFails = true), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(NotificationSettingsUiState.Error, vm.uiState.value)
    }

    @Test
    fun `« La semaine de la famille » can be turned off (récit 36)`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setRecap(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true, recap = false), vm.uiState.value)
        assertEquals(false, repo.recap.value)
    }

    @Test
    fun `the reminder of the bag can be turned off (récit 62)`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setPackingReminder(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true, packingReminder = false), vm.uiState.value)
        assertEquals(false, repo.packing.value)
    }

    @Test
    fun `the birthday wishes notices can be turned off (récit 36)`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setBirthdayWishes(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true, birthdayWishes = false), vm.uiState.value)
        assertEquals(false, repo.wishes.value)
    }

    @Test
    fun `the question of the week notice can be turned off`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.setWeekly(false)
        assertEquals(NotificationSettingsUiState.Ready(dayBeforeReminder = true, weekly = false), vm.uiState.value)
        assertEquals(false, repo.weekly.value)
    }

    @Test
    fun `a level sets every family in one write, and a switch changed afterwards counts`() = runTest {
        val repo = FakeNotifications()
        val vm = NotificationSettingsViewModel(repo, "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fun prefs() = (vm.uiState.value as NotificationSettingsUiState.Ready).prefs
        assertEquals(NotificationLevel.All, prefs().level)

        vm.setLevel(NotificationLevel.Essential)
        assertEquals(1, repo.writes.size)
        assertEquals(NotificationFamily.entries.toSet(), repo.writes.single().keys)
        assertEquals(NotificationLevel.Essential, prefs().level)

        vm.setFamily(NotificationFamily.Gifts, true)
        assertEquals(mapOf(NotificationFamily.Gifts to true), repo.writes.last())
        assertEquals(true, prefs().isOn(NotificationFamily.Gifts))
        assertEquals(null, prefs().level) // Set one by one: no level is ticked.

        vm.setQuietHours(true)
        assertEquals(true, prefs().quietHours)
    }
}
