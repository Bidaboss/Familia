package fr.familia.app.core.model

import java.time.Instant

/**
 * A family recipe (PRD, récit 38), in recipes/{recipeId}. [ingredients] and [steps] are kept as
 * texts, one per line; [originMemberId] says whose recipe it is when they are in the family,
 * otherwise [origin] tells it freely (« Mamie Jeanne »).
 */
data class Recipe(
    val id: String,
    val title: String,
    val origin: String,
    val originMemberId: String?,
    val servings: Int?,
    val ingredients: String,
    val steps: String,
    val tags: List<String>,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant?,
    /** Its little story (« Mamie la faisait chaque dimanche »), for the book of récit 47; may be empty. */
    val story: String = "",
) {
    val ingredientLines: List<String> get() = lines(ingredients)
    val stepLines: List<String> get() = lines(steps)

    companion object {
        const val MAX_TITLE = 80
        const val MAX_ORIGIN = 80
        const val MAX_INGREDIENTS = 3_000
        const val MAX_STEPS = 6_000
        const val MAX_TAGS = 6
        const val MAX_SERVINGS = 50
        const val MAX_STORY = 600

        fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** A comment under a recipe (« Je la fais avec moins de sucre »), in recipes/{recipeId}/comments. */
data class RecipeComment(
    val id: String,
    val text: String,
    val authorId: String,
    /** Kept so the comment stays signed once its author has left. */
    val authorName: String,
    /** Null just after this phone sent it, until the server time arrives. */
    val createdAt: Instant?,
    /** The members named « @Prénom » in [text] (Kévin, 19/11). */
    val mentionIds: List<String> = emptyList(),
) {
    companion object {
        const val MAX_TEXT = 500
    }
}
