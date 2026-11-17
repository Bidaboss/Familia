package fr.familia.app.feature.recipes.book

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.data.recipeOrder
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.share.photoAllowed
import fr.familia.app.feature.recipes.searchKey
import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * The book of the family recipes (PRD, récit 47), behind Feature.RecipeBook: the member picks the
 * recipes, the phone makes an A5 PDF (cover, contents by category, one recipe per page) and the
 * share sheet of Android opens. No server, so no cost.
 */

@Serializable
data object RecipeBookDestination

/** A recipe as it goes into the book: [photo] is the one allowed by [bookPhotoAllowed], or null. */
data class BookRecipe(val recipe: Recipe, val origin: String?, val photo: AlbumPhoto?)

/** A part of the contents: the first tag of its recipes, or null for « Autres recettes ». */
data class BookSection(val name: String?, val recipes: List<BookRecipe>)

/** What the printer needs; [coverPhoto] is one of the photos of the book, or none. */
data class BookContent(val title: String, val year: Int, val coverPhoto: AlbumPhoto?, val sections: List<BookSection>) {
    /** The photos drawn in the book, the cover included: the room to ask for before writing it. */
    val photoCount: Int get() = sections.sumOf { section -> section.recipes.count { it.photo != null } } + if (coverPhoto != null) 1 else 0
}

/**
 * The parts of the book: each recipe goes under its first tag (« dessert » and « Dessert » are one
 * part), parts by name, recipes by title, and those without a tag last, under « Autres recettes ».
 */
fun bookSections(recipes: List<BookRecipe>): List<BookSection> {
    val collator = java.text.Collator.getInstance(Locale.FRENCH)
    val (tagged, others) = recipes.partition { it.recipe.tags.isNotEmpty() }
    val parts = tagged.groupBy { searchKey(it.recipe.tags.first()) }
        .map { (_, list) ->
            val sorted = list.sortedWith(compareBy(recipeOrder) { it.recipe })
            BookSection(capitalized(sorted.first().recipe.tags.first()), sorted)
        }
        .sortedWith(compareBy(collator) { it.name.orEmpty() })
    val rest = if (others.isEmpty()) emptyList() else listOf(BookSection(null, others.sortedWith(compareBy(recipeOrder) { it.recipe })))
    return parts + rest
}

private fun capitalized(tag: String): String = tag.replaceFirstChar { it.titlecase(Locale.FRENCH) }

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
