package fr.familia.app.core.navigation

import fr.familia.app.core.model.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `bottom bar shows accueil, planning, gifts, entraide and family in that order`() {
        assertEquals(
            listOf(TopLevelDestination.Home, TopLevelDestination.Planning, TopLevelDestination.Gifts, TopLevelDestination.HelpRequests, TopLevelDestination.Members),
            TopLevelDestination.entries.toList(),
        )
    }

    @Test
    fun `app opens on the accueil`() {
        assertEquals(TopLevelDestination.Home, TopLevelDestination.START)
    }

    @Test
    fun `each tab has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `the bar holds 4 tabs around Tout Familia, the planning being a page`() {
        // « La base » has no switch any more (Kévin le 18/11, 20 h 39); Planning left the bar (DESIGN-55).
        val bar = listOf(TopLevelDestination.Home, TopLevelDestination.Gifts, TopLevelDestination.HelpRequests, TopLevelDestination.Members)
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags()))
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags(mapOf("gifts" to false))))
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags(isOwner = true)))
    }
}
