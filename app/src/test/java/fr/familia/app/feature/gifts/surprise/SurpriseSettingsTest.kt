package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseMemberRow
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.notebook.FakeNotebook
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** ⚙ « Rappels en sourdine » (brief § 9) and « Réinviter » by the creator (brief §4). */
class SurpriseSettingsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val surprise = Surprise(
        "s1", "adult2", "Les 40 ans", SurpriseOccasion.Birthday, LocalDate.of(2027, 2, 3), listOf("adult1", "admin1"), "adult1", "Adult1",
    )

    @Test
    fun `the mute is mine and goes back`() = runTest {
        val repo = FakeSurprises(listOf(surprise))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, FakeNotebook(), "admin1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        fun ready() = vm.state.value as SurpriseUiState.Ready
        assertFalse(ready().muted)
        vm.setMuted(true)
        assertTrue(ready().muted)
        assertEquals(listOf(SurpriseMemberRow("admin1", muted = true)), repo.rows.value["s1"])
        vm.setMuted(false)
        assertFalse(ready().muted)
    }

    @Test
    fun `the creator invites again a member who declined`() = runTest {
        val repo = FakeSurprises(listOf(surprise))
        repo.rows.value = mapOf("s1" to listOf(SurpriseMemberRow("admin1", declined = true)))
        val vm = SurpriseViewModel(repo, FakeMembers(), null, FakeNotebook(), "adult1", "s1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        fun ready() = vm.state.value as SurpriseUiState.Ready
        assertTrue(ready().isCreator)
        assertEquals(MemberState.Declined, ready().statuses.first { it.memberId == "admin1" }.state)
        vm.reinvite("admin1")
        assertEquals(MemberState.Waiting, ready().statuses.first { it.memberId == "admin1" }.state)
    }
}
