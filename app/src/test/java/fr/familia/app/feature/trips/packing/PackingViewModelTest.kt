package fr.familia.app.feature.trips.packing

import fr.familia.app.core.data.PackingRepository
import fr.familia.app.core.model.PackingBag
import fr.familia.app.core.model.PackingLine
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TestFamily: adult1 and kid1 (parent adult1) in h1, adult2 and kid2 (parent adult2) in h2. */
class PackingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePacking(private val refuse: Boolean = false) : PackingRepository {
        val bags = MutableStateFlow<Map<String, PackingBag>>(emptyMap())
        val templates = MutableStateFlow<Map<String, List<String>>>(emptyMap())
        val signedBy = mutableListOf<String>()
        override fun observeBag(tripId: String, memberId: String): Flow<PackingBag?> = bags.map { it["$tripId/$memberId"] }
        override suspend fun saveBag(tripId: String, memberId: String, lines: List<PackingLine>, by: String) {
            if (refuse) throw IllegalStateException("rules")
            signedBy += by
            bags.value = bags.value + ("$tripId/$memberId" to PackingBag(lines))
        }
        override fun observeTemplate(memberId: String): Flow<List<String>?> = templates.map { it[memberId] }
        override suspend fun saveTemplate(memberId: String, labels: List<String>) {
            templates.value = templates.value + (memberId to labels)
        }
    }

    private val defaults = listOf("Papiers", "Chargeur")

    private fun vm(me: String, packing: PackingRepository, participants: List<String> = listOf("adult1", "kid1", "adult2", "kid2")) =
        PackingViewModel(packing, FakeTrips(listOf(TestTrips.trip(participants = participants))), FakeMembers(), me, "t1", defaults)

    @Test
    fun `an adult sees their bag then the bags of their children who come, never the others`() = runTest {
        val vm = vm("adult1", FakePacking())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("adult1", "kid1"), vm.uiState.value.bags.map { it.owner.memberId })
        assertTrue(vm.uiState.value.bags.first().owner.isMe)
    }

    @Test
    fun `a follower who does not take part has no bag, a child only their own`() = runTest {
        val follower = vm("adult2", FakePacking(), participants = listOf("adult1", "kid1"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { follower.uiState.collect {} }
        assertTrue(follower.uiState.value.bags.isEmpty())
        val kid = vm("kid1", FakePacking())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { kid.uiState.collect {} }
        assertEquals(listOf("kid1"), kid.uiState.value.bags.map { it.owner.memberId })
    }

    @Test
    fun `a bag starts from my list, or from the default one, and the first tick writes it`() = runTest {
        val packing = FakePacking().apply { templates.value = mapOf("adult1" to listOf("Maillot")) }
        val vm = vm("adult1", packing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("Maillot"), vm.uiState.value.bags[0].lines.map { it.label })
        assertEquals(defaults, vm.uiState.value.bags[1].lines.map { it.label })
        assertTrue(packing.bags.value.isEmpty())

        vm.toggle("kid1", 1, done = true)
        assertEquals(listOf(PackingLine("Papiers"), PackingLine("Chargeur", done = true)), packing.bags.value["t1/kid1"]?.lines)
        assertEquals(listOf("adult1"), packing.signedBy)
        assertEquals(1, vm.uiState.value.bags[1].left)
    }

    @Test
    fun `adding twice the same thing or a blank line does nothing, removing takes it away`() = runTest {
        val packing = FakePacking()
        val vm = vm("adult1", packing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.add("adult1", "  Lunettes ")
        vm.add("adult1", "lunettes")
        vm.add("adult1", "   ")
        assertEquals(listOf("Papiers", "Chargeur", "Lunettes"), vm.uiState.value.bags[0].lines.map { it.label })
        vm.remove("adult1", 0)
        assertEquals(listOf("Chargeur", "Lunettes"), vm.uiState.value.bags[0].lines.map { it.label })
    }

    @Test
    fun `Garder comme ma liste de depart keeps my lines for the next trips`() = runTest {
        val packing = FakePacking()
        val vm = vm("adult1", packing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.add("adult1", "Gourde")
        vm.keepAsMyList()
        assertEquals(listOf("Papiers", "Chargeur", "Gourde"), packing.templates.value["adult1"])
    }

    @Test
    fun `a refused write is said`() = runTest {
        val vm = vm("adult1", FakePacking(refuse = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.toggle("adult1", 0, done = true)
        assertTrue(vm.uiState.value.sendFailed)
        vm.dismissFailure()
        assertTrue(!vm.uiState.value.sendFailed)
    }

    @Test
    fun `the bags of the children follow the households`() {
        val family = TestFamily.family.copy(members = TestFamily.family.members + TestFamily.otherKid.copy(id = "kid3", firstName = "Alma", parentIds = listOf("adult1"), householdId = "h2"))
        val trip = TestTrips.trip(participants = listOf("adult1", "kid1", "kid3"))
        // « Foyer Deux » comes before « Foyer Un », by name.
        assertEquals(listOf("adult1", "kid3", "kid1"), owners(trip, family, "adult1").map { it.memberId })
    }
}
