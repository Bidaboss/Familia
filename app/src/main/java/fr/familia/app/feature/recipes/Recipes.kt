package fr.familia.app.feature.recipes

import fr.familia.app.core.album.AlbumRights
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.isOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import java.text.Normalizer

/*
 * The recipe book (PRD, récit 38): every member reads every recipe and writes their own. The
 * author, or the admin principal, edits and removes it; the rules check the same
 * (firestore.rules, « Carnet de recettes »). Hidden behind Feature.Recipes.
 */

@Serializable
data object RecipesDestination

@Serializable
data class RecipeDetailDestination(val recipeId: String)

/** [recipeId] null: a new recipe. */
@Serializable
data class RecipeEditDestination(val recipeId: String? = null)

fun canManageRecipe(recipe: Recipe, me: Member, family: Family): Boolean = recipe.authorId == me.id || family.isOwner(me)

/** The photos of the dish: records in recipes/{id}/photos, files in Storage under the same folder. */
fun recipePhotoFolder(recipeId: String): String = "recipes/$recipeId"

/** Three photos of the dish are enough; the rules allow places 1 to 3. */
const val RECIPE_PHOTOS = 3

/** Whoever may change the recipe adds and removes its photos; everyone sees them. */
fun recipePhotoRights(recipes: RecipeRepository, members: MemberRepository, recipeId: String, currentMemberId: String): Flow<AlbumRights> =
    combine(recipes.observeRecipe(recipeId), members.observeFamily()) { recipe, family ->
        val me = family.members.firstOrNull { it.id == currentMemberId }
        val manages = recipe != null && me != null && canManageRecipe(recipe, me, family)
        AlbumRights(me = me.takeIf { recipe != null }, canAdd = manages, canManage = manages)
    }

/** Whose recipe it is: the first name of the member, or the free origin. */
fun originLabel(recipe: Recipe, family: Family): String? =
    recipe.originMemberId?.let { id -> family.members.firstOrNull { it.id == id }?.firstName }
        ?: recipe.origin.trim().takeIf { it.isNotEmpty() }

/** Lower case, without accents, so « creme » finds « Crème ». */
fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** True when every word of [query] is in the title, the origin, the tags or the ingredients. */
fun matches(recipe: Recipe, query: String, family: Family): Boolean {
    val words = searchKey(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = searchKey(listOfNotNull(recipe.title, originLabel(recipe, family), recipe.ingredients).plus(recipe.tags).joinToString(" "))
    return words.all { it in haystack }
}

/** « dessert, Noël , dessert » → [dessert, Noël]: trimmed, without doubles, six at most. */
fun parseTags(text: String): List<String> = text.split(',')
    .map { it.trim().take(24) }
    .filter { it.isNotEmpty() }
    .distinctBy { searchKey(it) }
    .take(Recipe.MAX_TAGS)
