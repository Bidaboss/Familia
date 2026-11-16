package fr.familia.app.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
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
        )
    }
}

enum class RecipeFormError { TitleMissing, TitleTooLong, OriginTooLong, ServingsInvalid, IngredientsMissing, IngredientsTooLong, StepsMissing, StepsTooLong }

fun validate(form: RecipeForm): Set<RecipeFormError> = buildSet {
    if (form.title.isBlank()) add(RecipeFormError.TitleMissing)
    if (form.title.trim().length > Recipe.MAX_TITLE) add(RecipeFormError.TitleTooLong)
    if (form.origin.trim().length > Recipe.MAX_ORIGIN) add(RecipeFormError.OriginTooLong)
    if (form.servings.isNotBlank() && (form.servings.trim().toIntOrNull() ?: 0) !in 1..Recipe.MAX_SERVINGS) add(RecipeFormError.ServingsInvalid)
    if (Recipe.lines(form.ingredients).isEmpty()) add(RecipeFormError.IngredientsMissing)
    if (form.ingredients.trim().length > Recipe.MAX_INGREDIENTS) add(RecipeFormError.IngredientsTooLong)
    if (Recipe.lines(form.steps).isEmpty()) add(RecipeFormError.StepsMissing)
    if (form.steps.trim().length > Recipe.MAX_STEPS) add(RecipeFormError.StepsTooLong)
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
        val errors: Set<RecipeFormError>,
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : RecipeEditUiState

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
        _uiState.value = RecipeEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            members = family.members.sortedBy { it.firstName.lowercase() }.map { OriginChoice(it.id, it.firstName) },
            errors = emptySet(),
        )
    }

    private fun unavailable() {
        _uiState.value = RecipeEditUiState.Unavailable
    }

    fun update(change: (RecipeForm) -> RecipeForm) {
        val state = _uiState.value as? RecipeEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
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
                _uiState.value = RecipeEditUiState.Done(recipe.id)
            } else {
                (_uiState.value as? RecipeEditUiState.Editing)?.let { _uiState.value = it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }
}
