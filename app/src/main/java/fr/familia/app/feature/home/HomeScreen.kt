package fr.familia.app.feature.home

import fr.familia.app.feature.trips.now.rememberNowTravelling
import fr.familia.app.feature.trips.now.NowTravellingCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.feature.joinme.JoinMeHome
import fr.familia.app.feature.joinme.JoinMeHomeContent
import fr.familia.app.feature.joinme.rememberJoinMeHome
import fr.familia.app.feature.month.monthName
import fr.familia.app.feature.tree.TreeHomeChip
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionHomeChip
import fr.familia.app.feature.yearago.YearAgoHomeChip
import fr.familia.app.feature.discover.DiscoverCard
import fr.familia.app.core.model.Poll
import fr.familia.app.feature.polls.shortDateLabel
import fr.familia.app.feature.dailynudge.DailyNudge
import fr.familia.app.feature.dailynudge.DailyNudgeLine
import fr.familia.app.feature.dailynudge.rememberDailyNudge
import fr.familia.app.feature.discover.DiscoverHome
import fr.familia.app.feature.discover.DiscoverModule
import fr.familia.app.feature.discover.rememberDiscoverHome
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
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.photo.CoverThumbnail
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import fr.familia.app.feature.goodnews.home.GoodNewsHomeCard
import fr.familia.app.feature.month.isSlideshowTime
import fr.familia.app.feature.tree.TreeHomeCard
import fr.familia.app.feature.yearago.YearAgoHomeCard
import fr.familia.app.feature.trips.tripDatesLabel
import fr.familia.app.feature.whoisit.WhoIsItHomeCard
import fr.familia.app.feature.whoisit.WhoIsItHomeChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Where each row of the home screen leads. Null: the row is not a link (its module is off). */
class HomeActions(
    val onOpenEvent: (String) -> Unit = {},
    val onOpenPlanning: () -> Unit = {},
    val onOpenHelpRequest: (String) -> Unit = {},
    val onOpenWishes: ((memberId: String) -> Unit)? = null,
    val onOpenList: (String) -> Unit = {},
    val onOpenTrip: (String) -> Unit = {},
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
    val placesOn = flags.isOn(Feature.Places)
    val joinMe = if (placesOn) rememberJoinMeHome(container, currentMember) else null
    val discover = if (flags.isOn(Feature.Discover)) rememberDiscoverHome(currentMember, onOpenDiscover) else null
    // Null while its switch is off or there is nothing to suggest today.
    val dailyNudge = rememberDailyNudge(container, currentMember)
    // The small modules stand in the row of chips, each only while it has something to say (rév. 205).
    val extras = buildList {
        if (flags.isOn(Feature.WeeklyQuestion)) {
            add(HomeExtra("weeklyQuestion", chip = { _, _ -> WeeklyQuestionHomeChip(container, currentMember, onOpenWeeklyQuestion) }))
        }
        if (flags.isOn(Feature.Tree)) {
            add(
                HomeExtra(
                    "tree",
                    chip = { selected, toggle -> TreeHomeChip(container, currentMember, selected, toggle) },
                    card = {
                        TreeHomeCard(
                            container,
                            currentMember = currentMember,
                            onPrepareChallenge = onPrepareChallenge,
                            onJoinChallenge = onJoinChallenge,
                        )
                    },
                ),
            )
        }
        if (flags.isOn(Feature.YearAgo)) {
            add(
                HomeExtra(
                    "yearAgo",
                    chip = { selected, toggle -> YearAgoHomeChip(container, currentMember, selected, toggle) },
                    card = { YearAgoHomeCard(container, currentMember, onOpenGoodNews = onOpenGoodNews, onOpenTrip = actions.onOpenTrip) },
                ),
            )
        }
        if (flags.isOn(Feature.Recipes)) {
            add(HomeExtra("newRecipe", chip = { _, _ -> RecipeHomeChip(container, currentMember.id, onOpenRecipe) }))
        }
        if (flags.isOn(Feature.WhoIsIt)) {
            add(
                HomeExtra(
                    "whoIsIt",
                    chip = { selected, toggle -> WhoIsItHomeChip(container, currentMember, selected, toggle) },
                    card = { WhoIsItHomeCard(container, currentMember) },
                ),
            )
        }
        if (placesOn) {
            add(HomeExtra("joinMe", chip = { _, _ -> HomeChip(stringResource(R.string.joinme_send_open), onClick = onJoinMe) }))
        }
    }
    if (flags.isOn(Feature.PresentationR1bis)) {
        val answerFailed by viewModel.answerFailed.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(answerFailed) {
            if (answerFailed) {
                Toast.makeText(context, R.string.home_r1_answer_failed, Toast.LENGTH_LONG).show()
                viewModel.answerFailureShown()
            }
        }
        HomeR1bisScreen(
            state = state,
            actions = actions,
            r1 = HomeR1bisActions(onAnswer = viewModel::answer, onOpenAll = onOpenAll, onJoinMe = if (placesOn) onJoinMe else null),
            goodNews = if (flags.isOn(Feature.GoodNews)) {
                { modifier -> GoodNewsHomeCard(container, currentMember, onOpenGoodNewsFeed, onOpenGoodNews, onPublishGoodNews, modifier) }
            } else {
                null
            },
            nowTravelling = rememberNowTravelling(container, currentMember)?.let { now ->
                { NowTravellingCard(now, onOpen = { actions.onOpenTrip(now.tripId) }) }
            },
            allBadge = allBadge,
        )
        return
    }
    HomeScreen(
        state = state,
        actions = actions,
        goodNews = if (flags.isOn(Feature.GoodNews)) {
            { GoodNewsHomeCard(container, currentMember, onOpenGoodNewsFeed, onOpenGoodNews, onPublishGoodNews) }
        } else {
            null
        },
        onOpenMonth = if (flags.isOn(Feature.MonthSlideshow)) onOpenMonth else null,
        joinMe = joinMe,
        dailyNudge = dailyNudge,
        onOpenGoodNews = onOpenGoodNews,
        discover = discover,
        extras = extras,
    )
}

/**
 * The Accueil (PRD rév. 205, DESIGN-11): four blocks at most, the same for everyone and in a fixed
 * order, each hidden while it has nothing to say: « À faire pour moi » (in relief, with what
 * « Rejoignez-moi » sent me at its top), « Cette semaine », « Bonnes nouvelles », then « À découvrir »
 * the week of its module ([discover], AVIS-19). Then one row of chips: anniversaires first, then the
 * question of the week, l'arbre, le diaporama, les voyages, la prochaine occasion and the [extras]
 * that follow. A chip with a card opens it under the row, one at a time; the others open their screen.
 * [goodNews] is given while its module is on, [onOpenMonth] while the slideshow is, from the last
 * Sunday of the month (récit 42), and [joinMe] while the places module is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    goodNews: (@Composable () -> Unit)? = null,
    onOpenMonth: ((YearMonth) -> Unit)? = null,
    joinMe: JoinMeHome? = null,
    dailyNudge: DailyNudge? = null,
    onOpenGoodNews: (String) -> Unit = {},
    discover: DiscoverHome? = null,
    extras: List<HomeExtra> = emptyList(),
) {
    val ready = state as? HomeUiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val month = YearMonth.from(ready.today).takeIf { onOpenMonth != null && isSlideshowTime(ready.today) }
    val toDo = ready.todos.isNotEmpty() || joinMe?.hasShares == true || dailyNudge != null
    // Four blocks at most, in this priority (DESIGN, 16/11): a fifth, « À découvrir », becomes the
    // first chip, with the same name, and opens its card under the row.
    val blocks = listOf(toDo, ready.week.isNotEmpty(), ready.showsPolls, goodNews != null).count { it }
    val discoverBlock = discover?.takeIf { blocks < HOME_BLOCKS }
    val discoverChip = discover?.takeIf { blocks >= HOME_BLOCKS }?.let { d ->
        HomeExtra(
            "discover",
            chip = { selected, toggle -> HomeChip(stringResource(R.string.discover_title), onClick = toggle, selected = selected) },
            card = { DiscoverCard(d) },
        )
    }
    val chips = listOfNotNull(discoverChip) + homeChips(ready, actions, month, onOpenMonth, extras)
    if (!toDo && ready.week.isEmpty() && !ready.showsPolls && goodNews == null && discover == null && chips.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CalendarMonth,
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
        ) {
            Button(onClick = actions.onOpenPlanning, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_week_all))
            }
        }
        return
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = chips.firstOrNull { it.key == open }?.card
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ClaySpacing.list,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        if (toDo) card("todo") { TodoCard(ready.todos, ready.today, actions, joinMe?.takeIf { it.hasShares }, dailyNudge, onOpenGoodNews) }
        if (ready.week.isNotEmpty()) card("week") { WeekCard(ready.week, ready.weekMore, ready.today, actions) }
        if (ready.showsPolls) card("polls") { PollsCard(ready, actions) }
        if (goodNews != null) card("goodNews") { goodNews() }
        if (discoverBlock != null) card("discover") { DiscoverCard(discoverBlock) }
        if (chips.isNotEmpty()) {
            card("chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { extra ->
                        key(extra.key) {
                            extra.chip(open == extra.key) { open = if (open == extra.key) null else extra.key }
                        }
                    }
                }
            }
        }
        if (opened != null) card("open-$open") { opened() }
    }
}

/** The blocks of the Accueil before the chips (DESIGN-11). */
private const val HOME_BLOCKS = 4

/** The chips of the Accueil, in their order: those fed by the home screen, then the modules' [extras]. */
@Composable
private fun homeChips(
    ready: HomeUiState.Ready,
    actions: HomeActions,
    month: YearMonth?,
    onOpenMonth: ((YearMonth) -> Unit)?,
    extras: List<HomeExtra>,
): List<HomeExtra> {
    val birthdays = ready.birthdays.takeIf { it.isNotEmpty() }?.let { list ->
        HomeExtra(
            "birthdays",
            chip = { selected, toggle ->
                HomeChip(pluralStringResource(R.plurals.home_chip_birthdays, list.size, list.size), onClick = toggle, selected = selected)
            },
            card = { BirthdaysCard(list, ready.today, actions.onOpenWishes) },
        )
    }
    val slideshow = if (month != null && onOpenMonth != null) {
        HomeExtra("month", chip = { _, _ -> HomeChip(stringResource(R.string.month_title, monthName(month)), onClick = { onOpenMonth(month) }) })
    } else {
        null
    }
    // Only while a trip is ongoing or coming: no chip stays for nothing (DESIGN); all the trips are
    // reached from the Planning.
    val trips = if (ready.trips.isNotEmpty()) {
        HomeExtra(
            "trips",
            chip = { selected, toggle ->
                val label = if (ready.trips.any { it.ongoing }) {
                    stringResource(R.string.home_chip_trips_ongoing)
                } else {
                    stringResource(R.string.trips_title)
                }
                HomeChip(label, onClick = toggle, selected = selected)
            },
            card = { TripsCard(ready.trips, ready.today, ready.canCreateTrip, actions) },
        )
    } else {
        null
    }
    val occasion = ready.occasion?.let { occasion ->
        HomeExtra(
            "occasion",
            chip = { _, _ ->
                val left = occasionWhen(occasion.daysLeft).replaceFirstChar { it.lowercase() }
                if (occasion.isMine) {
                    // My own occasion: never a list's title, only when it is, and my envies.
                    val what = stringResource(
                        if (occasion.list.occasion == GiftOccasion.Birthday) R.string.home_chip_my_birthday else R.string.home_chip_my_occasion,
                    )
                    HomeChip(
                        stringResource(R.string.home_chip_detail, what, left),
                        onClick = { actions.onOpenWishes?.invoke(occasion.list.beneficiaryId) ?: actions.onOpenList(occasion.list.id) },
                    )
                } else {
                    HomeChip(
                        stringResource(R.string.home_chip_detail, occasion.list.title, left),
                        onClick = { actions.onOpenList(occasion.list.id) },
                    )
                }
            },
        )
    }
    val byKey = extras.associateBy { it.key }
    return listOfNotNull(
        birthdays,
        byKey["weeklyQuestion"],
        byKey["tree"],
        slideshow,
        trips,
        occasion,
    ) + extras.filter { it.key != "weeklyQuestion" && it.key != "tree" }
}

@Composable
private fun occasionWhen(daysLeft: Long): String = when (daysLeft) {
    0L -> stringResource(R.string.home_occasion_today)
    1L -> stringResource(R.string.home_occasion_tomorrow)
    else -> pluralStringResource(R.plurals.home_occasion_in_days, daysLeft.toInt(), daysLeft.toInt())
}

private fun LazyListScope.card(key: String, content: @Composable () -> Unit) {
    item(key = key) { content() }
}

/** A row of a card: 56 dp tall at least, clickable as a whole when [onClick] is set. */
@Composable
private fun HomeRow(
    title: String,
    detail: String?,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    tint: ClayTint = ClayTint.Plain,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
            }
        }
    }
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
}

@Composable
private fun TodoCard(
    todos: List<HomeTodo>,
    today: LocalDate,
    actions: HomeActions,
    joinMe: JoinMeHome?,
    dailyNudge: DailyNudge? = null,
    onOpenGoodNews: (String) -> Unit = {},
) {
    // A place sent to me tints the block for my foyer, as the « Rejoignez-moi » card did (récit 33).
    val tint = if (joinMe?.state?.received?.isNotEmpty() == true) ClayTint.Household else ClayTint.Plain
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint, emphasis = true) {
        ClayCardTitle(stringResource(R.string.home_todo_title))
        joinMe?.let {
            JoinMeHomeContent(it.state, it.stopFailed, it.onStop)
            if (dailyNudge != null || todos.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        // The small gesture of the day, under « Rejoignez-moi » (help-requests thread, #162).
        dailyNudge?.let {
            DailyNudgeLine(it, onOpenGoodNews = onOpenGoodNews)
            if (todos.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        todos.forEach { todo ->
            when (todo) {
                is HomeTodo.Answer -> HomeRow(
                    title = todo.event.title,
                    detail = stringResource(R.string.home_todo_answer, eventWhen(todo.event, today)),
                    onClick = { actions.onOpenEvent(todo.event.id) },
                    onClickLabel = stringResource(R.string.home_open_event),
                ) { RowIcon(Icons.Outlined.Event) }
                is HomeTodo.Help -> HomeRow(
                    title = todo.request.title.ifBlank { kindLabel(todo.request.kind) },
                    detail = stringResource(R.string.home_todo_help, todo.request.authorName, whenLabel(todo.request, today)),
                    onClick = { actions.onOpenHelpRequest(todo.request.id) },
                    onClickLabel = stringResource(R.string.home_open_help),
                ) { RowIcon(Icons.Outlined.VolunteerActivism) }
                is HomeTodo.Vote -> HomeRow(
                    title = todo.poll.question,
                    detail = stringResource(R.string.home_todo_vote, todo.poll.authorName),
                    onClick = { actions.onOpenPoll(todo.poll.id) },
                    onClickLabel = stringResource(R.string.home_open_poll),
                ) { RowIcon(Icons.Outlined.HowToVote) }
            }
        }
    }
}

/**
 * « Sondages » (BUG-1): first those that wait for my vote, then those where I voted, until their
 * end and two days with their result (retour d'essai du 16/11). « Nouveau sondage » for an adult,
 * and « Tous les sondages ».
 */
@Composable
private fun PollsCard(ready: HomeUiState.Ready, actions: HomeActions) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        ClayCardTitle(stringResource(R.string.home_polls_title))
        ready.pollsToVote.forEach { todo ->
            HomeRow(
                title = todo.poll.question,
                detail = stringResource(R.string.home_todo_vote, todo.poll.authorName),
                onClick = { actions.onOpenPoll(todo.poll.id) },
                onClickLabel = stringResource(R.string.home_open_poll),
            ) { RowIcon(Icons.Outlined.HowToVote) }
        }
        ready.voted.forEach { item ->
            HomeRow(
                title = item.poll.question,
                detail = votedDetail(item),
                onClick = { actions.onOpenPoll(item.poll.id) },
                onClickLabel = stringResource(R.string.home_open_poll),
            ) { RowIcon(Icons.Outlined.HowToVote) }
        }
        if (ready.canAskPoll) {
            Button(onClick = actions.onNewPoll, modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.home_polls_new))
            }
        }
        ClayLink(stringResource(R.string.home_polls_all), onClick = actions.onOpenPolls)
    }
}

/** « Tu as voté · en tête : sam. 19/12 (4 voix) », or « Terminé · retenu : … » once it is over. */
@Composable
private fun votedDetail(item: HomeVoted): String {
    val poll = item.poll
    val first = item.leaders.firstOrNull()
    val label = first?.let { if (poll.kind == Poll.Kind.Date) shortDateLabel(poll, it) else poll.options.getOrNull(it).orEmpty() }
    return when {
        item.over && poll.chosen != null && label != null -> stringResource(R.string.home_voted_over_chosen, label)
        label == null -> stringResource(if (item.over) R.string.home_voted_over else R.string.home_voted)
        item.leaders.size > 1 -> stringResource(if (item.over) R.string.home_voted_over_tie else R.string.home_voted_tie)
        item.over -> stringResource(R.string.home_voted_over_lead, label, item.leaderVotes)
        else -> stringResource(R.string.home_voted_lead, label, item.leaderVotes)
    }
}

/** "Demain · 18 h 00", or the day alone for an event that lasts all day. */
@Composable
private fun eventWhen(event: Event, today: LocalDate): String {
    val day = dayShortLabel(event.date, today)
    return event.time?.let { "$day · ${formatTime(it)}" } ?: day
}

@Composable
private fun WeekCard(week: List<Event>, more: Int, today: LocalDate, actions: HomeActions) {
    val covers = LocalFeatureFlags.current.isOn(Feature.MorePhotos)
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        ClayCardTitle(stringResource(R.string.home_week_title))
        week.forEach { event ->
            HomeRow(
                title = event.title,
                detail = listOfNotNull(eventWhen(event, today), event.place?.takeIf { it.isNotBlank() }).joinToString(" · "),
                onClick = { actions.onOpenEvent(event.id) },
                onClickLabel = stringResource(R.string.home_open_event),
            ) {
                // The thumbnail of its cover in place of the icon (récit 44).
                val cover = event.coverPhoto?.takeIf { covers }
                if (cover != null) CoverThumbnail(cover, size = 40.dp) else RowIcon(Icons.Outlined.Event)
            }
        }
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.home_week_more, more, more),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
        ClayLink(stringResource(R.string.home_week_all), onClick = actions.onOpenPlanning)
    }
}

@Composable
private fun BirthdaysCard(birthdays: List<Birthday>, today: LocalDate, onOpenWishes: ((String) -> Unit)?) {
    val tint = ClayTint.Birthday
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        ClayCardTitle(stringResource(R.string.home_birthdays_title))
        birthdays.forEach { birthday ->
            HomeRow(
                title = birthday.member.firstName,
                detail = stringResource(
                    R.string.birthday_when_age,
                    dayShortLabel(birthday.date, today),
                    pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
                ),
                onClick = onOpenWishes?.let { open -> { open(birthday.member.id) } },
                onClickLabel = stringResource(R.string.home_open_wishes),
                tint = tint,
            ) { MemberAvatar(birthday.member, size = 40.dp) }
        }
    }
}

@Composable
private fun TripsCard(trips: List<HomeTrip>, today: LocalDate, canCreate: Boolean, actions: HomeActions) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        ClayCardTitle(stringResource(if (trips.isEmpty()) R.string.trips_title else R.string.home_trips_title))
        trips.forEach { item ->
            HomeRow(
                title = item.trip.title,
                detail = listOfNotNull(
                    if (item.ongoing) stringResource(R.string.home_trip_ongoing) else tripDatesLabel(item.trip, today),
                    item.who.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.trip_with, it) },
                ).joinToString(" · "),
                onClick = { actions.onOpenTrip(item.trip.id) },
                onClickLabel = stringResource(R.string.trips_open_action),
            ) { RowIcon(Icons.Outlined.CardTravel) }
            item.lastArrival?.let { ArrivalLine(it, today) }
        }
        if (trips.isEmpty()) {
            Text(stringResource(R.string.home_trips_none), style = MaterialTheme.typography.bodyLarge)
        }
        if (canCreate) ClayLink(stringResource(R.string.trips_add), onClick = actions.onNewTrip)
        actions.onOpenTrips?.let { open -> ClayLink(stringResource(R.string.home_trips_all), onClick = open) }
    }
}

@Composable
private fun ArrivalLine(arrival: TripArrival, today: LocalDate) {
    val at = arrival.at?.atZone(ZoneId.systemDefault())
    val label = if (at == null) {
        stringResource(R.string.trip_arrived_by, arrival.name)
    } else {
        val day = dayShortLabel(at.toLocalDate(), today).lowercase(Locale.FRANCE)
        stringResource(R.string.trip_arrived_by_at, arrival.name, "$day, ${formatTime(at.toLocalTime())}")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
    FamiliaTheme { Surface { HomeScreen(previewHome(), HomeActions(onOpenWishes = {})) } }
}
