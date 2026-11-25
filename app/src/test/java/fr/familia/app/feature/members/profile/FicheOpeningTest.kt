package fr.familia.app.feature.members.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * A member's page opens whole, never a first page then the full one (retour de l'admin, 25/11 : « un autre écran
 * avant la fiche »). Fictional family only (TestFamily).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FicheOpeningTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** A card that comes after [late] ms, or never. */
    private class SlowCards(private val late: Long?) : MemberCardRepository {
        override fun observeCard(memberId: String): Flow<MemberCard?> = flow {
            if (late == null) delay(Long.MAX_VALUE)
            delay(late ?: 0)
            emit(MemberCard(memberId, allergies = "Arachides"))
        }
        override suspend fun saveCard(card: MemberCard, byId: String) = Unit
    }

    private fun TestScope.model(cards: MemberCardRepository): MemberProfileV2ViewModel {
        val vm = MemberProfileV2ViewModel(FakeMembers(TestFamily.family), null, null, null, null, "adult1", "adult2", LocalDate.of(2026, 6, 10), memberCards = cards)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `a card a little late shows with the page, not after it`() = runTest {
        val vm = model(SlowCards(late = 200))
        runCurrent()
        assertNull(vm.state.value)
        advanceTimeBy(250)
        assertEquals("Arachides", vm.state.value!!.card!!.allergies)
    }

    @Test
    fun `a card that never comes does not hold the page`() = runTest {
        val vm = model(SlowCards(late = null))
        advanceTimeBy(EXTRAS_WAIT_MS + 50)
        assertNull(vm.state.value!!.card)
    }
}

/** The page while it loads (retour de l'admin, 25/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class FicheSkeletonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `while loading, the page shows its own skeleton, not a wheel on another screen`() {
        compose.setContent {
            FamiliaTheme {
                MemberProfileScreen(state = MemberProfileUiState.Loading, onBack = {}, onEdit = {})
            }
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithTag(FICHE_SKELETON_TAG).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Ses envies", substring = true).fetchSemanticsNodes().size)
    }
}
