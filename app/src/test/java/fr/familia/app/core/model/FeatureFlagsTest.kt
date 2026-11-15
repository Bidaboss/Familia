package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {

    @Test
    fun `a module stays hidden from the family until it is switched on`() {
        assertFalse(FeatureFlags().isOn(Feature.Gifts))
        assertFalse(FeatureFlags(mapOf("gifts" to false)).isOn(Feature.Gifts))
        assertTrue(FeatureFlags(mapOf("gifts" to true)).isOn(Feature.Gifts))
    }

    @Test
    fun `the admin principal always sees every module`() {
        val flags = FeatureFlags(emptyMap(), isOwner = true)
        Feature.entries.forEach { assertTrue(flags.isOn(it)) }
        assertFalse(flags.isOnForFamily(Feature.Trips))
    }

    @Test
    fun `every module has its own key`() {
        assertEquals(Feature.entries.size, Feature.entries.map { it.key }.toSet().size)
    }
}
