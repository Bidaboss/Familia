package fr.familia.app.feature.recipes.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.data.recipeOrder
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeCategory
import fr.familia.app.core.share.photoAllowed
import kotlinx.serialization.Serializable

/*
 * The book of the family recipes (PRD, récit 47), behind Feature.RecipeBook: the member picks the
 * recipes, the phone makes an A5 PDF (cover, contents by category, one recipe per page) and the
 * share sheet of Android opens. No server, so no cost.
 */

@Serializable
data object RecipeBookDestination

/** A recipe as it goes into the book: [photo] is the one allowed by [bookPhotoAllowed], or null. */
data class BookRecipe(val recipe: Recipe, val origin: String?, val photo: AlbumPhoto?)

/** A chapter of the book: one category (DESIGN-170), its recipes by title. */
data class BookSection(val category: RecipeCategory, val recipes: List<BookRecipe>)

/** What the printer needs; [coverPhoto] is one of the photos of the book, or none. */
data class BookContent(val title: String, val year: Int, val coverPhoto: AlbumPhoto?, val sections: List<BookSection>) {
    /** The photos drawn in the book, the cover included: the room to ask for before writing it. */
    val photoCount: Int get() = sections.sumOf { section -> section.recipes.count { it.photo != null } } + if (coverPhoto != null) 1 else 0
}

/**
 * The chapters of the book, one per category in the order of the carnet (Apéritifs first, Divers
 * last; plan récit 114, question 2), the recipes by title inside; a category without a recipe has
 * no chapter. Free tags no longer make chapters.
 */
fun bookSections(recipes: List<BookRecipe>): List<BookSection> {
    val byCategory = recipes.groupBy { it.recipe.category }
    return RecipeCategory.entries.mapNotNull { category ->
        byCategory[category]?.let { list -> BookSection(category, list.sortedWith(compareBy(recipeOrder) { it.recipe })) }
    }
}

/**
 * Carte EXPERT-2 in the book, which leaves the app: the photo of a recipe that comes from a child
 * stays with the adults of that child. A photo taken by a child goes to everyone (« on est en
 * famille », 17/11), and so does the photo of any other recipe (the box « Un enfant est sur une
 * des photos » was taken away the same day). A member who left can no longer be checked: no photo.
 */
fun bookPhotoAllowed(photo: AlbumPhoto, recipe: Recipe, makerId: String, family: Family): Boolean {
    if (family.members.none { it.id == photo.authorId }) return false
    val origin = recipe.originMemberId ?: return true
    val from = family.members.firstOrNull { it.id == origin } ?: return false
    return from.profile == MemberProfile.Adult || photoAllowed(makerId, listOf(from.parentIds))
}

/** The first photo of [photos] the maker may put in the book, or null: then the Argile drawing. */
fun bookPhoto(photos: List<AlbumPhoto>, recipe: Recipe, makerId: String, family: Family): AlbumPhoto? =
    photos.firstOrNull { bookPhotoAllowed(it, recipe, makerId, family) }

/** « livre-de-recettes-2026.pdf »: a name every phone and every printing shop accepts. */
fun bookFileName(year: Int): String = "livre-de-recettes-$year.pdf"

/** The title of the cover, 60 characters at most so it fits on two lines. */
const val BOOK_TITLE_MAX = 60
