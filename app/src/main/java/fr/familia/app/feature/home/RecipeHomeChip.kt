package fr.familia.app.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Recipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

/** How long « Nouvelle recette » stays on the Accueil after a recipe is added (PRODUIT, 16/11). */
internal val NEW_RECIPE_DAYS: Duration = Duration.ofDays(7)

/** The newest recipe added by someone else in the last 7 days, or null: the chip then stays away. */
internal fun newRecipeFor(recipes: List<Recipe>, meId: String, now: Instant): Recipe? =
    recipes
        .filter { it.authorId != meId && it.createdAt != null && it.createdAt <= now && now < it.createdAt.plus(NEW_RECIPE_DAYS) }
        .maxByOrNull { it.createdAt!! }

class RecipeHomeViewModel(repository: RecipeRepository?, meId: String, clock: () -> Instant = Instant::now) : ViewModel() {
    val recipe: StateFlow<Recipe?> = (repository?.observeRecipes() ?: flowOf(emptyList()))
        .map { newRecipeFor(it, meId, clock()) }
        .catch { e -> if (e is CancellationException) throw e else emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** « Nouvelle recette : … », which opens the recipe itself (DESIGN), only while it is new. */
@Composable
fun RecipeHomeChip(container: AppContainer?, meId: String, onOpenRecipe: (String) -> Unit) {
    val viewModel: RecipeHomeViewModel = viewModel(
        key = "recipeHome-$meId",
        factory = viewModelFactory { initializer { RecipeHomeViewModel(container?.recipeRepository, meId) } },
    )
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val shown = recipe ?: return
    HomeChip(stringResource(R.string.home_new_recipe, shown.title), onClick = { onOpenRecipe(shown.id) })
}
