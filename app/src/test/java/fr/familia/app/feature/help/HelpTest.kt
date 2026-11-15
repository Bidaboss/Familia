package fr.familia.app.feature.help

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
    fun `each member sees the topics of the functions they can use`() {
        assertFalse(helpTopics(isChef = false).any { it.audience != HelpAudience.Everyone })
        assertTrue(HelpTopic.Invite in helpTopics(isChef = true))
        assertFalse(HelpTopic.Export in helpTopics(isChef = true))
        assertTrue(helpTopics(isChef = false, isOwner = true).containsAll(listOf(HelpTopic.Invite, HelpTopic.Export)))
    }

    @Test
    fun `the admins are named the French way`() {
        assertNull(adminNames(emptyList()))
        assertEquals("Camille", adminNames(listOf("Camille")))
        assertEquals("Camille ou Inès", adminNames(listOf("Camille", "Inès")))
        assertEquals("Camille, Inès ou Sam", adminNames(listOf("Camille", "Inès", "Sam")))
    }

    @Test
    fun `the help page names the admin principal, then the chefs of the member's household`() = runTest {
        val members = FakeMembers()
        members.family.value = TestFamily.family.copy(
            members = TestFamily.family.members.map {
                when (it.id) {
                    "adult1" -> it.copy(isAdmin = true)
                    "adult2" -> it.copy(isAdmin = true)
                    else -> it
                }
            },
        )
        val vm = HelpViewModel(members, TestFamily.kid)
        vm.uiState.launchIn(backgroundScope)
        // adult2 is chef of another household: not named to kid1.
        assertEquals("Admin1 ou Adult1", vm.uiState.first { it.admins != null }.admins)
        assertEquals(helpTopics(isChef = false), vm.uiState.value.topics)
    }
}
