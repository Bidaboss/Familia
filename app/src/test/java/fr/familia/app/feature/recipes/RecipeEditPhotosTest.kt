package fr.familia.app.feature.recipes

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.UnreadablePhotoException
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The photos chosen while creating a recipe (retour de Kévin, 17/11), with the fictional test family. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditPhotosTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAlbum : AlbumPhotoRepository {
        val added = mutableListOf<Triple<String, Int, String>>()
        override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = MutableStateFlow(emptyList())
        override suspend fun addPhoto(folder: String, slot: Int, me: Member) {
            added += Triple(folder, slot, me.id)
        }
        override suspend fun deletePhoto(photo: AlbumPhoto) = Unit
    }

    private class FakeUploader(private val failing: Set<String> = emptySet()) : PhotoUploader {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) {
            if (source in failing) throw UnreadablePhotoException("unreadable")
            sent += source to photoPath
        }
    }

    private fun RecipeEditViewModel.fill() = update {
        it.copy(title = "Crêpes", ingredients = "farine\nlait", steps = "Mélanger.\nCuire.")
    }

    private val RecipeEditViewModel.editing get() = uiState.value as RecipeEditUiState.Editing

    @Test
    fun `a new recipe takes 3 photos at most, sent to its album once it is saved`() = runTest {
        val album = FakeAlbum()
        val uploader = FakeUploader()
        val vm = RecipeEditViewModel(FakeRecipes(), FakeMembers(), "adult2", recipeId = null, photos = album, uploader = uploader)
        vm.fill()
        vm.addPhotos(listOf("content://a", "content://b"))
        vm.addPhotos(listOf("content://b", "content://c", "content://d"))
        assertEquals(listOf("content://a", "content://b", "content://c"), vm.editing.photos)
        assertFalse(vm.editing.canAddPhotos)
        vm.removePhoto("content://b")
        assertTrue(vm.editing.canAddPhotos)

        vm.save()

        assertEquals(listOf("content://a" to "recipes/r-new/adult2/1.jpg", "content://c" to "recipes/r-new/adult2/2.jpg"), uploader.sent)
        assertEquals(listOf(Triple("recipes/r-new", 1, "adult2"), Triple("recipes/r-new", 2, "adult2")), album.added)
        assertEquals(RecipeEditUiState.Done("r-new"), vm.uiState.value)
    }

    @Test
    fun `a photo that cannot go never stops the recipe`() = runTest {
        val album = FakeAlbum()
        val vm = RecipeEditViewModel(FakeRecipes(), FakeMembers(), "adult2", recipeId = null, photos = album, uploader = FakeUploader(setOf("content://a")))
        vm.fill()
        vm.addPhotos(listOf("content://a", "content://b"))
        vm.save()
        assertEquals(listOf(Triple("recipes/r-new", 2, "adult2")), album.added)
        assertEquals(RecipeEditUiState.Done("r-new"), vm.uiState.value)
    }

    @Test
    fun `the child box goes with the photos, and is ignored without any`() = runTest {
        val repo = FakeRecipes()
        val vm = RecipeEditViewModel(repo, FakeMembers(), "adult2", recipeId = null, photos = FakeAlbum(), uploader = FakeUploader())
        vm.fill()
        vm.setChildInPhotos(true)
        vm.save()
        assertFalse(repo.saved.single().first.childInPhotos)

        val withPhoto = FakeRecipes()
        val other = RecipeEditViewModel(withPhoto, FakeMembers(), "adult2", recipeId = null, photos = FakeAlbum(), uploader = FakeUploader())
        other.fill()
        other.addPhotos(listOf("content://a"))
        other.setChildInPhotos(true)
        other.save()
        assertTrue(withPhoto.saved.single().first.childInPhotos)
    }

    @Test
    fun `no photo field when editing a recipe that exists`() = runTest {
        val apple = FakeRecipes().also {
            it.saveRecipe(fr.familia.app.core.model.Recipe("r1", "Gâteau", "", null, null, "farine", "Cuire.", emptyList(), "adult1", "Adult1", null), true)
        }
        val vm = RecipeEditViewModel(apple, FakeMembers(), "adult1", recipeId = "r1", photos = FakeAlbum(), uploader = FakeUploader())
        assertFalse(vm.editing.canAddPhotos)
        vm.addPhotos(listOf("content://a"))
        assertEquals(emptyList<String>(), vm.editing.photos)
    }
}
