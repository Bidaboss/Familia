package fr.familia.app.feature.recipes.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Recipe
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The book of récit 47, with the fictional test family (see TestFamily). */
class RecipeBookTest {

    private fun recipe(id: String, title: String, tags: List<String> = emptyList(), origin: String? = null, author: String = "adult1") =
        Recipe(id, title, "", origin, null, "farine", "Cuire.", tags, author, author, null)

    private fun photo(recipeId: String, author: String) = AlbumPhoto("recipes/$recipeId", author, author, 1, null)

    private fun item(recipe: Recipe) = BookRecipe(recipe, null, null)

    @Test
    fun `the contents go by first tag, by name, recipes by title, and those without a tag last`() {
        val sections = bookSections(
            listOf(
                item(recipe("1", "Tarte", listOf("dessert"))),
                item(recipe("2", "Crêpes")),
                item(recipe("3", "Clafoutis", listOf("Dessert", "été"))),
                item(recipe("4", "Soupe", listOf("hiver"))),
            ),
        )
        assertEquals(listOf("Dessert", "Hiver", null), sections.map { it.name })
        assertEquals(listOf("Clafoutis", "Tarte"), sections.first().recipes.map { it.recipe.title })
        assertEquals(listOf("Crêpes"), sections.last().recipes.map { it.recipe.title })
    }

    @Test
    fun `a photo taken by an adult of a recipe from an adult goes in the book`() {
        assertTrue(bookPhotoAllowed(photo("1", "adult1"), recipe("1", "Tarte"), "adult2", TestFamily.family))
    }

    @Test
    fun `a photo taken by a child, or of a recipe from a child, stays with the adults of that child`() {
        val family = TestFamily.family
        val byKid = photo("1", "kid1")
        assertTrue(bookPhotoAllowed(byKid, recipe("1", "Tarte", author = "kid1"), "adult1", family))
        assertFalse(bookPhotoAllowed(byKid, recipe("1", "Tarte", author = "kid1"), "adult2", family))

        val fromKid = recipe("2", "Gâteau de Kid2", origin = "kid2")
        assertTrue(bookPhotoAllowed(photo("2", "adult2"), fromKid, "adult2", family))
        assertFalse(bookPhotoAllowed(photo("2", "adult2"), fromKid, "admin1", family))
    }

    @Test
    fun `a photo by someone who left the family can no longer be checked, so it stays out`() {
        assertFalse(bookPhotoAllowed(photo("1", "gone"), recipe("1", "Tarte"), "adult1", TestFamily.family))
    }
}
