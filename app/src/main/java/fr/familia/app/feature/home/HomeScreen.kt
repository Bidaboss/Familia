package fr.familia.app.feature.home

import fr.familia.app.core.data.familyToday
import fr.familia.app.feature.firststeps.rememberFirstSteps
import fr.familia.app.feature.gooddeals.GoodDealHomeCard
import fr.familia.app.feature.helprequests.todo.rememberHelpRequestTodos
import fr.familia.app.feature.helprequests.todo.helpActionErrorText
import fr.familia.app.feature.helprequests.todo.HelpRequestTodoCard
import fr.familia.app.feature.tree.challenges.rememberHasChallenge
import fr.familia.app.feature.tree.challenges.ChallengeHomeCard
import fr.familia.app.feature.trips.now.rememberNowTravelling
import fr.familia.app.feature.trips.now.NowTravellingCard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import fr.familia.app.feature.feed.FeedList
import fr.familia.app.feature.feed.FeedNavigation
import fr.familia.app.feature.feed.HomeFeedSwitch
import fr.familia.app.feature.feed.LocalFeedNavigation
import fr.familia.app.feature.feed.rememberFeedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.familia.app.feature.discover.DiscoverModule
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.goodnews.home.GoodNewsHomeCard
import fr.familia.app.feature.yearago.MemoryAlbum
import fr.familia.app.feature.yearinreview.YearInReviewHomeChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth


/** Where each row of the home screen leads. Null: the row is not a link (its module is off). */
class HomeActions(
    val onOpenEvent: (String) -> Unit = {},
    val onOpenPlanning: () -> Unit = {},
    val onOpenHelpRequest: (String) -> Unit = {},
    val onOpenWishes: ((memberId: String) -> Unit)? = null,
    val onOpenList: (String) -> Unit = {},
    val onOpenTrip: (String) -> Unit = {},
    /** A photo of « Il y a N ans »: its album, opened on it (récit 36). */
    val onOpenAlbumPhoto: (MemoryAlbum, String) -> Unit = { _, _ -> },
    val onNewTrip: () -> Unit = {},
    /** « Tous les voyages »: set while the trips module is on; the card then always shows. */
    val onOpenTrips: (() -> Unit)? = null,
    val onOpenPoll: (String) -> Unit = {},
    /** « Tous les sondages » and « Nouveau sondage » of the block Sondages (BUG-1). */
    val onOpenPolls: () -> Unit = {},
    val onNewPoll: () -> Unit = {},
)

/** Connects the Accueil tab to its ViewModel; each module feeds its card only while it shows. */
@Composable
fun HomeRoute(
    container: AppContainer?,
    currentMember: Member,
    actions: HomeActions,
    onOpenGoodNewsFeed: () -> Unit = {},
    onOpenGoodNews: (String) -> Unit = {},
    onPublishGoodNews: () -> Unit = {},
    onOpenWeeklyQuestion: () -> Unit = {},
    onOpenMonth: (YearMonth) -> Unit = {},
    onJoinMe: () -> Unit = {},
    /** The guide of the challenge of the foyer (récit 35 bis). */
    onPrepareChallenge: (YearMonth) -> Unit = {},
    /** A Bonne nouvelle linked to the challenge of a foyer (récit 35 bis). */
    onJoinChallenge: () -> Unit = {},
    /** Opens the module of « À découvrir » (AVIS-19). */
    onOpenDiscover: (DiscoverModule) -> Unit = {},
    /** Opens the recipe of the chip « Nouvelle recette ». */
    onOpenRecipe: (String) -> Unit = {},
    /** « Tout Familia » of the R1 bis Accueil, and the sum of the badges of its drawer. */
    onOpenAll: () -> Unit = {},
    allBadge: Int = 0,
    /** « Défis » (R1 bis), opened from the card of the challenge of the month. */
    onOpenChallenges: () -> Unit = {},
    /** A « Nouveau bon plan » chip opens its card (récit 45). */
    onOpenGoodDeal: (String) -> Unit = {},
    /** « Notre année en famille », from 26/12 to 10/01: opens the year. */
    onOpenYear: (Int) -> Unit = {},
    /** My profile, where « Goûts et passions » is filled (« Mes premiers pas », récit 61). */
    onOpenMyProfile: () -> Unit = {},
    /** A member's page, from a card of the fil (« Lui souhaiter », récit 67). */
    onOpenMember: (String) -> Unit = {},
) {
    val flags = LocalFeatureFlags.current
    val giftsOn = flags.isOn(Feature.Gifts)
    val helpOn = flags.isOn(Feature.HelpRequests)
    val tripsOn = flags.isOn(Feature.Trips)
    val pollsOn = flags.isOn(Feature.Polls)
    val singleList = flags.isOn(Feature.SingleList)
    val viewModel: HomeViewModel = viewModel(
        key = "home-$giftsOn-$helpOn-$tripsOn-$pollsOn-$singleList",
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    members = container?.memberRepository,
                    events = container?.eventRepository,
                    currentMemberId = currentMember.id,
                    rsvps = container?.rsvpRepository,
                    helpRequests = if (helpOn) container?.helpRequestRepository else null,
                    gifts = if (giftsOn) container?.giftRepository else null,
                    trips = if (tripsOn) container?.tripRepository else null,
                    polls = if (pollsOn) container?.pollRepository else null,
                    singleList = singleList,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = familyToday()
    val answerFailed by viewModel.answerFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(answerFailed) {
        if (answerFailed) {
            Toast.makeText(context, R.string.home_r1_answer_failed, Toast.LENGTH_LONG).show()
            viewModel.answerFailureShown()
        }
    }
    val hasChallenge = rememberHasChallenge(container, currentMember)
    // The coups de main answer right on their card (help-requests thread, #210).
    val help = if (helpOn) rememberHelpRequestTodos(container, currentMember) else null
    val helpError = help?.let { helpActionErrorText(it.error) }
    LaunchedEffect(helpError) {
        if (helpError != null && help != null) {
            Toast.makeText(context, helpError, Toast.LENGTH_LONG).show()
            help.onDismissError()
        }
    }
    val shownState = if (help != null) {
        (state as? HomeUiState.Ready)?.let { ready -> ready.copy(todos = ready.todos.filterNot { it is HomeTodo.Help }) } ?: state
    } else {
        state
    }
    // Le fil d'actualité (récit 67): off, the Accueil does not change, even for the admin principal.
    val feedOn = flags.isOnForFamily(Feature.NewsFeed)
    val feed = if (feedOn) rememberFeedState(container, currentMember) else null
    // The app always opens on « Aujourd'hui ».
    var onFeed by rememberSaveable { mutableStateOf(false) }
    val feedNavigation = remember(actions) {
        FeedNavigation(
            onOpenEvent = actions.onOpenEvent,
            onOpenMember = onOpenMember,
            onOpenGoodNews = onOpenGoodNews,
            onOpenHelpRequest = actions.onOpenHelpRequest,
            onOpenTrip = actions.onOpenTrip,
            onOpenRecipe = onOpenRecipe,
            onOpenAlbumPhoto = actions.onOpenAlbumPhoto,
            onOpenWeeklyQuestion = onOpenWeeklyQuestion,
        )
    }
    HomeR1bisScreen(
        switcher = feed?.let { f -> { HomeFeedSwitch(onFeed, f.unseen, onChoose = { onFeed = it }) } },
        feed = feed?.takeIf { onFeed }?.let { f ->
            { CompositionLocalProvider(LocalFeedNavigation provides feedNavigation) { FeedList(f) } }
        },
        state = shownState,
        firstSteps = rememberFirstSteps(container, currentMember, onOpenTastes = onOpenMyProfile, onOpenGoodNews = onOpenGoodNewsFeed),
        challenge = if (hasChallenge) {
            { modifier, wide -> ChallengeHomeCard(container, currentMember, wide, onOpen = onOpenChallenges, onAddPhoto = onJoinChallenge, modifier = modifier) }
        } else {
            null
        },
        yearInReview = if (flags.isOn(Feature.YearInReview)) {
            { YearInReviewHomeChip(today, onOpenYear) }
        } else {
            null
        },
        goodDeal = if (flags.isOn(Feature.GoodDeals)) {
            { GoodDealHomeCard(container, currentMember.id, onOpenGoodDeal) }
        } else {
            null
        },
        helpTodos = help?.todos?.takeIf { it.isNotEmpty() }?.let { todos ->
            {
                todos.forEach { todo ->
                    HelpRequestTodoCard(
                        todo,
                        onTake = { help.onTake(todo.request) },
                        onDecline = { help.onDecline(todo.request) },
                        onOpen = { actions.onOpenHelpRequest(todo.request.id) },
                    )
                }
            }
        },
        actions = actions,
        r1 = HomeR1bisActions(onAnswer = viewModel::answer, onOpenAll = onOpenAll),
        goodNews = if (flags.isOn(Feature.GoodNews)) {
            { modifier, compact -> GoodNewsHomeCard(container, currentMember, onOpenGoodNewsFeed, onOpenGoodNews, onPublishGoodNews, modifier, compact) }
        } else {
            null
        },
        nowTravelling = rememberNowTravelling(container, currentMember)?.let { now ->
            { NowTravellingCard(now, onOpen = { actions.onOpenTrip(now.tripId) }) }
        },
        allBadge = allBadge,
    )
}

/** Fictional home screen for previews and screenshots. */
internal fun previewHome(): HomeUiState.Ready {
    val today = LocalDate.of(2026, 11, 21)
    fun member(id: String, name: String, birth: LocalDate?, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, birth, "h1", profile, false, emptyList(), null)
    val camille = member("a", "Camille", LocalDate.of(1990, 11, 28))
    val noe = member("b", "Noé", LocalDate.of(2016, 12, 3), MemberProfile.ChildWithApp)
    fun event(id: String, title: String, date: LocalDate, time: LocalTime?, place: String? = null) =
        Event(id, title, date, time, place, null, EventTarget.ForFamily, "a", "Camille")
    val dinner = event("e1", "Repas chez Inès", today.plusDays(1), LocalTime.of(12, 30), "Lyon")
    val trip = Trip("t1", "Vacances à la mer", today.minusDays(2), today.plusDays(4), listOf("a", "b"), emptyList(), "a", "Camille")
    return HomeUiState.Ready(
        today = today,
        todos = listOf(
            HomeTodo.Answer(dinner),
            HomeTodo.Help(
                HelpRequest(
                    "h1", HelpKind.Ride, "Emmener Noé au judo", today.plusDays(2), LocalTime.of(17, 0), null, null, null,
                    "a", "Camille", "b", listOf("me"),
                ),
            ),
        ),
        week = listOf(dinner, event("e2", "Goûter du mercredi", today.plusDays(4), null)),
        birthdays = listOf(Birthday(camille, LocalDate.of(2026, 11, 28), 36), Birthday(noe, LocalDate.of(2026, 12, 3), 10)),
        occasion = HomeOccasion(
            GiftList("l1", GiftListKind.Own, "Noël de Noé", GiftOccasion.Christmas, LocalDate.of(2026, 12, 25), "b", emptyList(), "a", "Camille"),
            "Noé", 34, 2,
        ),
        trips = listOf(HomeTrip(trip, "Camille et Noé", ongoing = true, TripArrival(Instant.parse("2026-11-19T15:10:00Z"), "a", "Camille"))),
        canCreateTrip = true,
    )
}

@FamiliaPreviews
@Composable
private fun HomeScreenPreview() {
    FamiliaTheme { Surface { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) } }
}
