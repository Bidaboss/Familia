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
}
