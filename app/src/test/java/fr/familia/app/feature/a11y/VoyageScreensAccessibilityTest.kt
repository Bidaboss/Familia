package fr.familia.app.feature.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.PhotoViewer
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.PackingLine
import fr.familia.app.core.model.WhoIsItPost
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.albums.AlbumEntry
import fr.familia.app.feature.albums.AlbumsScreen
import fr.familia.app.feature.albums.AlbumsUiState
import fr.familia.app.feature.capsules.CapsuleDetailScreen
import fr.familia.app.feature.capsules.CapsuleEditActions
import fr.familia.app.feature.capsules.CapsuleEditScreen
import fr.familia.app.feature.capsules.CapsulesScreen
import fr.familia.app.feature.capsules.previewCapsuleDetail
import fr.familia.app.feature.capsules.previewCapsuleEdit
import fr.familia.app.feature.capsules.previewCapsulesState
import fr.familia.app.feature.genealogy.GenealogyScreen
import fr.familia.app.feature.genealogy.GenealogyUiState
import fr.familia.app.feature.genealogy.poster.PosterChoice
import fr.familia.app.feature.genealogy.poster.PosterChoiceGroup
import fr.familia.app.feature.genealogy.poster.TreePosterActions
import fr.familia.app.feature.genealogy.poster.TreePosterScreen
import fr.familia.app.feature.genealogy.poster.TreePosterUiState
import fr.familia.app.feature.month.MonthSlideshowScreen
import fr.familia.app.feature.month.previewMonthSlideshow
import fr.familia.app.feature.recipes.RecipeDetailScreen
import fr.familia.app.feature.recipes.RecipesScreen
import fr.familia.app.feature.recipes.book.BookRecipe
import fr.familia.app.feature.recipes.book.RecipeBookActions
import fr.familia.app.feature.recipes.book.RecipeBookScreen
import fr.familia.app.feature.recipes.book.RecipeBookUiState
import fr.familia.app.feature.recipes.previewRecipeDetail
import fr.familia.app.feature.recipes.previewRecipes
import fr.familia.app.feature.recipes.previewRecipesState
import fr.familia.app.feature.trips.detail.TripDetailScreen
import fr.familia.app.feature.trips.detail.previewTripDetail
import fr.familia.app.feature.trips.list.TripsScreen
import fr.familia.app.feature.trips.list.previewTrips
import fr.familia.app.feature.trips.packing.BagOwner
import fr.familia.app.feature.trips.packing.BagUiState
import fr.familia.app.feature.trips.packing.PackingActions
import fr.familia.app.feature.trips.packing.PackingBags
import fr.familia.app.feature.trips.packing.PackingUiState
import fr.familia.app.feature.whoisit.WhoIsItActions
import fr.familia.app.feature.whoisit.WhoIsItListScreen
import fr.familia.app.feature.whoisit.WhoIsItListUiState
import fr.familia.app.feature.whoisit.WhoIsItPostScreen
import fr.familia.app.feature.whoisit.previewWhoIsIt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Police 200 % and TalkBack on the screens of the voyage thread (voyages et À emporter, albums,
 * visionneuse, diaporama, recettes et livre, capsule, arbre et affiche, Qui est-ce ?), with their
 * preview states and the common check of [checkAccessibility]. Fictional family only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class VoyageScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun member(id: String, name: String, born: LocalDate? = null, parents: List<String> = emptyList(), profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, born, "h1", profile, false, parents, null)

    @Test
    fun `the trips`() = compose.checkAccessibility { TripsScreen(previewTrips(), {}, {}, {}, {}) }

    @Test
    fun `a trip`() = compose.checkAccessibility {
        TripDetailScreen(previewTripDetail(), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos(), onOpenExpenses = {})
    }

    @Test
    fun `a trip, for who does not take part`() = compose.checkAccessibility {
        TripDetailScreen(previewTripDetail(isParticipant = false), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos())
    }

    @Test
    fun `À emporter`() = compose.checkAccessibility {
        PackingBags(
            PackingUiState(
                listOf(
                    BagUiState(BagOwner("a", "Camille", isMe = true), listOf(PackingLine("Maillot de bain", done = true), PackingLine("Crème solaire"))),
                    BagUiState(BagOwner("b", "Noé", isMe = false), listOf(PackingLine("Doudou"))),
                ),
            ),
            PackingActions(),
        )
    }

    @Test
    fun `the albums`() = compose.checkAccessibility {
        val albums = listOf(
            AlbumEntry(AlbumEntry.Kind.Event, "e1", "Pique-nique au lac", LocalDate.of(2026, 10, 4), photoCount = 12),
            AlbumEntry(AlbumEntry.Kind.Trip, "t1", "Corse", LocalDate.of(2026, 8, 2), photoCount = 40),
        )
        AlbumsScreen(AlbumsUiState(loading = false, albums = albums, months = albums.map { YearMonth.from(it.date) }), {}, {}, {})
    }

    @Test
    fun `an album`() = compose.checkAccessibility { AlbumScreen("Photos", previewAlbumPhotos(), PhotoActions(), {}) }

    @Test
    fun `the viewer of a photo`() = compose.checkAccessibility {
        val state = previewAlbumPhotos()
        PhotoViewer(state, state.photos.first().id, onRemove = {}, onClose = {})
    }

    @Test
    fun `the slideshow of the month`() = compose.checkAccessibility {
        MonthSlideshowScreen(YearMonth.of(2027, 8), previewMonthSlideshow(listOf(member("c", "Camille", LocalDate.of(1990, 8, 12)))), FilmState.Idle, FilmActions(), {})
    }

    @Test
    fun `the recipes`() = compose.checkAccessibility { RecipesScreen(previewRecipesState(), {}, {}, {}, {}) }

    @Test
    fun `a recipe`() = compose.checkAccessibility { RecipeDetailScreen(previewRecipeDetail(), {}, {}, {}) }

    @Test
    fun `the book of recipes`() = compose.checkAccessibility {
        val items = previewRecipes().mapIndexed { i, recipe ->
            BookRecipe(recipe, recipe.origin.ifBlank { null }, if (i == 0) AlbumPhoto("recipes/1", "a", "Camille", 1, null) else null)
        }
        RecipeBookScreen(
            RecipeBookUiState.Choosing(items, items.take(2).map { it.recipe.id }.toSet(), "Les recettes de la famille", 2026),
            RecipeBookActions({}, {}, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun `the capsules`() = compose.checkAccessibility { CapsulesScreen(previewCapsulesState(), {}, onOpen = {}, onAdd = {}) }

    @Test
    fun `a capsule`() = compose.checkAccessibility { CapsuleDetailScreen(previewCapsuleDetail(), {}, {}) }

    @Test
    fun `write a capsule`() = compose.checkAccessibility { CapsuleEditScreen(previewCapsuleEdit(), CapsuleEditActions(), {}) }

    @Test
    fun `the tree`() = compose.checkAccessibility {
        val jeanne = Kin("jeanne", "Jeanne", "Martin", living = false, born = "1921", died = "1999", note = "Elle faisait les meilleures crêpes.", householdId = "h1")
        val tree = GenealogyTree(
            listOf(member("camille", "Camille"), member("ines", "Inès"), member("noe", "Noé")),
            listOf(jeanne),
            listOf(KinLink.parent("jeanne", "camille"), KinLink.partners("camille", "ines"), KinLink.parent("camille", "noe")),
        )
        GenealogyScreen(GenealogyUiState.Ready(tree, mapOf("jeanne" to "Arrière-grand-mère"), "noe"), onBack = {})
    }

    @Test
    fun `the poster of the tree`() = compose.checkAccessibility {
        val tree = GenealogyTree(
            listOf(
                member("p1", "Camille", LocalDate.of(1980, 5, 2)),
                member("p2", "Noé", LocalDate.of(2016, 12, 20), listOf("p1"), MemberProfile.ChildWithoutApp),
            ),
            emptyList(),
            listOf(KinLink.parent("p1", "p2")),
        )
        TreePosterScreen(
            TreePosterUiState.Choosing(tree, listOf(PosterChoiceGroup("Les Martin", listOf(PosterChoice("p1", "Camille")))), "L'arbre de la famille", 2026),
            TreePosterActions({}, {}, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun `Qui est-ce ?, the list`() = compose.checkAccessibility {
        WhoIsItListScreen(WhoIsItListUiState.Ready(listOf(WhoIsItPost("w1", "a", "Camille", "Un mariage, au printemps", null))), {}, onAdd = {}, onBack = {})
    }

    @Test
    fun `Qui est-ce ?, a photo to guess`() = compose.checkAccessibility { WhoIsItPostScreen(previewWhoIsIt(revealed = false), WhoIsItActions(), {}, {}) }

    @Test
    fun `Qui est-ce ?, the answer`() = compose.checkAccessibility { WhoIsItPostScreen(previewWhoIsIt(revealed = true), WhoIsItActions(), {}, {}) }
}
