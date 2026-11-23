package fr.familia.app.core.navigation

import fr.familia.app.core.model.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `tabs are listed accueil, planning, gifts, entraide, voyages and family in that order`() {
        assertEquals(
            listOf(TopLevelDestination.Home, TopLevelDestination.Planning, TopLevelDestination.Gifts, TopLevelDestination.HelpRequests, TopLevelDestination.Trips, TopLevelDestination.Members),
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
    fun `the bar holds 4 tabs around Tout Familia, Voyages in place of Entraide, the planning being a page`() {
        // « La base » has no switch (Kévin le 18/11), and Voyages joined it on 22/11 with no switch either.
        val bar = listOf(TopLevelDestination.Home, TopLevelDestination.Gifts, TopLevelDestination.Trips, TopLevelDestination.Members)
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags()))
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags(mapOf("trips" to false, "gifts" to false))))
        assertEquals(bar, TopLevelDestination.visible(FeatureFlags(isOwner = true)))
    }

    @Test
    fun `Planning and Entraide are pages of the Accueil, out of the bar`() {
        val flags = FeatureFlags()
        assertTrue(TopLevelDestination.isPage(TopLevelDestination.HelpRequests, flags))
        assertTrue(TopLevelDestination.isPage(TopLevelDestination.Planning, flags))
        assertFalse(TopLevelDestination.isPage(TopLevelDestination.Trips, flags))
        assertFalse(TopLevelDestination.isPage(TopLevelDestination.Home, flags))
    }
}
