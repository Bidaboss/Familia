package fr.familia.app.feature.birthdays

import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaysViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun `birthdays are listed nearest first and members without a date are named apart`() = runTest {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.map {
                when (it.id) {
                    "adult1" -> it.copy(birthDate = LocalDate.of(1985, 1, 10))
                    "kid1" -> it.copy(birthDate = LocalDate.of(2015, 12, 1))
                    else -> it
                }
            },
        )
        val vm = BirthdaysViewModel(FakeMembers(family), today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as BirthdaysUiState.Success
        assertEquals(listOf("kid1", "adult1"), state.birthdays.map { it.member.id })
        assertEquals(listOf(11, 42), state.birthdays.map { it.age })
        assertEquals(listOf("Admin1", "Adult2", "Kid2"), state.missingDate.map { it.firstName })
    }

    @Test
    fun `a birth date added by an admin shows up live`() = runTest {
        val members = FakeMembers()
        val vm = BirthdaysViewModel(members, today = flowOf(today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(0, (vm.uiState.value as BirthdaysUiState.Success).birthdays.size)
        members.family.value = TestFamily.family.copy(
            members = TestFamily.family.members.map { if (it.id == "kid2") it.copy(birthDate = LocalDate.of(2021, 4, 29)) else it },
        )
        val state = vm.uiState.value as BirthdaysUiState.Success
        assertEquals(LocalDate.of(2027, 4, 29), state.birthdays.single().date)
    }
}
