package fr.familia.app.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.pickGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the member types. [servings] stays a text until saved, so a half-typed number is kept. */
data class RecipeForm(
    val title: String = "",
    val originMemberId: String? = null,
    val origin: String = "",
    val servings: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val tags: String = "",
    val story: String = "",
) {
    companion object {
        fun from(recipe: Recipe) = RecipeForm(
            title = recipe.title,
            originMemberId = recipe.originMemberId,
            origin = recipe.origin,
            servings = recipe.servings?.toString().orEmpty(),
            ingredients = recipe.ingredients,
            steps = recipe.steps,
            tags = recipe.tags.joinToString(", "),
            story = recipe.story,
        )
    }
}

enum class RecipeFormError { TitleMissing, TitleTooLong, OriginTooLong, ServingsInvalid, IngredientsMissing, IngredientsTooLong, StepsMissing, StepsTooLong, StoryTooLong }

fun validate(form: RecipeForm): Set<RecipeFormError> = buildSet {
    if (form.title.isBlank()) add(RecipeFormError.TitleMissing)
    if (form.title.trim().length > Recipe.MAX_TITLE) add(RecipeFormError.TitleTooLong)
    if (form.origin.trim().length > Recipe.MAX_ORIGIN) add(RecipeFormError.OriginTooLong)
    if (form.servings.isNotBlank() && (form.servings.trim().toIntOrNull() ?: 0) !in 1..Recipe.MAX_SERVINGS) add(RecipeFormError.ServingsInvalid)
    if (Recipe.lines(form.ingredients).isEmpty()) add(RecipeFormError.IngredientsMissing)
    if (form.ingredients.trim().length > Recipe.MAX_INGREDIENTS) add(RecipeFormError.IngredientsTooLong)
    if (Recipe.lines(form.steps).isEmpty()) add(RecipeFormError.StepsMissing)
    if (form.steps.trim().length > Recipe.MAX_STEPS) add(RecipeFormError.StepsTooLong)
    if (form.story.trim().length > Recipe.MAX_STORY) add(RecipeFormError.StoryTooLong)
}

/** A member who may be named as the origin of the recipe. */
data class OriginChoice(val id: String, val firstName: String)

sealed interface RecipeEditUiState {
    data object Loading : RecipeEditUiState

    /** Removed meanwhile, or not a recipe this member may change. */
    data object Unavailable : RecipeEditUiState

    data class Editing(
        val form: RecipeForm,
        val isNew: Boolean,
        val members: List<OriginChoice>,
        /** The same members by household, for « De qui vient la recette ? » (règle de Kévin, 17/11). */
        val groups: List<PickGroup> = emptyList(),
        val errors: Set<RecipeFormError>,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        /** A new recipe only: the photos of the dish chosen on the phone, sent once it is saved. */
        val photos: List<String> = emptyList(),
        /** Sending photo n after the recipe is written; null otherwise. */
        val sendingPhoto: Int? = null,
    ) : RecipeEditUiState {
        val canAddPhotos: Boolean get() = isNew && photos.size < RECIPE_PHOTOS && !isSaving
    }

    /** Saved (or queued until the network is back): [recipeId] opens next. */
    data class Done(val recipeId: String) : RecipeEditUiState
}

/** Write a recipe ([recipeId] null) or change one (PRD, récit 38). Removing is on the recipe itself. */
class RecipeEditViewModel(
    private val recipes: RecipeRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val recipeId: String?,
    private val writeTimeoutMillis: Long = 4_000,
    /** Keeps the draft when Android stops the app in the background (relecture #82, C3). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** The photos of a new recipe go to its album once it is written (retour de Kévin, 17/11). */
    private val photos: AlbumPhotoRepository? = null,
    private val uploader: PhotoUploader? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecipeEditUiState>(RecipeEditUiState.Loading)
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private var original: Recipe? = null
    private var me: Member? = null
    private var editedId = ""
    private var initialForm = RecipeForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val recipeRepo = recipes ?: return
        val memberRepo = members ?: return
        val loaded = try {
            memberRepo.observeFamily().first() to recipeId?.let { recipeRepo.getRecipe(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val family = loaded?.first ?: return unavailable()
        val existing = loaded.second
        val current = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (recipeId != null && (existing == null || !canManageRecipe(existing, current, family))) return unavailable()
        me = current
        original = existing
        editedId = existing?.id ?: recipeRepo.newRecipeId()
        initialForm = existing?.let { RecipeForm.from(it) } ?: RecipeForm()
        val form = restoreDraft() ?: initialForm
        _uiState.value = RecipeEditUiState.Editing(
            form = form,
            hasChanges = form != initialForm,
            isNew = existing == null,
            members = family.members.sortedBy { it.firstName.lowercase() }.map { OriginChoice(it.id, it.firstName) },
            groups = pickGroups(family.members, family, current.householdId),
            errors = emptySet(),
        )
    }

    private fun unavailable() {
        _uiState.value = RecipeEditUiState.Unavailable
    }

    fun update(change: (RecipeForm) -> RecipeForm) {
        val state = _uiState.value as? RecipeEditUiState.Editing ?: return
        val form = change(state.form)
        keepDraft(form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    /** Photos chosen on the phone for a new recipe, [RECIPE_PHOTOS] at most. */
    fun addPhotos(uris: List<String>) {
        val state = _uiState.value as? RecipeEditUiState.Editing ?: return
        if (!state.canAddPhotos) return
        _uiState.value = state.copy(photos = (state.photos + uris).distinct().take(RECIPE_PHOTOS), hasChanges = true)
    }

    fun removePhoto(uri: String) {
        val state = _uiState.value as? RecipeEditUiState.Editing ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(photos = state.photos - uri)
    }

    private fun keepDraft(form: RecipeForm) {
        savedState[DRAFT_TITLE] = form.title
        savedState[DRAFT_ORIGIN_MEMBER] = form.originMemberId.orEmpty()
        savedState[DRAFT_ORIGIN] = form.origin
        savedState[DRAFT_SERVINGS] = form.servings
        savedState[DRAFT_INGREDIENTS] = form.ingredients
        savedState[DRAFT_STEPS] = form.steps
        savedState[DRAFT_TAGS] = form.tags
        savedState[DRAFT_STORY] = form.story
    }

    private fun restoreDraft(): RecipeForm? {
        val title = savedState.get<String>(DRAFT_TITLE) ?: return null
        return RecipeForm(
            title = title,
            originMemberId = savedState.get<String>(DRAFT_ORIGIN_MEMBER)?.takeIf { it.isNotEmpty() },
            origin = savedState.get<String>(DRAFT_ORIGIN).orEmpty(),
            servings = savedState.get<String>(DRAFT_SERVINGS).orEmpty(),
            ingredients = savedState.get<String>(DRAFT_INGREDIENTS).orEmpty(),
            steps = savedState.get<String>(DRAFT_STEPS).orEmpty(),
            tags = savedState.get<String>(DRAFT_TAGS).orEmpty(),
            story = savedState.get<String>(DRAFT_STORY).orEmpty(),
        )
    }

    /**
     * The photos of a new recipe, once it is written: each in its place of the album (1 to 3), the
     * files first, then the record, as on the recipe itself. A photo that cannot go is left out: it
     * can still be added from the recipe.
     */
    private suspend fun sendPhotos(recipeId: String, author: Member, sources: List<String>) {
        val repo = photos ?: return
        val upload = uploader ?: return
        sources.take(RECIPE_PHOTOS).forEachIndexed { index, source ->
            (_uiState.value as? RecipeEditUiState.Editing)?.let { _uiState.value = it.copy(sendingPhoto = index + 1) }
            val photo = AlbumPhoto(recipePhotoFolder(recipeId), author.id, author.firstName, index + 1, createdAt = null)
            try {
                upload.upload(source, photo.photoPath, photo.thumbnailPath)
                withTimeoutOrNull(writeTimeoutMillis) { repo.addPhoto(photo.folder, photo.slot, author) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unreadable, refused or no network: the recipe is there, the photo can wait.
            }
        }
    }

    fun save() {
        val state = _uiState.value as? RecipeEditUiState.Editing ?: return
        val repo = recipes ?: return
        val author = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val form = state.form
        val recipe = Recipe(
            id = editedId,
            title = form.title.trim(),
            // A member of the family is named by their card; the free origin is for anyone else.
            origin = if (form.originMemberId != null) "" else form.origin.trim(),
            originMemberId = form.originMemberId,
            servings = form.servings.trim().toIntOrNull(),
            ingredients = Recipe.lines(form.ingredients).joinToString("\n"),
            steps = Recipe.lines(form.steps).joinToString("\n"),
            tags = parseTags(form.tags),
            authorId = original?.authorId ?: author.id,
            authorName = original?.authorName ?: author.firstName,
            createdAt = original?.createdAt,
            story = form.story.trim(),
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val written = try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.saveRecipe(recipe, isNew = original == null) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (written) {
                if (original == null) sendPhotos(recipe.id, author, state.photos)
                _uiState.value = RecipeEditUiState.Done(recipe.id)
            } else {
                (_uiState.value as? RecipeEditUiState.Editing)?.let { _uiState.value = it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private companion object {
        const val DRAFT_TITLE = "recipe_draft_title"
        const val DRAFT_ORIGIN_MEMBER = "recipe_draft_origin_member"
        const val DRAFT_ORIGIN = "recipe_draft_origin"
        const val DRAFT_SERVINGS = "recipe_draft_servings"
        const val DRAFT_INGREDIENTS = "recipe_draft_ingredients"
        const val DRAFT_STEPS = "recipe_draft_steps"
        const val DRAFT_TAGS = "recipe_draft_tags"
        const val DRAFT_STORY = "recipe_draft_story"
    }
}
