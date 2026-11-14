package fr.familia.app.feature.members

import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun m(id: String, name: String, household: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, household, profile, false, emptyList(), null)

    @Test
    fun `households are sorted by name, adults before children, then by first name`() {
        val family = Family(
            households = listOf(Household("h2", "Zèbre"), Household("h1", "Abeille")),
            members = listOf(
                m("1", "Zoé", "h1", MemberProfile.ChildWithoutApp),
                m("2", "bruno", "h1"),
                m("3", "Anna", "h1", MemberProfile.ChildWithApp),
                m("4", "Alice", "h1"),
                m("5", "Yann", "h2"),
            ),
        )
        val groups = MembersViewModel.group(family)
        assertEquals(listOf("Abeille", "Zèbre"), groups.map { it.household.name })
        assertEquals(listOf("Alice", "bruno", "Anna", "Zoé"), groups[0].members.map { it.firstName })
    }

    @Test
    fun `an empty household is not shown`() {
        val family = Family(listOf(Household("h1", "Vide")), emptyList())
        assertEquals(emptyList<HouseholdGroup>(), MembersViewModel.group(family))
    }

    @Test
    fun `the screen follows live changes of the family`() = runTest {
        val source = MutableStateFlow(Family(listOf(Household("h1", "Foyer")), listOf(m("1", "Alice", "h1"))))
        val vm = MembersViewModel(FakeMembers(source), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, (vm.uiState.value as MembersUiState.Success).groups.single().members.size)

        source.value = source.value.copy(members = source.value.members + m("2", "Bruno", "h1"))
        assertEquals(2, (vm.uiState.value as MembersUiState.Success).groups.single().members.size)
    }

    @Test
    fun `a failure shows the error state`() = runTest {
        val vm = MembersViewModel(FakeMembers(flow { throw IOException("offline") }), currentMemberId = "1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(MembersUiState.Error, vm.uiState.value)
    }

    private class FakeMembers(private val family: Flow<Family>) : MemberRepository {
        override suspend fun findByEmail(email: String): Member? = null
        override suspend fun linkAccount(memberId: String, uid: String) = Unit
        override fun observeFamily(): Flow<Family> = family
    }
}
