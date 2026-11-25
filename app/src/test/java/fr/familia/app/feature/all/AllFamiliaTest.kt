package fr.familia.app.feature.all

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllFamiliaTest {

    private fun module(key: String) = AllModule(ModuleSpec.entries.first { it.key == key }) {}

    private val modules = listOf(module("polls") to "Sondages", module("recipes") to "Recettes", module("lending") to "On se prête")

    @Test
    fun `a blank search shows every module`() {
        assertEquals(listOf("polls", "recipes", "lending"), filterModules(modules, "  ").map { it.key })
    }

    @Test
    fun `the search forgets accents and capitals`() {
        assertEquals(listOf("lending"), filterModules(modules, "ON SE PRETE").map { it.key })
        assertEquals(listOf("recipes"), filterModules(modules, "RECET").map { it.key })
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

    @Test
    fun `the menu holds 13 modules, in the order of the brief, without the gazette nor « Où je suis » (récits 117 et 120)`() {
        assertEquals(
            listOf(
                "planning", "help", "goodDeals", "lending", "polls",
                "gifts", "trips", "recipes", "challenges",
                "albums", "capsule", "yearInReview", "whoIsIt",
            ),
            ModuleSpec.entries.map { it.key },
        )
        val keys = ModuleSpec.entries.map { it.key }
        assertTrue(keys.none { it in listOf("feed", "goodNews", "souvenirs", "where") })
    }

    @Test
    fun `three groups, « Qui est-ce ? » always in « Se souvenir », last`() {
        assertEquals(ModuleFamily.Remember, ModuleSpec.WhoIsIt.family)
        assertEquals(ModuleSpec.WhoIsIt, ModuleSpec.entries.last())
        assertEquals(
            listOf(5, 4, 4),
            ModuleFamily.entries.map { family -> ModuleSpec.entries.count { it.family == family } },
        )
    }

    @Test
    fun `Cadeaux and Voyages have the double picto`() {
        assertEquals(setOf(ModuleSpec.Gifts, ModuleSpec.Trips), ModuleSpec.entries.filter { it.double }.toSet())
    }

    @Test
    fun `4 a line, 3 from a text at 130 %, 2 from 180 %`() {
        assertEquals(4, modulesPerLine(1f))
        assertEquals(3, modulesPerLine(1.3f))
        assertEquals(2, modulesPerLine(2f))
    }
}
