package fr.familia.app.core.data

import fr.familia.app.core.model.Recipe
import kotlinx.coroutines.flow.Flow

/** The recipe book of the family (PRD, récit 38). See docs/data-model.md. */
interface RecipeRepository {
    /** Every recipe, by title, updated live. */
    fun observeRecipes(): Flow<List<Recipe>>

    fun observeRecipe(id: String): Flow<Recipe?>

    suspend fun getRecipe(id: String): Recipe?

    fun newRecipeId(): String

    suspend fun saveRecipe(recipe: Recipe, isNew: Boolean)

    suspend fun deleteRecipe(id: String)
}

/** By title, ignoring case and accents as a French reader would. */
val recipeOrder: Comparator<Recipe> = compareBy(java.text.Collator.getInstance(java.util.Locale.FRENCH)) { it.title }
