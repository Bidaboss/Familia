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

/** A recipe of the list, with whose recipe it is (« De … »: its origin, or who noted it). */
data class RecipeRow(val recipe: Recipe, val origin: String?)

sealed interface RecipesUiState {
    data object Loading : RecipesUiState
    data object Failed : RecipesUiState

    /**
     * [groups] already filtered by [query], by category; [total] counts the whole book, or the
     * recipes [memberName] noted when the list comes from the profile of a member. [isMine]: the
     * list of my own recipes. [canAdd]: « Nouvelle recette », only on the book and on my own list
     * (règle 19 du brief).
     */
    data class Ready(
        val groups: List<RecipeGroup>,
        val query: String,
        val total: Int,
        val memberName: String? = null,
        val isMine: Boolean = false,
        val canAdd: Boolean = true,
    ) : RecipesUiState {
        val rows: List<RecipeRow> get() = groups.flatMap { it.rows }
        val isMemberList: Boolean get() = memberName != null
    }
}

/**
 * The recipe book, grouped by category and filtered as the member types (PRD, récit 38; récit 114),
 * or only the recipes [memberId] noted, from their profile (DESIGN-171). The search of the book
 * also finds a first name; the one of a member's list does not (all are theirs).
 */
class RecipesViewModel(
    recipes: RecipeRepository?,
    members: MemberRepository?,
    memberId: String? = null,
    currentMemberId: String? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<RecipesUiState> =
        if (recipes == null || members == null) {
            MutableStateFlow(RecipesUiState.Ready(emptyList(), "", 0)) // Firebase not configured (previews).
        } else {
            combine(recipes.observeRecipes(), members.observeFamily(), query) { all, family, q ->
                val book = if (memberId == null) all else all.filter { it.authorId == memberId }
                val memberName = memberId?.let { id -> family.members.firstOrNull { it.id == id }?.firstName ?: book.firstOrNull()?.authorName ?: "" }
                val isMine = memberId != null && memberId == currentMemberId
                val rows = book.filter { matches(it, q, family, withNames = memberId == null) }.map { RecipeRow(it, fromLabel(it, family)) }
                val ready: RecipesUiState = RecipesUiState.Ready(
                    groups = groupByCategory(rows),
                    query = q,
                    total = book.size,
                    memberName = memberName,
                    isMine = isMine,
                    canAdd = memberId == null || isMine,
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

    /** [origin]: « De … », its origin or who noted it; [authorName]: who noted it, by today's first name. */
    data class Ready(
        val recipe: Recipe,
        val origin: String?,
        val canManage: Boolean,
        val removeFailed: Boolean = false,
        val authorName: String? = null,
    ) : RecipeDetailUiState

    data object Removed : RecipeDetailUiState
}

/** One recipe; only its author may change or remove it (DESIGN-173). */
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
                    else -> RecipeDetailUiState.Ready(
                        recipe,
                        fromLabel(recipe, family),
                        canManageRecipe(recipe, me),
                        removed == false,
                        authorLabel(recipe, family),
                    )
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
