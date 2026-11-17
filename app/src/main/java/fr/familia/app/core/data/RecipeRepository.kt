package fr.familia.app.core.data

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeComment
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

    /** The comments of a recipe, oldest first, updated live. */
    fun observeComments(recipeId: String): Flow<List<RecipeComment>>

    /** Adds a comment signed by [me]. */
    suspend fun addComment(recipeId: String, text: String, me: Member)

    suspend fun deleteComment(recipeId: String, commentId: String)
}

/** Oldest first; a comment just sent from this phone, with no server time yet, comes last. */
val recipeCommentOrder: Comparator<RecipeComment> =
    compareBy<RecipeComment> { it.createdAt ?: java.time.Instant.MAX }.thenBy { it.id }

/** By title, ignoring case and accents as a French reader would. */
val recipeOrder: Comparator<Recipe> = compareBy(java.text.Collator.getInstance(java.util.Locale.FRENCH)) { it.title }
