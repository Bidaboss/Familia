package fr.familia.app.feature.recipes.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.recipes.FakeRecipes
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** « Faire le livre » (récit 47): the choices of the screen, then the printer. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeBookViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAlbums(initial: List<AlbumPhoto>) : AlbumPhotoRepository {
        val photos = MutableStateFlow(initial)
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = photos.map { list -> list.filter { it.folder == folder } }
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) = Unit
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    private fun recipe(id: String, title: String, tags: List<String> = emptyList()) =
        Recipe(id, title, "", null, null, "farine", "Cuire.", tags, "adult1", "Adult1", null)

    private fun photo(recipeId: String, author: String) = AlbumPhoto("recipes/$recipeId", author, author, 1, null)

    @Test
    fun `every recipe is ticked at first, the cover follows the ticks, and the book goes to the printer`() = runTest {
        val tart = recipe("1", "Tarte", listOf("dessert"))
        val soup = recipe("2", "Soupe")
        var printed: BookContent? = null
        val file = File("livre.pdf")
        val vm = RecipeBookViewModel(
            recipes = FakeRecipes(listOf(tart, soup)),
            members = FakeMembers(),
            photos = FakeAlbums(listOf(photo("1", "adult1"))),
            currentMemberId = "adult2",
            defaultTitle = "Les recettes de la famille",
            year = 2026,
            print = { printed = it; file },
        )
        val state = vm.uiState.value as RecipeBookUiState.Choosing
        assertEquals(setOf("1", "2"), state.chosen)
        assertEquals(listOf("1"), state.coverChoices.map { it.recipe.id })

        vm.setCover("1")
        vm.toggle("1")
        assertNull((vm.uiState.value as RecipeBookUiState.Choosing).coverRecipeId)

        vm.toggle("1")
        vm.setCover("1")
        vm.setTitle("  Nos recettes ")
        vm.make()

        val content = printed!!
        assertEquals("Nos recettes", content.title)
        assertEquals(photo("1", "adult1"), content.coverPhoto)
        assertEquals(listOf("Dessert", null), content.sections.map { it.name })
        assertEquals(file, vm.made.value)
        vm.shared()
        assertNull(vm.made.value)
    }

    @Test
    fun `no book without a recipe or without a title`() = runTest {
        val vm = RecipeBookViewModel(FakeRecipes(listOf(recipe("1", "Tarte"))), FakeMembers(), null, "adult1", "Livre", 2026, print = { null })
        vm.setTitle(" ")
        assertFalse((vm.uiState.value as RecipeBookUiState.Choosing).canMake)
        vm.setTitle("Livre")
        vm.chooseAll(false)
        assertFalse((vm.uiState.value as RecipeBookUiState.Choosing).canMake)

        vm.chooseAll(true)
        vm.make()
        assertTrue((vm.uiState.value as RecipeBookUiState.Choosing).failed)

        val empty = RecipeBookViewModel(FakeRecipes(), FakeMembers(), null, "adult1", "Livre", 2026, print = { null })
        assertEquals(RecipeBookUiState.Empty, empty.uiState.value)
    }
}
