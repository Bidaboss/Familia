package fr.familia.app.feature.recipes

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import fr.familia.app.R
import fr.familia.app.core.model.RecipeCategory

/** How a category shows (DESIGN-170): its emoji, its name (« Entrée ») and its group title (« Entrées (2) »). */
data class RecipeCategoryLook(val emoji: String, @StringRes val labelRes: Int, @PluralsRes val groupRes: Int)

val RecipeCategory.look: RecipeCategoryLook
    get() = when (this) {
        RecipeCategory.Aperitif -> RecipeCategoryLook("🥂", R.string.recipe_category_aperitif, R.plurals.recipe_group_aperitif)
        RecipeCategory.Entree -> RecipeCategoryLook("🥗", R.string.recipe_category_entree, R.plurals.recipe_group_entree)
        RecipeCategory.Plat -> RecipeCategoryLook("🍽", R.string.recipe_category_plat, R.plurals.recipe_group_plat)
        RecipeCategory.Accompagnement -> RecipeCategoryLook("🥕", R.string.recipe_category_accompagnement, R.plurals.recipe_group_accompagnement)
        RecipeCategory.Dessert -> RecipeCategoryLook("🍰", R.string.recipe_category_dessert, R.plurals.recipe_group_dessert)
        RecipeCategory.Boisson -> RecipeCategoryLook("🍹", R.string.recipe_category_boisson, R.plurals.recipe_group_boisson)
        RecipeCategory.Sauce -> RecipeCategoryLook("🥣", R.string.recipe_category_sauce, R.plurals.recipe_group_sauce)
        RecipeCategory.Divers -> RecipeCategoryLook("🧺", R.string.recipe_category_divers, R.plurals.recipe_group_divers)
    }

/** The recipes of one category, already in the order of the book. */
data class RecipeGroup(val category: RecipeCategory, val rows: List<RecipeRow>)

/**
 * The rows grouped by category (règle 20 du brief), in the order of [RecipeCategory]: Apéritifs
 * first, Divers last; an empty category has no group. Inside a group the rows keep their order.
 */
fun groupByCategory(rows: List<RecipeRow>): List<RecipeGroup> {
    val byCategory = rows.groupBy { it.recipe.category }
    return RecipeCategory.entries.mapNotNull { category -> byCategory[category]?.let { RecipeGroup(category, it) } }
}

