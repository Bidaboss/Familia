package fr.familia.app.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Recipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A recipe of the list, with whose recipe it is. */
data class RecipeRow(val recipe: Recipe, val origin: String?)

sealed interface RecipesUiState {
    data object Loading : RecipesUiState
    data object Failed : RecipesUiState

    /**
     * [rows] already filtered by [query]; [total] counts the whole book, or the recipes of
     * [originName] when the list comes from the profile of a member.
     */
    data class Ready(val rows: List<RecipeRow>, val query: String, val total: Int, val originName: String? = null) : RecipesUiState
}

/** The recipe book, by title, filtered as the member types (PRD, récit 38), or only those of [originMemberId]. */
class RecipesViewModel(recipes: RecipeRepository?, members: MemberRepository?, originMemberId: String? = null) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<RecipesUiState> =
        if (recipes == null || members == null) {
            MutableStateFlow(RecipesUiState.Ready(emptyList(), "", 0)) // Firebase not configured (previews).
        } else {
            combine(recipes.observeRecipes(), members.observeFamily(), query) { all, family, q ->
                val book = if (originMemberId == null) all else all.filter { it.originMemberId == originMemberId }
                val originName = originMemberId?.let { id -> family.members.firstOrNull { it.id == id }?.firstName }
                val ready: RecipesUiState = RecipesUiState.Ready(
                    rows = book.filter { matches(it, q, family) }.map { RecipeRow(it, originLabel(it, family)) },
                    query = q,
                    total = book.size,
                    originName = originName,
                )
                ready
            }
                .catch { e -> if (e is CancellationException) throw e else emit(RecipesUiState.Failed) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState.Loading)
        }

    fun search(text: String) {
        query.value = text
    }
}

sealed interface RecipeDetailUiState {
    data object Loading : RecipeDetailUiState

    /** Removed meanwhile, or not readable. */
    data object Missing : RecipeDetailUiState

    data class Ready(val recipe: Recipe, val origin: String?, val canManage: Boolean, val removeFailed: Boolean = false) : RecipeDetailUiState

    data object Removed : RecipeDetailUiState
}

/** One recipe; its author or the admin principal may change or remove it. */
class RecipeDetailViewModel(
    private val recipes: RecipeRepository?,
    members: MemberRepository?,
    currentMemberId: String,
    private val recipeId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val removal = MutableStateFlow<Boolean?>(null) // null: nothing asked; true: removed; false: failed.

    val uiState: StateFlow<RecipeDetailUiState> =
        if (recipes == null || members == null) {
            MutableStateFlow(RecipeDetailUiState.Missing)
        } else {
            combine(recipes.observeRecipe(recipeId), members.observeFamily(), removal) { recipe, family: Family, removed ->
                val me = family.members.firstOrNull { it.id == currentMemberId }
                val state: RecipeDetailUiState = when {
                    removed == true -> RecipeDetailUiState.Removed
                    recipe == null || me == null -> RecipeDetailUiState.Missing
                    else -> RecipeDetailUiState.Ready(recipe, originLabel(recipe, family), canManageRecipe(recipe, me), removed == false)
                }
                state
            }
                .catch { e -> if (e is CancellationException) throw e else emit(RecipeDetailUiState.Missing) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState.Loading)
        }

    /** Removes the recipe, after the screen asked for confirmation. */
    fun remove() {
        val repo = recipes ?: return
        val ready = uiState.value as? RecipeDetailUiState.Ready ?: return
        if (!ready.canManage) return
        viewModelScope.launch {
            removal.value = try {
                // Offline, the removal waits in Firestore and is sent later.
                withTimeoutOrNull(writeTimeoutMillis) { repo.deleteRecipe(recipeId) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
    }
}
