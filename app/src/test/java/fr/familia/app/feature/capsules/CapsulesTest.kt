package fr.familia.app.feature.capsules

import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.model.Capsule
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Holds every capsule and answers as the rules would: only the author's, and those opened for one. */
class FakeCapsules(initial: List<Capsule> = emptyList()) : CapsuleRepository {
    val capsules = MutableStateFlow(initial)
    override fun observeWrittenBy(memberId: String): Flow<List<Capsule>> = capsules.map { list -> list.filter { it.authorId == memberId } }
    override fun observeOpenedFor(memberId: String): Flow<List<Capsule>> = capsules.map { list -> list.filter { memberId in it.openedFor } }
    override fun observeCapsule(id: String): Flow<Capsule?> = capsules.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun deleteCapsule(id: String) {
        capsules.value = capsules.value.filterNot { it.id == id }
    }
}

/** Fictional capsules of the test family (see TestFamily). */
@OptIn(ExperimentalCoroutinesApi::class)
class CapsulesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun capsule(id: String, author: String, to: List<String>, openAt: String, opened: Boolean = false) =
        Capsule(id, "Message $id", 0, to, LocalDate.parse(openAt), if (opened) to else emptyList(), null, author, author.replaceFirstChar { it.uppercase() }, null)

    private val forMeOpened = capsule("k1", "adult2", listOf("adult1"), "2026-11-01", opened = true)
    private val forMeSealed = capsule("k2", "adult2", listOf("adult1", "kid1"), "2027-06-01")
    private val mineLater = capsule("k3", "adult1", listOf("kid1", "adult2"), "2030-01-01")
    private val mineSooner = capsule("k4", "adult1", listOf("kid1"), "2027-01-01")
    private val mineForMeOpened = capsule("k5", "adult1", listOf("adult1"), "2026-10-01", opened = true)

    @Test
    fun `one sees what was opened for one and one's own sealed capsules, never those sealed for one`() = runTest {
        val vm = CapsulesViewModel(FakeCapsules(listOf(forMeOpened, forMeSealed, mineLater, mineSooner, mineForMeOpened)), FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val ready = vm.uiState.value as CapsulesUiState.Ready
        // The last opened first, and a capsule for oneself only once.
        assertEquals(listOf("k1", "k5"), ready.opened.map { it.capsule.id })
        // The next to open first.
        assertEquals(listOf("k4", "k3"), ready.sealed.map { it.capsule.id })
        assertEquals("Kid1 et Adult2", ready.sealed[1].recipients)
        assertFalse(ready.opened[0].isMine)
        assertTrue(ready.sealed.all { it.isMine })
    }

    @Test
    fun `a capsule still sealed stays closed for its recipients, open for its author`() = runTest {
        val repo = FakeCapsules(listOf(forMeSealed))
        fun detail(me: String) = CapsuleDetailViewModel(repo, FakeMembers(), me, "k2").also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
        assertEquals(CapsuleDetailUiState.Missing, detail("adult1").uiState.value)
        assertTrue((detail("adult2").uiState.value as CapsuleDetailUiState.Ready).row.isMine)
    }

    @Test
    fun `only the author removes a capsule, and its removal closes it`() = runTest {
        val repo = FakeCapsules(listOf(forMeOpened))
        fun detail(me: String) = CapsuleDetailViewModel(repo, FakeMembers(), me, "k1").also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
        val recipient = detail("adult1")
        recipient.remove()
        assertEquals(1, repo.capsules.value.size)

        val author = detail("adult2")
        author.remove()

        assertEquals(CapsuleDetailUiState.Removed, author.uiState.value)
        assertTrue(repo.capsules.value.isEmpty())
    }

    @Test
    fun `photos are found in the author's folder`() {
        val c = capsule("k9", "adult1", listOf("kid1"), "2027-01-01").copy(photoCount = 2)
        assertEquals("capsules/adult1/k9/2.jpg", c.photoPath(2))
        assertEquals("capsules/adult1/k9/1-thumb.jpg", c.photoPath(1, thumbnail = true))
    }
}
