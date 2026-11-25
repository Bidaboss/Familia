package fr.familia.app.core.model

import java.time.Instant

/**
 * A family recipe (PRD, récit 38), in recipes/{recipeId}. [ingredients] and [steps] are kept as
 * texts, one per line. It belongs to its author only (récit 114); [origin] tells freely where it
 * comes from (« Mamie Jeanne »). [originMemberId] is a leftover of the « personne d'origine »:
 * the server turns it into [origin] text, so new recipes leave it null.
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
    /** One of 8 (DESIGN-170); an old recipe without one is « Divers ». */
    val category: RecipeCategory = RecipeCategory.Divers,
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

/** The category of a recipe (récit 114, DESIGN-170), stored by its [key] in the field "category". */
enum class RecipeCategory(val key: String) {
    Aperitif("aperitif"),
    Entree("entree"),
    Plat("plat"),
    Accompagnement("accompagnement"),
    Dessert("dessert"),
    Boisson("boisson"),
    Sauce("sauce"),
    Divers("divers"),
    ;

    companion object {
        /** No category or an unknown one (a recipe written before): « Divers ». */
        fun fromKey(key: String?): RecipeCategory = entries.firstOrNull { it.key == key } ?: Divers
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
