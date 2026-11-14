package fr.familia.app.feature.help

import fr.familia.app.core.model.Family
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a member who is not admin does not see the admin topics`() {
        assertFalse(helpTopics(isAdmin = false).any { it.adminOnly })
        assertTrue(helpTopics(isAdmin = true).containsAll(listOf(HelpTopic.Invite, HelpTopic.Export)))
    }

    @Test
    fun `the admins are named the French way`() {
        assertNull(adminNames(emptyList()))
        assertEquals("Camille", adminNames(listOf("Camille")))
        assertEquals("Camille ou Inès", adminNames(listOf("Camille", "Inès")))
        assertEquals("Camille, Inès ou Sam", adminNames(listOf("Camille", "Inès", "Sam")))
    }

    @Test
    fun `the help page names the family's admins`() = runTest {
        val members = FakeMembers()
        members.family.value = Family(
            TestFamily.family.households,
            TestFamily.family.members + TestFamily.otherAdult.copy(id = "adult3", firstName = "Bea", isAdmin = true),
        )
        val vm = HelpViewModel(members, isAdmin = false)
        vm.uiState.launchIn(backgroundScope)
        assertEquals("Admin1 ou Bea", vm.uiState.first { it.admins != null }.admins)
        assertEquals(helpTopics(isAdmin = false), vm.uiState.value.topics)
    }
}
