package fr.familia.app.feature.home

import fr.familia.app.core.data.familyToday
import fr.familia.app.feature.firststeps.rememberFirstSteps
import fr.familia.app.feature.gooddeals.GoodDealHomeCard
import fr.familia.app.feature.helprequests.todo.rememberHelpRequestTodos
import fr.familia.app.feature.helprequests.todo.helpActionErrorText
import fr.familia.app.feature.helprequests.todo.HelpRequestTodoCard
import fr.familia.app.feature.joinme.JoinMeHomeContent
import fr.familia.app.feature.joinme.rememberJoinMeHome
import fr.familia.app.feature.tree.challenges.rememberHasChallenge
import fr.familia.app.feature.tree.challenges.ChallengeHomeCard
import fr.familia.app.feature.trips.now.rememberNowTravelling
import fr.familia.app.feature.trips.now.NowTravellingCard
import fr.familia.app.feature.trips.now.TripDepartureCard
import fr.familia.app.feature.trips.now.rememberTripDeparture
import androidx.compose.material.icons.outlined.Event
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import fr.familia.app.feature.feed.FeedList
import fr.familia.app.feature.feed.FeedOpenRequest
import fr.familia.app.feature.feed.FeedPublishBar
import fr.familia.app.feature.feed.FeedNavigation
import fr.familia.app.feature.feed.HomeFeedSwitch
import fr.familia.app.feature.feed.LocalFeedGlass
import fr.familia.app.feature.feed.LocalFeedNavigation
import fr.familia.app.feature.feed.rememberFeedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.isToConfirm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.PhotoScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
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
    /** « Valise » of the card of the day before a trip (récit 109). */
    val onOpenPacking: (String) -> Unit = {},
    val onOpenWord: (String, String) -> Unit = { _, _ -> },
    /** A photo of « Il y a N ans »: its album, opened on it (récit 36). */
    val onOpenAlbumPhoto: (MemoryAlbum, String) -> Unit = { _, _ -> },
    val onNewTrip: () -> Unit = {},
    /** « Tous les voyages »: set while the trips module is on; the card then always shows. */
    val onOpenTrips: (() -> Unit)? = null,
    val onOpenPoll: (String) -> Unit = {},
    /** « Tous les sondages » and « Nouveau sondage » of the block Sondages (BUG-1). */
    val onOpenPolls: () -> Unit = {},
    val onNewPoll: () -> Unit = {},
    /** A surprise of mine on its day, « C'est offert ? » (récit 113 with Cadeaux): set while surprises are on. */
    val onOpenSurprise: ((String) -> Unit)? = null,
    /** The Cadeaux tab, for several surprises to confirm at once. */
    val onOpenGifts: () -> Unit = {},
)

/** Connects the Accueil tab to its ViewModel; each module feeds its card only while it shows. */
@OptIn(ExperimentalCoroutinesApi::class)
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
    /** An opened capsule, from its card of the fil (récit 71). */
    onOpenCapsule: (String) -> Unit = {},
    /** « La semaine de la famille », from its card of the fil (récit 71). */
    onOpenRecap: () -> Unit = {},
    /** The Accueil on the photo of the theme (récit 113): its header and its scroll; null: as before. */
    photo: PhotoHome? = null,
) {
    val flags = LocalFeatureFlags.current
    val helpOn = flags.isOn(Feature.HelpRequests)
    val tripsOn = flags.isOn(Feature.Trips)
    val pollsOn = flags.isOn(Feature.Polls)
    val viewModel: HomeViewModel = viewModel(
        key = "home-$helpOn-$tripsOn-$pollsOn",
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    members = container?.memberRepository,
                    events = container?.eventRepository,
                    currentMemberId = currentMember.id,
                    rsvps = container?.rsvpRepository,
                    helpRequests = if (helpOn) container?.helpRequestRepository else null,
                    trips = if (tripsOn) container?.tripRepository else null,
                    polls = if (pollsOn) container?.pollRepository else null,
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
    // « Rejoignez-moi » (récit 33): lost with the old Accueil (#397), back at the top (l'admin, 20/11).
    val joinMe = if (flags.isOn(Feature.Places)) rememberJoinMeHome(container, currentMember)?.takeIf { it.hasShares } else null
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
    // « Le fil » of Tout Familia opens the Accueil on the fil (récit 71).
    val feedAsked by FeedOpenRequest.asked.collectAsState()
    LaunchedEffect(feedAsked, feed != null) {
        if (feedAsked && feed != null) {
            onFeed = true
            FeedOpenRequest.consume()
        }
    }
    // A « rejoins-moi » opens the Accueil on « Aujourd'hui », where its tile is (l'admin, 20/11).
    val todayAsked by TodayOpenRequest.asked.collectAsState()
    LaunchedEffect(todayAsked) {
        if (todayAsked) {
            onFeed = false
            TodayOpenRequest.consume()
        }
    }
    val feedNavigation = remember(actions) {
        FeedNavigation(
            onOpenEvent = actions.onOpenEvent,
            onOpenMember = onOpenMember,
            onOpenGoodNews = onOpenGoodNews,
            onOpenHelpRequest = actions.onOpenHelpRequest,
            onOpenTrip = actions.onOpenTrip,
            onOpenWord = actions.onOpenWord,
            onOpenRecipe = onOpenRecipe,
            onOpenAlbumPhoto = actions.onOpenAlbumPhoto,
            onOpenWeeklyQuestion = onOpenWeeklyQuestion,
            onOpenCapsule = onOpenCapsule,
            onOpenRecap = onOpenRecap,
            onOpenYear = onOpenYear,
        )
    }
    val canPublish = flags.isOn(Feature.GoodNews)
    // On the photo (règles 2, 3 et 13): the greeting, the free top of the photo, then the switch,
    // all scrolling with the day or the fil.
    val photoTop: (@Composable () -> Unit)? = photo?.let { p ->
        {
            Column {
                p.header()
                Spacer(Modifier.height(PHOTO_FREE_TOP))
                feed?.let { f ->
                    PhotoFeedSwitch(
                        onFeed = onFeed,
                        unseen = f.unseen,
                        accent = Color(p.theme.accent ?: 0xFF000000),
                        dark = Color(p.theme.dark ?: 0xFF000000),
                        onChoose = {
                            onFeed = it
                            p.scroll.reset()
                        },
                    )
                }
            }
        }
    }
    Box(if (photo != null) Modifier.nestedScroll(photo.scroll.connection) else Modifier) {
    // My surprises, for « À répondre » on the photo: the one of the day waits for « C'est offert ».
    // « Je ne participe pas » is final: a surprise I left never asks me to confirm it (only the ones of
    // the day are checked, one small listener each).
    val mySurprisesFlow = remember(container, currentMember.id, actions.onOpenSurprise != null) {
        val repository = container?.surpriseRepository?.takeIf { actions.onOpenSurprise != null }
        repository?.observeMine(currentMember.id)?.let { mineFlow -> combine(mineFlow, currentDayFlow()) { mine, today -> mine to today } }?.flatMapLatest { (mine, today) ->
            val due = mine.filter { it.isToConfirm(today) }
            if (due.isEmpty()) {
                flowOf(mine)
            } else {
                combine(
                    due.map { surprise ->
                        repository.observeMemberRows(surprise.id)
                            .map { rows -> surprise.id.takeIf { rows.any { it.memberId == currentMember.id && it.declined } } }
                            // A refused read of one surprise (left, rules being published) leaves it shown.
                            .catch { emit(null) }
                    },
                ) { left -> mine.filter { it.id !in left.filterNotNull() } }
            }
        }?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }
    val mySurprises by mySurprisesFlow.collectAsState(initial = emptyList())
    // « Surprise de X : n idées à noter » (audit Cadeaux, point 8): before the day, the proposals I
    // have not rated yet, with the first name of the beneficiary.
    val toRateFlow = remember(container, currentMember.id, actions.onOpenSurprise != null) {
        val repository = container?.surpriseRepository?.takeIf { actions.onOpenSurprise != null }
        val family = container?.memberRepository?.observeFamily()
        if (repository == null || family == null) {
            flowOf(emptyList())
        } else {
            // The day moves on at midnight even with the Accueil open: a surprise of the day leaves « à noter ».
            currentDayFlow().flatMapLatest { today ->
                combine(
                    fr.familia.app.feature.gifts.surprise.observeSurprisesToRate(repository, container?.giftRepository, currentMember.id, today),
                    family,
                ) { rates, fam ->
                    rates.map { rate -> PhotoSurpriseToRate(rate.surpriseId, fam.members.firstOrNull { it.id == rate.beneficiaryId }?.firstName.orEmpty(), rate.count) }
                }
            }.catch { emit(emptyList()) }
        }
    }
    val surprisesToRate by toRateFlow.collectAsState(initial = emptyList())
    HomeR1bisScreen(
        top = photoTop,
        mySurprises = mySurprises,
        surprisesToRate = surprisesToRate,
        feedUnseen = feed?.unseen ?: 0,
        onOpenFeed = if (photo != null && feed != null) ({
            onFeed = true
            photo.scroll.reset()
        }) else null,
        switcher = feed?.let { f -> { HomeFeedSwitch(onFeed, f.unseen, onChoose = { onFeed = it }) } },
        feed = feed?.takeIf { onFeed }?.let { f ->
            {
                CompositionLocalProvider(LocalFeedNavigation provides feedNavigation) {
                    // Publish from the fil itself (maquette SF1); never in presentation or « Voir comme ».
                    val publish: (@Composable () -> Unit)? = if (canPublish && f.context?.readOnly == false) {
                        { FeedPublishBar(currentMember, onPublishGoodNews) }
                    } else {
                        null
                    }
                    if (photo != null && photoTop != null) {
                        // Fiche A2: « Le fil de la famille » and its « + Publier » pill, then a glass
                        // card per actualité, on the photo.
                        val canWrite = canPublish && f.context?.readOnly == false
                        val top: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                photoTop()
                                PhotoSection(
                                    title = stringResource(R.string.home_photo_feed),
                                    colors = PhotoColors.of(photo.theme),
                                    pill = if (canWrite) stringResource(R.string.home_photo_feed_publish) else null,
                                    onPill = if (canWrite) onPublishGoodNews else null,
                                )
                            }
                        }
                        CompositionLocalProvider(LocalFeedGlass provides true) { FeedList(f, top = top) }
                    } else {
                        FeedList(f, top = publish)
                    }
                }
            }
        },
        state = shownState,
        joinMe = joinMe?.let { shares ->
            {
                ClayCard(Modifier.fillMaxWidth()) {
                    ClayCardTitle(stringResource(R.string.joinme_title))
                    JoinMeHomeContent(shares.state, shares.stopFailed, shares.onStop)
                }
            }
        },
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
        // The day before and on the day, those who go see their departure; then « En ce moment ».
        nowTravelling = rememberTripDeparture(container, currentMember)?.let { departure ->
            {
                TripDepartureCard(
                    departure,
                    onPacking = if (flags.isOn(Feature.PackingList)) {
                        { actions.onOpenPacking(departure.tripId) }
                    } else {
                        null
                    },
                    onOpen = { actions.onOpenTrip(departure.tripId) },
                )
            }
        } ?: rememberNowTravelling(container, currentMember)?.let { now ->
            { NowTravellingCard(now, onOpen = { actions.onOpenTrip(now.tripId) }) }
        },
        allBadge = allBadge,
    )
    }
}

/** The free top of the photo, under the greeting, where nothing is written (règle 13, A1: 170 dp). */
private val PHOTO_FREE_TOP = 170.dp

/** The Accueil on the photo of [theme] (récit 113): [header] at its top, [scroll] for the photo to glide. */
class PhotoHome(val theme: FamiliaThemeId, val scroll: PhotoScroll, val header: @Composable () -> Unit)

/**
 * Asks the Accueil to show « Aujourd'hui » rather than « Le fil »: the notification « rejoins-moi »
 * (MainActivity), whose place is at the top of the day. Kept until the Accueil reads it.
 */
object TodayOpenRequest {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(false)
    val asked: kotlinx.coroutines.flow.StateFlow<Boolean> = state

    fun ask() {
        state.value = true
    }

    fun consume() {
        state.value = false
    }
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
        trips = listOf(HomeTrip(trip, "Camille et Noé", ongoing = true, TripArrival(Instant.parse("2026-11-19T15:10:00Z"), "a", "Camille"))),
        canCreateTrip = true,
    )
}

@FamiliaPreviews
@Composable
private fun HomeScreenPreview() {
    FamiliaTheme { Surface { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) } }
}
