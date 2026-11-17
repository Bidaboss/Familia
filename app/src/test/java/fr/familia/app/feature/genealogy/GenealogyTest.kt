package fr.familia.app.feature.genealogy

import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The family tree, in memory (PRD, récit 40). Fictional family. */
class FakeGenealogy(
    kin: List<Kin> = emptyList(),
    links: List<KinLink> = emptyList(),
    private val wordsFail: Boolean = false,
) : GenealogyRepository {
    val kin = MutableStateFlow(kin)
    val links = MutableStateFlow(links)
    val words = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    override fun observeKin(): Flow<List<Kin>> = kin
    override fun observeLinks(): Flow<List<KinLink>> = links
    override fun observeWords(memberId: String): Flow<Map<String, String>> =
        if (wordsFail) flow { throw IOException("refused") } else MutableStateFlow(words.value[memberId].orEmpty())
    override fun newKinId(): String = "kin-new"
    override suspend fun saveKin(kin: Kin, byMemberId: String, firstLink: KinLink?) {
        this.kin.value = this.kin.value.filterNot { it.id == kin.id } + kin
        firstLink?.let { links.value = links.value + it }
    }
    override suspend fun deleteKin(id: String) {
        kin.value = kin.value.filterNot { it.id == id }
        links.value = links.value.filterNot { it.aId == id || it.bId == id }
    }
    override suspend fun addLink(link: KinLink, byMemberId: String) {
        links.value = (links.value + link).distinctBy { it.id }
    }
    override suspend fun removeLink(link: KinLink) {
        links.value = links.value.filterNot { it.id == link.id }
    }
    override suspend fun saveWords(memberId: String, words: Map<String, String>) {
        this.words.value = this.words.value + (memberId to words)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GenealogyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jeanne = Kin("jeanne", "Jeanne", living = false, born = "1921", died = "1999", householdId = "h1")

    @Test
    fun `the tree joins the members and the kin, with my words`() = runTest {
        val repo = FakeGenealogy(listOf(jeanne), listOf(KinLink.parent("jeanne", "adult1"), KinLink.parent("adult1", "kid1")))
        repo.words.value = mapOf("kid1" to mapOf("jeanne" to "Arrière-grand-mère"))
        val vm = GenealogyViewModel(repo, FakeMembers(), "kid1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as GenealogyUiState.Ready
        assertEquals(0, ready.tree.generations["jeanne"])
        assertEquals(2, ready.tree.generations["kid1"])
        assertEquals("Arrière-grand-mère", ready.words["jeanne"])
        assertEquals(TestFamily.family.members.size + 1, ready.tree.people.size)
    }

    @Test
    fun `with no link yet, the screen says how the tree fills up`() = runTest {
        val vm = GenealogyViewModel(FakeGenealogy(), FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value as GenealogyUiState.Ready).isEmpty)
    }

    @Test
    fun `my words refused, the tree still shows`() = runTest {
        val repo = FakeGenealogy(links = listOf(KinLink.partners("adult1", "adult2")), wordsFail = true)
        val vm = GenealogyViewModel(repo, FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as GenealogyUiState.Ready
        assertEquals(listOf("adult2"), ready.tree.partnersOf("adult1"))
        assertTrue(ready.words.isEmpty())
    }

    @Test
    fun `a refused tree says so`() = runTest {
        val repo = object : GenealogyRepository by FakeGenealogy() {
            override fun observeLinks(): Flow<List<KinLink>> = flow { throw IOException("refused") }
        }
        val vm = GenealogyViewModel(repo, FakeMembers(), "adult1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(GenealogyUiState.Failed, vm.uiState.value)
    }

    private fun TestScope.ready(vm: GenealogyViewModel): GenealogyUiState.Ready {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm.uiState.value as GenealogyUiState.Ready
    }

    @Test
    fun `the admin principal edits everything, a chef their household, an adult nothing (récit 40c)`() = runTest {
        // admin1 is the admin principal and chef of h1; adult2 is in h2.
        val repo = FakeGenealogy(listOf(jeanne))
        val owner = ready(GenealogyViewModel(repo, FakeMembers(), "admin1"))
        assertTrue(owner.isOwner && owner.canEditPerson("adult2"))
        val chef = TestFamily.family.copy(ownerId = "someone-else")
        val asChef = ready(GenealogyViewModel(repo, FakeMembers(chef), "admin1"))
        assertTrue(asChef.canEditPerson("adult1") && asChef.canEditPerson("jeanne"))
        assertTrue(!asChef.canEditPerson("adult2"))
        assertTrue(asChef.canEditLink(KinLink.partners("adult1", "adult2")))
        // PRD rév. 195: a parent only for a child of my household.
        assertTrue(asChef.canEditLink(KinLink.parent("adult2", "adult1")))
        assertTrue(!asChef.canEditLink(KinLink.parent("adult1", "adult2")))
        assertTrue(!asChef.canJoin("adult1", Relation.Child, "adult2"))
        assertTrue(asChef.canJoin("adult1", Relation.Partner, "adult2"))
        val adult = ready(GenealogyViewModel(repo, FakeMembers(), "adult1"))
        assertTrue(!adult.canEdit)
    }

    @Test
    fun `an empty tree offers to join two members: the ones I may edit, my household first`() = runTest {
        val owner = ready(GenealogyViewModel(FakeGenealogy(), FakeMembers(), "admin1"))
        assertTrue(owner.isEmpty)
        val ids = owner.joinable.map { it.id }
        assertEquals(TestFamily.family.members.size, ids.size)
        val mine = TestFamily.family.members.first { it.id == "admin1" }.householdId
        val firstOther = ids.indexOfFirst { owner.tree.people.getValue(it).householdId != mine }
        assertTrue(ids.drop(firstOther).none { owner.tree.people.getValue(it).householdId == mine })
        // A chef only starts from their household; an adult not at all.
        val chef = TestFamily.family.copy(ownerId = "someone-else")
        val asChef = ready(GenealogyViewModel(FakeGenealogy(), FakeMembers(chef), "admin1"))
        assertTrue(asChef.joinable.isNotEmpty() && asChef.joinable.all { it.householdId == mine })
        assertTrue(ready(GenealogyViewModel(FakeGenealogy(), FakeMembers(), "adult1")).joinable.isEmpty())
    }

    @Test
    fun `a link is made once, never backwards, and only where one may`() = runTest {
        val repo = FakeGenealogy(listOf(jeanne))
        val vm = GenealogyViewModel(repo, FakeMembers(), "admin1")
        ready(vm)
        vm.addLink("adult1", Relation.Parent, "jeanne")
        vm.addLink("adult1", Relation.Parent, "jeanne")
        vm.addLink("jeanne", Relation.Parent, "adult1")
        vm.addLink("adult1", Relation.Partner, "adult1")
        assertEquals(listOf(KinLink.parent("jeanne", "adult1")), repo.links.value)

        val adult = GenealogyViewModel(repo, FakeMembers(), "adult1")
        ready(adult)
        adult.addLink("adult1", Relation.Child, "kid1")
        adult.removeLink(KinLink.parent("jeanne", "adult1"))
        assertEquals(1, repo.links.value.size)
    }

    @Test
    fun `a new relative joins the household of the person they are linked to, with the link`() = runTest {
        val repo = FakeGenealogy()
        val vm = GenealogyViewModel(repo, FakeMembers(), "admin1")
        ready(vm)
        vm.saveKin(Kin("", "  Lucien ", living = true, died = "1990", householdId = ""), "adult2" to Relation.Parent)
        val saved = repo.kin.value.single()
        assertEquals("kin-new", saved.id)
        assertEquals("Lucien", saved.firstName)
        assertEquals("h2", saved.householdId)
        assertEquals(null, saved.died)
        assertEquals(listOf(KinLink.parent("kin-new", "adult2")), repo.links.value)
    }

    @Test
    fun `a chef never gives a new parent to a child of another household (PRD rev 195)`() = runTest {
        val repo = FakeGenealogy()
        val chef = TestFamily.family.copy(ownerId = "someone-else")
        val vm = GenealogyViewModel(repo, FakeMembers(chef), "admin1")
        ready(vm)
        vm.saveKin(Kin("", "Lucien", living = true, householdId = ""), "adult2" to Relation.Parent)
        assertTrue(repo.kin.value.isEmpty() && repo.links.value.isEmpty())
        vm.saveKin(Kin("", "Lucien", living = true, householdId = ""), "adult2" to Relation.Partner)
        assertEquals("h1", repo.kin.value.single().householdId)
    }

    @Test
    fun `my words are mine, trimmed, and a blank one is taken away`() = runTest {
        val repo = FakeGenealogy(listOf(jeanne))
        val vm = GenealogyViewModel(repo, FakeMembers(), "kid1")
        ready(vm)
        vm.setWord("jeanne", "  Mamie Jeanne ")
        vm.setWord("kid1", "moi")
        assertEquals(mapOf("jeanne" to "Mamie Jeanne"), repo.words.value["kid1"])
    }

    @Test
    fun `a refused change is said once`() = runTest {
        val repo = object : GenealogyRepository by FakeGenealogy(listOf(jeanne)) {
            override suspend fun deleteKin(id: String) {
                throw IOException("refused")
            }
        }
        val vm = GenealogyViewModel(repo, FakeMembers(), "admin1")
        ready(vm)
        vm.deleteKin("jeanne")
        assertTrue(vm.actionFailed.value)
        vm.failureShown()
        assertTrue(!vm.actionFailed.value)
    }
}
