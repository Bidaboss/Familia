package fr.familia.app.feature.recipes.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeCategory
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
    fun `the book has one chapter per category, in the order of the carnet, recipes by title, empty ones left out`() {
        val sections = bookSections(
            listOf(
                item(recipe("1", "Tarte", listOf("dessert")).copy(category = RecipeCategory.Dessert)),
                item(recipe("2", "Crêpes")),
                item(recipe("3", "Clafoutis", listOf("été")).copy(category = RecipeCategory.Dessert)),
                item(recipe("4", "Soupe", listOf("hiver")).copy(category = RecipeCategory.Entree)),
            ),
        )
        assertEquals(listOf(RecipeCategory.Entree, RecipeCategory.Dessert, RecipeCategory.Divers), sections.map { it.category })
        assertEquals(listOf("Clafoutis", "Tarte"), sections[1].recipes.map { it.recipe.title })
        assertEquals(listOf("Crêpes"), sections.last().recipes.map { it.recipe.title })
    }

    @Test
    fun `a photo taken by an adult of a recipe from an adult goes in the book`() {
        assertTrue(bookPhotoAllowed(photo("1", "adult1"), recipe("1", "Tarte"), "adult2", TestFamily.family))
    }

    @Test
    fun `a photo taken by a child of a recipe from an adult goes to everyone`() {
        val byKid = photo("1", "kid1")
        assertTrue(bookPhotoAllowed(byKid, recipe("1", "Tarte"), "adult1", TestFamily.family))
        assertTrue(bookPhotoAllowed(byKid, recipe("1", "Tarte"), "adult2", TestFamily.family))
    }

    @Test
    fun `the photo of a recipe written by a child stays with the adults of that child`() {
        val family = TestFamily.family
        val byKid = recipe("1", "Gâteau de Kid1", author = "kid1")
        assertTrue(bookPhotoAllowed(photo("1", "adult1"), byKid, "adult1", family))
        assertFalse(bookPhotoAllowed(photo("1", "adult1"), byKid, "adult2", family))
        assertFalse(bookPhotoAllowed(photo("1", "kid1"), byKid, "admin1", family))

        val byOtherKid = recipe("2", "Gâteau de Kid2", author = "kid2")
        assertTrue(bookPhotoAllowed(photo("2", "adult2"), byOtherKid, "adult2", family))
        assertFalse(bookPhotoAllowed(photo("2", "adult2"), byOtherKid, "adult1", family))
    }

    @Test
    fun `a child without any adult keeps the photo of its recipe out of the book`() {
        val orphan = TestFamily.family.members.first { it.id == "kid1" }.copy(id = "kid3", parentIds = emptyList())
        val family = TestFamily.family.copy(members = TestFamily.family.members + orphan)
        assertFalse(bookPhotoAllowed(photo("1", "adult1"), recipe("1", "Tarte", author = "kid3"), "adult1", family))
    }

    @Test
    fun `a photo by someone who left the family can no longer be checked, so it stays out`() {
        assertFalse(bookPhotoAllowed(photo("1", "gone"), recipe("1", "Tarte"), "adult1", TestFamily.family))
    }

    @Test
    fun `a recipe whose author left the family can no longer be checked, so its photo stays out`() {
        assertFalse(bookPhotoAllowed(photo("1", "adult1"), recipe("1", "Tarte", author = "gone"), "adult1", TestFamily.family))
    }
}
