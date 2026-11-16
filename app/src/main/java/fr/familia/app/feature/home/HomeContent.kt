package fr.familia.app.feature.home

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.concerns
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.feature.helprequests.canVolunteer
import fr.familia.app.feature.trips.canCreateTrip
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.joinNames
import java.time.Instant
import java.time.ZoneId
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One line of « À faire pour moi »: it opens the event or the coup de main. */
sealed interface HomeTodo {
    val date: LocalDate

    /** An event that concerns me, where I have not said if I come. */
    data class Answer(val event: Event) : HomeTodo {
        override val date: LocalDate get() = event.date
    }

    /** A coup de main where I was warned and can still help. */
    data class Help(val request: HelpRequest) : HomeTodo {
        override val date: LocalDate get() = request.date
    }

    /** A poll that asks me and that I have not answered (PRD, récits 2 bis et 2 ter). */
    data class Vote(val poll: Poll, override val date: LocalDate) : HomeTodo
}

/** A trip I follow, ongoing or to come, with the last « Bien arrivé » while it is ongoing. */
data class HomeTrip(val trip: Trip, val who: String, val ongoing: Boolean, val lastArrival: TripArrival?)

/**
 * The next occasion of a gift list I can see. [myClaims] is null on my own list: what is reserved
 * for me never shows. Surprise lists never come here, they stay behind the Cadeaux tab.
 */
data class HomeOccasion(val list: GiftList, val beneficiaryName: String, val daysLeft: Long, val myClaims: Int?)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** Each card shows only when it has something to say (PRD, « Tableau de bord d'accueil »). */
    data class Ready(
        val today: LocalDate,
        val todos: List<HomeTodo> = emptyList(),
        val week: List<Event> = emptyList(),
        /** Events of the 7 days beyond the 3 shown. */
        val weekMore: Int = 0,
        val birthdays: List<Birthday> = emptyList(),
        val occasion: HomeOccasion? = null,
        val trips: List<HomeTrip> = emptyList(),
        val canCreateTrip: Boolean = false,
    ) : HomeUiState {
        val isEmpty: Boolean get() = todos.isEmpty() && week.isEmpty() && birthdays.isEmpty() && occasion == null && trips.isEmpty()
    }
}

/** How far ahead each card looks. */
internal object HomeWindow {
    const val TODO_DAYS = 30L
    const val TODO_SHOWN = 5
    const val WEEK_DAYS = 7L
    const val WEEK_SHOWN = 3
    const val BIRTHDAY_DAYS = 30L
    const val TRIPS_SHOWN = 2
}

/** What I still have to answer: my own answer only, never one for a child of someone else. */
internal fun todos(
    me: Member,
    family: Family,
    events: List<Event>,
    answers: Map<String, List<Rsvp>>,
    help: List<HelpRequest>,
    today: LocalDate,
    toVote: List<HomeTodo.Vote> = emptyList(),
): List<HomeTodo> {
    val last = today.plusDays(HomeWindow.TODO_DAYS)
    val toAnswer = events
        .filter { it.date in today..last && concerns(it.target, me, family) }
        .filterNot { it.target is EventTarget.ForChild && it.target.memberId != me.id }
        .filterNot { event -> answers[event.id].orEmpty().any { it.memberId == me.id } }
        .map { HomeTodo.Answer(it) }
    // Same rule as the badge of the Entraide tab (badgeCount), so the two never disagree.
    val toHelp = help
        .filter { it.date >= today && canVolunteer(it, me) && it.declinedBy != me.id }
        .map { HomeTodo.Help(it) }
    return (toAnswer + toHelp + toVote).sortedBy { it.date }.take(HomeWindow.TODO_SHOWN)
}

/**
 * The open polls that ask me and where I have not voted yet: my own vote only (a child with the
 * app votes for themselves). A date poll sorts by its first date, a free poll by its end, or today.
 */
internal fun pollsToVote(me: Member, polls: List<Poll>, votes: Map<String, List<PollVote>>, now: Instant, today: LocalDate): List<HomeTodo.Vote> =
    polls
        .filter { it.isOpen(now) && me.id in it.askedIds }
        .filterNot { poll -> votes[poll.id].orEmpty().any { it.memberId == me.id } }
        .map { poll ->
            val first = if (poll.kind == Poll.Kind.Date) poll.options.mapNotNull { Poll.dateOption(it)?.first }.minOrNull() else null
            val ends = poll.closesAt?.atZone(ZoneId.of("Europe/Paris"))?.toLocalDate()
            HomeTodo.Vote(poll, maxOf(first ?: ends ?: today, today))
        }

/** The events of the next 7 days that concern me, by date. */
internal fun week(me: Member, family: Family, events: List<Event>, today: LocalDate): List<Event> {
    val last = today.plusDays(HomeWindow.WEEK_DAYS - 1)
    return events.filter { it.date in today..last && concerns(it.target, me, family) }
}

internal fun birthdays(family: Family, today: LocalDate): List<Birthday> =
    upcomingBirthdays(family.members, today).filter { it.date <= today.plusDays(HomeWindow.BIRTHDAY_DAYS) }

/** The next dated, not yet revealed list I can see; surprise lists stay out of the home screen. */
internal fun nextOccasionList(lists: List<GiftList>, today: LocalDate): GiftList? =
    lists
        .filter { it.kind == GiftListKind.Own && !it.revealed && it.date != null && it.date >= today }
        .minByOrNull { it.date!! }

internal fun occasion(list: GiftList?, me: Member, family: Family, myClaims: Int, today: LocalDate): HomeOccasion? {
    if (list == null) return null
    val date = list.date ?: return null
    val name = family.members.firstOrNull { it.id == list.beneficiaryId }?.firstName ?: return null
    val mine = list.beneficiaryId == me.id
    return HomeOccasion(list, name, ChronoUnit.DAYS.between(today, date), if (mine) null else myClaims)
}

/** The trips I follow that are not over, the ongoing ones first. */
internal fun shownTrips(trips: List<Trip>, today: LocalDate): List<Trip> =
    trips.filter { it.endDate >= today }.sortedBy { it.startDate }.take(HomeWindow.TRIPS_SHOWN)

internal fun lastArrival(steps: List<TripStep>): TripArrival? =
    steps.mapNotNull { it.arrival }.maxByOrNull { it.at ?: Instant.MIN }

internal fun homeTrips(trips: List<Trip>, arrivals: Map<String, TripArrival?>, family: Family, today: LocalDate): List<HomeTrip> =
    trips.map { trip ->
        val ongoing = trip.startDate <= today
        HomeTrip(trip, joinNames(firstNames(trip.participantIds, family)), ongoing, if (ongoing) arrivals[trip.id] else null)
    }

/** Everything the home screen shows, from what the app already reads. */
internal fun homeState(
    me: Member,
    family: Family,
    today: LocalDate,
    events: List<Event>,
    answers: Map<String, List<Rsvp>>,
    help: List<HelpRequest>,
    occasion: HomeOccasion?,
    trips: List<HomeTrip>,
    toVote: List<HomeTodo.Vote> = emptyList(),
): HomeUiState.Ready {
    val week = week(me, family, events, today)
    return HomeUiState.Ready(
        today = today,
        todos = todos(me, family, events, answers, help, today, toVote),
        week = week.take(HomeWindow.WEEK_SHOWN),
        weekMore = (week.size - HomeWindow.WEEK_SHOWN).coerceAtLeast(0),
        birthdays = birthdays(family, today),
        occasion = occasion,
        trips = trips,
        canCreateTrip = canCreateTrip(me),
    )
}
