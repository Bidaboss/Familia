package fr.familia.app.feature.home

import fr.familia.app.core.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecipeHomeChipTest {

    private val now = Instant.parse("2026-11-16T10:00:00Z")
    private val day = 86_400L

    private fun recipe(id: String, author: String, addedDaysAgo: Long?) =
        Recipe(id, "Tarte $id", "", null, null, "", "", emptyList(), author, "Inès", addedDaysAgo?.let { now.minusSeconds(it * day) })

    @Test
    fun `the newest recipe of someone else in the last 7 days, never mine`() {
        val recipes = listOf(recipe("r1", "m2", 6), recipe("r2", "m2", 1), recipe("r3", "m1", 0), recipe("r4", "m3", 8), recipe("r5", "m2", null))
        assertEquals("r2", newRecipeFor(recipes, "m1", now)?.id)
        assertNull(newRecipeFor(listOf(recipe("r3", "m1", 0), recipe("r4", "m3", 8)), "m1", now))
    }
}
