package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-2: the three groups of Administration > Modules. */
class FeatureTiersTest {

    @Test
    fun `the base holds the everyday modules, and each switch lands in exactly one group`() {
        assertEquals(FeatureTier.Base, Feature.Home.tier)
        assertEquals(FeatureTier.Base, Feature.Gifts.tier)
        assertEquals(FeatureTier.NewVersion, Feature.FamilyTabV2.tier)
        assertEquals(FeatureTier.Base, Feature.Trips.tier)
        assertEquals(FeatureTier.Choice, Feature.NewsFeed.tier)
        val counts = Feature.entries.groupingBy { it.tier }.eachCount()
        assertEquals(19, counts[FeatureTier.Base])
        assertEquals(3, counts[FeatureTier.NewVersion])
        assertTrue(counts.getValue(FeatureTier.Choice) > 0)
        assertEquals(Feature.entries.size, counts.values.sum())
    }
}
