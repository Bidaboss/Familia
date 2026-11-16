package fr.familia.app.feature.home

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
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import fr.familia.app.feature.goodnews.home.GoodNewsHomeCard
import fr.familia.app.feature.joinme.JoinMeHomeCard
import fr.familia.app.feature.month.MonthSlideshowHomeCard
import fr.familia.app.feature.month.isSlideshowTime
import fr.familia.app.feature.tree.TreeHomeCard
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionHomeCard
import fr.familia.app.feature.trips.tripDatesLabel
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
) {
    val flags = LocalFeatureFlags.current
    val giftsOn = flags.isOn(Feature.Gifts)
    val helpOn = flags.isOn(Feature.HelpRequests)
    val tripsOn = flags.isOn(Feature.Trips)
    val viewModel: HomeViewModel = viewModel(
        key = "home-$giftsOn-$helpOn-$tripsOn",
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
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions = actions,
        goodNews = if (flags.isOn(Feature.GoodNews)) {
            { GoodNewsHomeCard(container, currentMember, onOpenGoodNewsFeed, onOpenGoodNews, onPublishGoodNews) }
        } else {
            null
        },
        weeklyQuestion = if (flags.isOn(Feature.WeeklyQuestion)) {
            { WeeklyQuestionHomeCard(container, currentMember, onOpenWeeklyQuestion) }
        } else {
            null
        },
        onOpenMonth = if (flags.isOn(Feature.MonthSlideshow)) onOpenMonth else null,
        tree = if (flags.isOn(Feature.Tree)) {
            { TreeHomeCard(container, currentMember = currentMember, onPrepareChallenge = onPrepareChallenge) }
        } else {
            null
        },
        joinMe = if (flags.isOn(Feature.Places)) {
            { JoinMeHomeCard(container, currentMember, onJoinMe) }
        } else {
            null
        },
    )
}

/**
 * The Accueil: fixed cards in a fixed order, the same for everyone, each hidden while it has
 * nothing to say (PRD, « Tableau de bord d'accueil »). [goodNews] is the « Bonnes nouvelles » card,
 * [weeklyQuestion] the question of the week (récit 37), both given only while their module is on.
 * [onOpenMonth], set while the slideshow module is on, shows its card from the last Sunday (récit 42).
 * [tree] is l'arbre de la famille and its challenge of the month (récit 35), while its module is on.
 * [joinMe], given while the places module is on, is the « Rejoignez-moi » card, first (récit 33).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    goodNews: (@Composable () -> Unit)? = null,
    weeklyQuestion: (@Composable () -> Unit)? = null,
    onOpenMonth: ((YearMonth) -> Unit)? = null,
    tree: (@Composable () -> Unit)? = null,
    joinMe: (@Composable () -> Unit)? = null,
) {
    val ready = state as? HomeUiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val month = YearMonth.from(ready.today).takeIf { onOpenMonth != null && isSlideshowTime(ready.today) }
    if (ready.isEmpty && goodNews == null && weeklyQuestion == null && month == null && tree == null && joinMe == null) {
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // First: someone may be waiting for me there (récit 33).
        if (joinMe != null) card("joinMe") { joinMe() }
        if (ready.todos.isNotEmpty()) card("todo") { TodoCard(ready.todos, ready.today, actions) }
        if (ready.week.isNotEmpty()) card("week") { WeekCard(ready.week, ready.weekMore, ready.today, actions) }
        if (goodNews != null) card("goodNews") { goodNews() }
        if (weeklyQuestion != null) card("weeklyQuestion") { weeklyQuestion() }
        if (tree != null) card("tree") { tree() }
        if (month != null && onOpenMonth != null) card("month") { MonthSlideshowHomeCard(month) { onOpenMonth(month) } }
        if (ready.birthdays.isNotEmpty()) card("birthdays") { BirthdaysCard(ready.birthdays, ready.today, actions.onOpenWishes) }
        ready.occasion?.let { occasion -> card("occasion") { OccasionCard(occasion, ready.today) { actions.onOpenList(occasion.list.id) } } }
        // While the module is on, the card always shows: it is the way to the trips (audit DESIGN n°4).
        if (ready.trips.isNotEmpty() || actions.onOpenTrips != null) {
            card("trips") { TripsCard(ready.trips, ready.today, ready.canCreateTrip, actions) }
        }
    }
}

private fun LazyListScope.card(key: String, content: @Composable () -> Unit) {
    item(key = key) { content() }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
private fun TodoCard(todos: List<HomeTodo>, today: LocalDate, actions: HomeActions) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.home_todo_title))
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
            }
        }
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
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.home_week_title))
        week.forEach { event ->
            HomeRow(
                title = event.title,
                detail = listOfNotNull(eventWhen(event, today), event.place?.takeIf { it.isNotBlank() }).joinToString(" · "),
                onClick = { actions.onOpenEvent(event.id) },
                onClickLabel = stringResource(R.string.home_open_event),
            ) { RowIcon(Icons.Outlined.Event) }
        }
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.home_week_more, more, more),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
        TextButton(onClick = actions.onOpenPlanning, modifier = Modifier.heightIn(min = 56.dp).align(Alignment.End)) {
            Text(stringResource(R.string.home_week_all))
        }
    }
}

@Composable
private fun BirthdaysCard(birthdays: List<Birthday>, today: LocalDate, onOpenWishes: ((String) -> Unit)?) {
    val tint = ClayTint.Birthday
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
        CardTitle(stringResource(R.string.home_birthdays_title))
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
private fun OccasionCard(occasion: HomeOccasion, today: LocalDate, onOpen: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.home_open_list),
    ) {
        CardTitle(stringResource(R.string.home_occasion_title))
        Text(occasion.list.title, style = MaterialTheme.typography.titleMedium)
        Text(
            when (occasion.daysLeft) {
                0L -> stringResource(R.string.home_occasion_today)
                1L -> stringResource(R.string.home_occasion_tomorrow)
                else -> pluralStringResource(R.plurals.home_occasion_in_days, occasion.daysLeft.toInt(), occasion.daysLeft.toInt())
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        occasion.myClaims?.let { claims ->
            Text(
                if (claims == 0) stringResource(R.string.home_occasion_no_claim) else pluralStringResource(R.plurals.home_occasion_claims, claims, claims),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
    }
}

@Composable
private fun TripsCard(trips: List<HomeTrip>, today: LocalDate, canCreate: Boolean, actions: HomeActions) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(if (trips.isEmpty()) R.string.trips_title else R.string.home_trips_title))
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
        Row(modifier = Modifier.align(Alignment.End)) {
            if (canCreate) {
                TextButton(onClick = actions.onNewTrip, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.trips_add))
                }
            }
            actions.onOpenTrips?.let { open ->
                TextButton(onClick = open, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.home_trips_all))
                }
            }
        }
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
