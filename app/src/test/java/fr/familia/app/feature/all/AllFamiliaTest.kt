package fr.familia.app.feature.all

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import org.junit.Assert.assertEquals
import org.junit.Test

class AllFamiliaTest {

    private fun module(key: String) = AllModule(key, 0, Icons.Outlined.Search, ModuleFamily.Organise) {}

    private val modules = listOf(module("polls") to "Sondages", module("goodNews") to "Bonnes nouvelles", module("where") to "Où je suis")

    @Test
    fun `a blank search shows every module`() {
        assertEquals(listOf("polls", "goodNews", "where"), filterModules(modules, "  ").map { it.key })
    }

    @Test
    fun `the search forgets accents and capitals`() {
        assertEquals(listOf("where"), filterModules(modules, "OU JE").map { it.key })
        assertEquals(listOf("goodNews"), filterModules(modules, "nouv").map { it.key })
        assertEquals(emptyList<String>(), filterModules(modules, "zzz").map { it.key })
    }

    @Test
    fun `the sub-tabs of Entraide are found by a word of their name (PRODUIT-118)`() {
        val entraide = listOf(
            module("help") to "Entraide",
            module("goodDeals") to "Bons plans",
            module("lending") to "On se prête",
        )
        assertEquals(listOf("lending"), filterModules(entraide, "prêt").map { it.key })
        assertEquals(listOf("goodDeals"), filterModules(entraide, "plan").map { it.key })
    }
}
