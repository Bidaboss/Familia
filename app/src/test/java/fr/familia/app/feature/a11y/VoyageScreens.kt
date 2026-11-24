package fr.familia.app.feature.a11y

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.album.PhotoViewer
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.PackingLine
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.RecipeComment
import fr.familia.app.core.model.WhoIsItPost
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
import fr.familia.app.feature.feed.AlbumFeedCard
import fr.familia.app.feature.feed.FeedAlbum
import fr.familia.app.feature.feed.FeedContext
import fr.familia.app.feature.feed.RecipeFeedCard
import fr.familia.app.feature.feed.WordFeedCard
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.genealogy.GenealogyScreen
import fr.familia.app.feature.genealogy.GenealogyUiState
import fr.familia.app.feature.genealogy.poster.PosterChoice
import fr.familia.app.feature.genealogy.poster.PosterChoiceGroup
import fr.familia.app.feature.genealogy.poster.TreePosterActions
import fr.familia.app.feature.genealogy.poster.TreePosterScreen
import fr.familia.app.feature.genealogy.poster.TreePosterUiState
import fr.familia.app.feature.month.MonthSlideshowScreen
import fr.familia.app.feature.month.previewMonthSlideshow
import fr.familia.app.feature.recipes.FakeRecipes
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
import fr.familia.app.feature.trips.edit.TripEditScreen
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.edit.DestinationSearch
import fr.familia.app.feature.trips.edit.TripDestinationScreen
import fr.familia.app.feature.trips.edit.TripDatesScreen
import fr.familia.app.feature.trips.edit.TripFormError
import fr.familia.app.feature.trips.edit.previewTripEditState
import fr.familia.app.feature.trips.list.TripsScreen
import fr.familia.app.feature.trips.map.TripMapScreen
import fr.familia.app.R
import fr.familia.app.core.model.EventComment
import fr.familia.app.feature.events.comments.EventCommentRow
import fr.familia.app.feature.events.comments.EventCommentsContent
import fr.familia.app.feature.events.comments.EventCommentsUiState
import fr.familia.app.feature.trips.word.StepWordActions
import fr.familia.app.feature.trips.word.StepWordScreen
import fr.familia.app.feature.trips.word.previewStepWord
import fr.familia.app.feature.trips.step.StepEditScreen
import fr.familia.app.feature.trips.place.PlaceEditScreen
import fr.familia.app.feature.trips.place.PlaceSearchResults
import fr.familia.app.feature.trips.place.PlaceSearchScreen
import fr.familia.app.feature.trips.place.previewPlaceSearchState
import fr.familia.app.feature.trips.place.PlaceFormError
import fr.familia.app.feature.trips.place.previewPlaceEditState
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.feature.trips.step.StepFormError
import fr.familia.app.feature.trips.step.previewStepEditState
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
import fr.familia.app.feature.yearago.MemoryAlbum
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * The screens of the voyage thread (voyages et À emporter, albums, visionneuse, diaporama, recettes
 * et livre, capsule, arbre et affiche, Qui est-ce ?) in their preview states, shared by the police
 * 200 % and TalkBack test and by the contrast test. Fictional family only.
 */
internal object VoyageScreens {

    private fun member(id: String, name: String, born: LocalDate? = null, parents: List<String> = emptyList(), profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, born, "h1", profile, false, parents, null)

    private fun screen(name: String, content: @Composable () -> Unit) = name to content

    val all: List<Pair<String, @Composable () -> Unit>> = listOf(
        screen("the trips") { TripsScreen(previewTrips(), {}, {}, {}, {}) },
        screen("a new trip") { TripEditScreen(previewTripEditState(), {}, {}, {}, {}) },
        screen("edit a trip, with errors") {
            TripEditScreen(previewTripEditState(isNew = false, errors = setOf(TripFormError.TitleMissing, TripFormError.TooManyParticipants)), {}, {}, {}, {})
        },
        screen("a new step") { StepEditScreen(previewStepEditState(isNew = true), {}, {}, {}, {}) },
        // Récit 103: the page of a place, to add, to change with errors, and to read for a follower.
        screen("a new accommodation") { PlaceEditScreen(previewPlaceEditState(kind = TripPlaceKind.Lodging, isNew = true), {}, {}, {}, {}) },
        screen("a restaurant, with errors") {
            PlaceEditScreen(previewPlaceEditState(errors = setOf(PlaceFormError.NameMissing, PlaceFormError.CostInvalid)), {}, {}, {}, {})
        },
        screen("a spot, for a follower") { PlaceEditScreen(previewPlaceEditState(kind = TripPlaceKind.Spot, canEdit = false), {}, {}, {}, {}) },
        // Règle 20: the search of each kind, with places found, a typed name and no answer.
        screen("a restaurant, with « Choisir un autre »") { PlaceEditScreen(previewPlaceEditState(), {}, {}, {}, {}, onSearch = {}) },
        screen("the search of a restaurant") { PlaceSearchScreen(previewPlaceSearchState(), "Ajaccio", {}, {}, {}, {}, {}) },
        screen("the search of a spot, no answer") {
            PlaceSearchScreen(previewPlaceSearchState(kind = TripPlaceKind.Spot, text = "plage", results = PlaceSearchResults.Failed(fr.familia.app.core.place.SearchFailure.Busy)), "Ajaccio", {}, {}, {}, {}, {})
        },
        screen("edit a step, with errors") {
            StepEditScreen(previewStepEditState(errors = setOf(StepFormError.PlaceMissing, StepFormError.ArriveBeforeDepart)), {}, {}, {}, {})
        },
        screen("a trip") { TripDetailScreen(previewTripDetail(), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos(), onOpenExpenses = {}) },
        // For who does not take part: no « Bien arrivé » button and no « Ajouter une étape » button over it, only the link.
        screen("a trip with the link to its map") {
            TripDetailScreen(previewTripDetail(isParticipant = false, withPositions = true), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos(), onOpenMap = {})
        },
        screen("the map of a trip") { TripMapScreen(previewTripDetail(withPositions = true), {}, {}, {}) },
        screen("the map of a trip, for who does not take part") { TripMapScreen(previewTripDetail(isParticipant = false, withPositions = true), {}, {}, {}) },
        screen("the word of a step, to write") { StepWordScreen(previewStepWord(canWrite = true, withWord = false), StepWordActions()) },
        screen("the word of a step, to change") { StepWordScreen(previewStepWord(canWrite = true, withWord = true), StepWordActions()) },
        screen("the word of a step, to read") { StepWordScreen(previewStepWord(canWrite = false, withWord = true, isParticipant = false), StepWordActions()) },
        screen("the word of a step, to read, with comments") {
            StepWordScreen(previewStepWord(canWrite = false, withWord = true, isParticipant = false), StepWordActions()) {
                EventCommentsContent(
                    state = EventCommentsUiState(
                        rows = listOf(
                            EventCommentRow(EventComment("c1", "Quelle belle arrivée, @Camille !", "m1", "Inès", null), canEdit = false, canRemove = false),
                        ),
                        isLoading = false,
                    ),
                    onType = { _, _ -> },
                    onSend = {},
                    onShowMore = {},
                    onEdit = { _, _, _ -> true },
                    onRemove = {},
                    onMute = {},
                    empty = R.string.word_comments_empty,
                )
            }
        },
        screen("the word of a step, with no word yet, to read") { StepWordScreen(previewStepWord(canWrite = false, withWord = false, isParticipant = false), StepWordActions()) },
        screen("the map of a trip, with no step placed") { TripMapScreen(previewTripDetail(), {}, {}, {}) },
        // Fiche F1: the path with its header, the big photo card, the dates, who goes, the AI pill and the tools.
        screen("a trip, the path of the fiche F1") {
            TripDetailScreen(
                previewTripDetail(), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos(), onOpenExpenses = {},
                illustrated = true,
                cover = { success -> fr.familia.app.feature.trips.detail.TripCoverCarousel(success.trip, success.steps, null, today = success.today) },
                onOpenEnrich = {}, onOpenPacking = {}, packingCount = 2, onOpenDocuments = {}, onShare = {},
            )
        },
        screen("a trip, for who does not take part") { TripDetailScreen(previewTripDetail(isParticipant = false), {}, {}, {}, {}, {}, {}, {}, photos = previewAlbumPhotos()) },
        // Fiche F4: the calendar of the dates, a trip chosen; the destination without the globe (no token).
        screen("the dates of a trip") {
            TripDatesScreen(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 9), { _, _ -> }, {})
        },
        screen("where are you going") {
            TripDestinationScreen("Ajac", DestinationSearch.Idle, null, {}, {}, {}, {}, {}, {})
        },
        // Fiche F5: the page of a destination, for who goes (departure, « + Ajouter »).
        screen("the page of a destination") {
            fr.familia.app.feature.trips.detail.DestinationPage(
                container = null,
                success = previewTripDetail(),
                stayId = "s2",
                actions = fr.familia.app.feature.trips.detail.TimelineActions(
                    onNights = { _, _ -> fr.familia.app.feature.trips.detail.NightsChange.Blocked(fr.familia.app.feature.trips.detail.NightsBlock.OneNight) },
                    onAddPlace = { _, _ -> },
                ),
                links = fr.familia.app.feature.trips.detail.DestinationLinks(onEdit = {}, onAllAbout = {}, onOpenPhrases = null, onOpenMoney = null),
                onSelect = {},
                onBack = {},
                inSheet = false,
            )
        },
        // Fiche F6: « Enrichir avec une IA » in three steps, and the sheet « Monnaie du voyage ».
        screen("enrich with an AI") {
            fr.familia.app.feature.trips.enrich.TripEnrichScreen(
                false, true, previewTripDetail().steps, "Prompt", fr.familia.app.feature.trips.enrich.EnrichUiState(), {}, {}, {}, {}, {}, {},
            )
        }
        screen("enrich, an answer refused") {
            fr.familia.app.feature.trips.enrich.TripEnrichScreen(
                false, true, previewTripDetail().steps, "Prompt",
                fr.familia.app.feature.trips.enrich.EnrichUiState(answer = "bonjour", reading = fr.familia.app.feature.trips.enrich.EnrichReading.Refused.NotJson),
                {}, {}, {}, {}, {}, {},
                startStep = fr.familia.app.feature.trips.enrich.EnrichStep.Paste,
            )
        }
        screen("enrich, the check") {
            val reading = fr.familia.app.feature.trips.enrich.EnrichReading.Read(
                listOf(
                    fr.familia.app.feature.trips.enrich.ProposedDestination(
                        "Bastia", null, LocalDate.of(2027, 8, 5), LocalDate.of(2027, 8, 7),
                        listOf(fr.familia.app.feature.trips.enrich.ProposedPlace(fr.familia.app.core.model.TripPlaceKind.Restaurant, "Chez Inès", "Le port", null, null, null)),
                    ),
                ),
                listOf(fr.familia.app.feature.trips.enrich.Skipped("Musée", fr.familia.app.feature.trips.enrich.SkipReason.Link)),
            )
            fr.familia.app.feature.trips.enrich.TripEnrichScreen(
                false, true, previewTripDetail().steps, "Prompt",
                fr.familia.app.feature.trips.enrich.EnrichUiState(answer = "{}", reading = reading, ticked = fr.familia.app.feature.trips.enrich.allKeys(reading)),
                {}, {}, {}, {}, {}, {},
            )
        }
        screen("the currency of a trip") {
            fr.familia.app.feature.trips.money.TripMoneyScreen(
                loading = false,
                currency = fr.familia.app.core.money.Currencies.of("MAD"),
                suggestions = emptyList(),
                rates = fr.familia.app.feature.trips.money.RatesState.Ready(fr.familia.app.core.money.RatesTable(mapOf("MAD" to 10.9), LocalDate.of(2026, 11, 23))),
                saveFailed = false,
                onChange = {},
                onRetry = {},
                onBack = {},
            )
        }
        screen("the currency of a trip, for who does not manage it") {
            fr.familia.app.feature.trips.money.TripMoneyScreen(
                loading = false,
                currency = fr.familia.app.core.money.Currencies.of("MAD"),
                suggestions = emptyList(),
                rates = fr.familia.app.feature.trips.money.RatesState.Unavailable,
                saveFailed = false,
                onChange = null,
                onRetry = {},
                onBack = {},
            )
        }
        // Fiche F3: the bags are always on the light blue page of « Valise ».
        screen("À emporter") { androidx.compose.foundation.layout.Box(Modifier.background(TripPathColors.Sky)) {
            PackingBags(
                PackingUiState(
                    listOf(
                        BagUiState(BagOwner("a", "Camille", isMe = true), listOf(PackingLine("Maillot de bain", done = true), PackingLine("Crème solaire"))),
                        BagUiState(BagOwner("b", "Noé", isMe = false), listOf(PackingLine("Doudou"))),
                    ),
                ),
                PackingActions(),
            )
        } },
        screen("the albums") {
            val albums = listOf(
                AlbumEntry(AlbumEntry.Kind.Event, "e1", "Pique-nique au lac", LocalDate.of(2026, 10, 4), photoCount = 12),
                AlbumEntry(AlbumEntry.Kind.Trip, "t1", "Corse", LocalDate.of(2026, 8, 2), photoCount = 40),
            )
            AlbumsScreen(AlbumsUiState(loading = false, albums = albums, months = albums.map { YearMonth.from(it.date) }), {}, {}, {})
        },
        screen("an album") { AlbumScreen("Photos", previewAlbumPhotos(), PhotoActions(), {}) },
        screen("the viewer of a photo") {
            val state = previewAlbumPhotos()
            PhotoViewer(state, state.photos.first().id, onRemove = {}, onClose = {})
        },
        screen("the slideshow of the month") { MonthSlideshowScreen(YearMonth.of(2027, 8), previewMonthSlideshow(listOf(member("c", "Camille", LocalDate.of(1990, 8, 12)))), FilmState.Idle, FilmActions(), {}) },
        screen("the recipes") { RecipesScreen(previewRecipesState(), {}, {}, {}, {}) },
        screen("a recipe") { RecipeDetailScreen(previewRecipeDetail(), {}, {}, {}) },
        screen("the book of recipes") {
            val items = previewRecipes().mapIndexed { i, recipe ->
                BookRecipe(recipe, recipe.origin.ifBlank { null }, if (i == 0) AlbumPhoto("recipes/1", "a", "Camille", 1, null) else null)
            }
            RecipeBookScreen(
                RecipeBookUiState.Choosing(items, items.take(2).map { it.recipe.id }.toSet(), "Les recettes de la famille", 2026),
                RecipeBookActions({}, {}, {}, {}, {}),
                onBack = {},
            )
        },
        screen("the capsules") { CapsulesScreen(previewCapsulesState(), {}, onOpen = {}, onAdd = {}) },
        screen("a capsule") { CapsuleDetailScreen(previewCapsuleDetail(), {}, {}) },
        screen("write a capsule") { CapsuleEditScreen(previewCapsuleEdit(), CapsuleEditActions(), {}) },
        screen("the tree") {
            val jeanne = Kin("jeanne", "Jeanne", "Martin", living = false, born = "1921", died = "1999", note = "Elle faisait les meilleures crêpes.", householdId = "h1")
            val tree = GenealogyTree(
                listOf(member("camille", "Camille"), member("ines", "Inès"), member("noe", "Noé")),
                listOf(jeanne),
                listOf(KinLink.parent("jeanne", "camille"), KinLink.partners("camille", "ines"), KinLink.parent("camille", "noe")),
            )
            GenealogyScreen(GenealogyUiState.Ready(tree, mapOf("jeanne" to "Arrière-grand-mère"), "noe"), onBack = {})
        },
        screen("the tree of life") {
            val jeanne = Kin("jeanne", "Jeanne", "Martin", living = false, born = "1921", died = "1999", householdId = "h1")
            val tree = GenealogyTree(
                listOf(member("camille", "Camille"), member("ines", "Inès"), member("noe", "Noé"), member("sam", "Sam"), member("lou", "Lou")),
                listOf(jeanne),
                listOf(
                    KinLink.parent("jeanne", "camille"), KinLink.partners("camille", "ines"),
                    KinLink.parent("camille", "noe"), KinLink.parent("ines", "noe"), KinLink.partners("noe", "sam"), KinLink.parent("noe", "lou"),
                ),
            )
            GenealogyScreen(GenealogyUiState.Ready(tree, emptyMap(), "noe", isOwner = true), onBack = {}, treeOfLife = true)
        },
        screen("the tree of life, without relatives") {
            val tree = GenealogyTree(
                listOf(member("camille", "Camille"), member("ines", "Inès"), member("noe", "Noé")),
                emptyList(),
                listOf(KinLink.partners("camille", "ines"), KinLink.parent("camille", "noe")),
            )
            GenealogyScreen(GenealogyUiState.Ready(tree, emptyMap(), "noe"), onBack = {}, treeOfLife = true)
        },
        screen("the tree of life, four generations") {
            val tree = GenealogyTree(
                listOf(member("a", "Adèle"), member("b", "Basile"), member("c", "Céleste"), member("d", "Daphné")),
                emptyList(),
                listOf(KinLink.parent("a", "b"), KinLink.parent("b", "c"), KinLink.parent("c", "d")),
            )
            GenealogyScreen(GenealogyUiState.Ready(tree, emptyMap(), "d"), onBack = {}, treeOfLife = true)
        },
        screen("the poster of the tree") {
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
        },
        screen("the fil, photos and a recipe") {
            val camille = member("camille", "Camille")
            val ines = member("ines", "Inès")
            val context = FeedContext(camille, Family(listOf(Household("h1", "Les Martin")), listOf(camille, ines)), LocalDate.of(2026, 11, 18), readOnly = false)
            val album = FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Trip, "t1", "Une semaine à la mer"), "trips/t1", LocalDate.of(2026, 11, 10))
            val photos = (1..5).map { AlbumPhoto("trips/t1", if (it % 2 == 0) "ines" else "camille", if (it % 2 == 0) "Inès" else "Camille", it, Instant.parse("2026-11-18T0${it}:00:00Z")) }
            val recipes = FakeRecipes().apply { comments.value = listOf(RecipeComment("c1", "Je la fais dimanche !", "camille", "Camille", null)) }
            val recipe = Recipe("r1", "Tarte aux pommes", "Mamie Jeanne", null, 6, "", "", emptyList(), "ines", "Inès", Instant.parse("2026-11-18T09:00:00Z"))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlbumFeedCard(album, LocalDate.of(2026, 11, 18), photos.reversed()).Content(context)
                RecipeFeedCard(recipe, recipes, null).Content(context)
                WordFeedCard("t1", "Une semaine à la mer", "Ajaccio", TripWord("s1", "Arrivés à Ajaccio ! Premier café sur le port.", listOf("a", "b"), "ines", "Inès", setOf("camille", "ines"), Instant.parse("2026-11-18T09:30:00Z"))).Content(context)
            }
        },
        screen("Qui est-ce ?, the list") { WhoIsItListScreen(WhoIsItListUiState.Ready(listOf(WhoIsItPost("w1", "a", "Camille", "Un mariage, au printemps", null))), {}, onAdd = {}, onBack = {}) },
        screen("Qui est-ce ?, a photo to guess") { WhoIsItPostScreen(previewWhoIsIt(revealed = false), WhoIsItActions(), {}, {}) },
        screen("Qui est-ce ?, the answer") { WhoIsItPostScreen(previewWhoIsIt(revealed = true), WhoIsItActions(), {}, {}) },
    )

    fun screen(name: String): @Composable () -> Unit = all.first { it.first == name }.second
}
