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

        fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}
