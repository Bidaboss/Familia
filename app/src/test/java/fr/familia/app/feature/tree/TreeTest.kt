package fr.familia.app.feature.tree

import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.TreeSeason
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

private class FakeTree : TreeRepository {
    val tree = MutableStateFlow(FamilyTree())
    val challenge = MutableStateFlow<MonthChallenge?>(null)
    override fun observeTree(): Flow<FamilyTree> = tree
    override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = challenge
}

class TreeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val december = YearMonth.of(2026, 12)

    @Test
    fun `the levels follow the leaves, like the server`() {
        assertEquals(0, FamilyTree(0).level)
        assertEquals(20, FamilyTree(0).leavesToNextLevel)
        assertEquals(1, FamilyTree(20).level)
        assertEquals(6, FamilyTree(1000).level)
        assertNull(FamilyTree(1000).leavesToNextLevel)
    }

    @Test
    fun `the tree dresses for the season and blooms the month of a challenge achieved`() {
        assertEquals(TreeSeason.Winter, TreeSeason.of(december))
        assertEquals(TreeSeason.Spring, TreeSeason.of(YearMonth.of(2027, 4)))
        assertTrue(FamilyTree(10, listOf("2026-12")).bloomed(december))
        assertFalse(FamilyTree(10, listOf("2026-12")).bloomed(YearMonth.of(2027, 1)))
    }

    @Test
    fun `levels 1 to 3 fill the inner row of tufts, then the outer row grows (DESIGN)`() {
        assertEquals(1 to 0, tuftRows(0))
        assertEquals(4 to 0, tuftRows(3))
        assertEquals(4 to 2, tuftRows(4))
        assertEquals(4 to 6, tuftRows(6))
    }

    @Test
    fun `the gauge never goes past its target`() {
        assertEquals(0.35f, MonthChallenge(december, "20 photos de Noël", "🎄", 20, 7, false).fraction, 0.001f)
        assertEquals(1f, MonthChallenge(december, "x", "", 20, 25, true).fraction, 0.001f)
        assertEquals(0f, MonthChallenge(december, "x", "", 0, 3, false).fraction, 0.001f)
    }

    @Test
    fun `the card shows the tree, then the challenge once the server has it`() = runTest {
        val repo = FakeTree()
        val vm = TreeViewModel(repo, december)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value?.challenge)
        repo.tree.value = FamilyTree(42)
        repo.challenge.value = MonthChallenge(december, "20 photos de Noël", "🎄", 20, 7, false)
        assertEquals(42, vm.uiState.value?.tree?.leaves)
        assertEquals(7, vm.uiState.value?.challenge?.progress)
    }
}
