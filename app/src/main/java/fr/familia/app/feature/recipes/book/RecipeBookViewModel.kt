package fr.familia.app.feature.recipes.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.feature.recipes.originLabel
import fr.familia.app.feature.recipes.recipePhotoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

sealed interface RecipeBookUiState {
    data object Loading : RecipeBookUiState

    /** No recipe yet, or the recipes could not be read. */
    data object Empty : RecipeBookUiState

    data class Choosing(
        val recipes: List<BookRecipe>,
        /** Every recipe is in the book at first; the member unticks those to leave out. */
        val chosen: Set<String>,
        val title: String,
        val year: Int,
        /** The recipe whose photo is on the cover, or null for the drawing. */
        val coverRecipeId: String? = null,
        val isMaking: Boolean = false,
        val failed: Boolean = false,
    ) : RecipeBookUiState {
        val canMake: Boolean get() = chosen.isNotEmpty() && title.isNotBlank() && !isMaking

        /** The chosen recipes that have a photo allowed in the book: they may give the cover. */
        val coverChoices: List<BookRecipe> get() = recipes.filter { it.recipe.id in chosen && it.photo != null }
    }
}

/**
 * « Faire le livre » (récit 47): reads the recipes and their photos once, keeps for each the first
 * photo carte EXPERT-2 lets the member put in the book, then [print] writes the PDF.
 */
class RecipeBookViewModel(
    private val recipes: RecipeRepository?,
    private val members: MemberRepository?,
    private val photos: AlbumPhotoRepository?,
    private val currentMemberId: String,
    private val defaultTitle: String,
    private val year: Int,
    /** Writes the book and gives its file, or null when it failed. */
    private val print: suspend (BookContent) -> File?,
    private val readTimeoutMillis: Long = 10_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeBookUiState>(RecipeBookUiState.Loading)
    val uiState: StateFlow<RecipeBookUiState> = _uiState.asStateFlow()

    private val _made = MutableStateFlow<File?>(null)

    /** The book just made, for the share sheet; [shared] clears it. */
    val made: StateFlow<File?> = _made.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val recipeRepo = recipes
        val memberRepo = members
        val items = if (recipeRepo == null || memberRepo == null) null else try {
            withTimeoutOrNull(readTimeoutMillis) {
                val family = memberRepo.observeFamily().first()
                val list = recipeRepo.observeRecipes().first()
                list.map { recipe ->
                    async {
                        val all = photosOf(recipe.id)
                        BookRecipe(recipe, originLabel(recipe, family), bookPhoto(all, recipe, currentMemberId, family))
                    }
                }.awaitAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _uiState.value = if (items.isNullOrEmpty()) {
            RecipeBookUiState.Empty
        } else {
            RecipeBookUiState.Choosing(items, items.map { it.recipe.id }.toSet(), defaultTitle, year)
        }
    }

    /** No photo when they cannot be read: the book still has its drawings. */
    private suspend fun photosOf(recipeId: String): List<AlbumPhoto> = try {
        photos?.observePhotos(recipePhotoFolder(recipeId))?.first().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun change(transform: (RecipeBookUiState.Choosing) -> RecipeBookUiState.Choosing) {
        val state = _uiState.value as? RecipeBookUiState.Choosing ?: return
        if (state.isMaking) return
        val next = transform(state).copy(failed = false)
        // The cover follows the recipes: an unticked one no longer gives it.
        _uiState.value = if (next.coverRecipeId != null && next.coverChoices.none { it.recipe.id == next.coverRecipeId }) next.copy(coverRecipeId = null) else next
    }

    fun toggle(recipeId: String) = change { it.copy(chosen = if (recipeId in it.chosen) it.chosen - recipeId else it.chosen + recipeId) }

    fun chooseAll(all: Boolean) = change { state -> state.copy(chosen = if (all) state.recipes.map { it.recipe.id }.toSet() else emptySet()) }

    fun setTitle(title: String) = change { it.copy(title = title.take(BOOK_TITLE_MAX)) }

    fun setCover(recipeId: String?) = change { it.copy(coverRecipeId = recipeId) }

    fun make() {
        val state = _uiState.value as? RecipeBookUiState.Choosing ?: return
        if (!state.canMake) return
        val chosen = state.recipes.filter { it.recipe.id in state.chosen }
        val content = BookContent(
            title = state.title.trim(),
            year = state.year,
            coverPhoto = chosen.firstOrNull { it.recipe.id == state.coverRecipeId }?.photo,
            sections = bookSections(chosen),
        )
        _uiState.value = state.copy(isMaking = true, failed = false)
        viewModelScope.launch {
            val file = try {
                print(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            (_uiState.value as? RecipeBookUiState.Choosing)?.let { _uiState.value = it.copy(isMaking = false, failed = file == null) }
            _made.value = file
        }
    }

    fun shared() {
        _made.value = null
    }
}
