package fr.familia.app.feature.home

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.concerns
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.feature.helprequests.canVolunteer
import fr.familia.app.feature.polls.canAskPoll
import fr.familia.app.feature.trips.canCreateTrip
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.joinNames
import java.time.Instant
import java.time.ZoneId
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.leaders
import fr.familia.app.core.model.tally
import java.time.Duration
import java.time.LocalDate

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

/**
 * A poll where I voted: it stays on the Accueil until its end, then [HomeWindow.POLL_RESULT_DAYS]
 * more days with its result ([over]). [leaders] are the options in the lead (or the one chosen),
 * [leaderVotes] the yes of the first of them.
 */
data class HomeVoted(val poll: Poll, val leaders: List<Int>, val leaderVotes: Int, val over: Boolean)

/** A trip I follow, ongoing or to come, with the last « Bien arrivé » while it is ongoing. */
data class HomeTrip(val trip: Trip, val who: String, val ongoing: Boolean, val lastArrival: TripArrival?)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** Each card shows only when it has something to say (PRD, « Tableau de bord d'accueil »). */
    data class Ready(
        val today: LocalDate,
        val todos: List<HomeTodo> = emptyList(),
        val week: List<Event> = emptyList(),
        /** The 7 days of the frise (R1 bis): every event of mine, the help and the birthdays. */
        val days: List<HomeDay> = emptyList(),
        /** Events of the 7 days beyond the 3 shown. */
        val weekMore: Int = 0,
        val birthdays: List<Birthday> = emptyList(),
        val trips: List<HomeTrip> = emptyList(),
        val canCreateTrip: Boolean = false,
        /**
         * The block « Sondages » (BUG-1, tranché par l'admin le 16/11): the polls that wait for my
         * vote, then those where I voted. It shows only while the module is on ([pollsOn]) and
         * there is a poll to show (DESIGN: never an empty block); [canAskPoll] adds « Nouveau sondage ».
         */
        val pollsToVote: List<HomeTodo.Vote> = emptyList(),
        val voted: List<HomeVoted> = emptyList(),
        val pollsOn: Boolean = false,
        val canAskPoll: Boolean = false,
        /** « À venir » on the photo (récit 113, règle 7): my events after today, 30 days at most, by date. */
        val upcoming: List<Event> = emptyList(),
    ) : HomeUiState {
        val showsPolls: Boolean get() = pollsOn && (pollsToVote.isNotEmpty() || voted.isNotEmpty())
        val isEmpty: Boolean get() = todos.isEmpty() && !showsPolls && week.isEmpty() && birthdays.isEmpty() && trips.isEmpty()
    }
}

/** How far ahead each card looks. */
internal object HomeWindow {
    const val TODO_DAYS = 30L
    const val UPCOMING_DAYS = 30L
    const val TODO_SHOWN = 5
    const val VOTED_SHOWN = 2
    const val TO_VOTE_SHOWN = 3
    const val POLL_RESULT_DAYS = 2L
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
        // An empty list: the answer could not be read, so « À faire » does not ask again (note C de #317).
        .filterNot { event -> answers[event.id]?.let { list -> list.isEmpty() || list.any { it.memberId == me.id } } == true }
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

/**
 * A poll stays on the Accueil while it is open, and [HomeWindow.POLL_RESULT_DAYS] days after its
 * end date to show the result. One closed by hand has no date of closing: it goes at once,
 * unless its end date is still to come.
 */
internal fun showsOnHome(poll: Poll, now: Instant): Boolean =
    poll.isOpen(now) || (poll.closesAt != null && now < poll.closesAt.plus(Duration.ofDays(HomeWindow.POLL_RESULT_DAYS)))

/** The polls that asked me and where I voted, the soonest to end first. */
internal fun pollsVoted(me: Member, polls: List<Poll>, votes: Map<String, List<PollVote>>, now: Instant): List<HomeVoted> =
    polls
        .filter { me.id in it.askedIds && showsOnHome(it, now) }
        .filter { poll -> votes[poll.id].orEmpty().any { it.memberId == me.id } }
        .sortedBy { it.closesAt ?: Instant.MAX }
        .take(HomeWindow.VOTED_SHOWN)
        .map { poll ->
            val all = votes[poll.id].orEmpty()
            val lead = poll.chosen?.let { listOf(it) } ?: leaders(poll, all).sorted()
            val count = lead.firstOrNull()?.let { tally(poll, all).getOrNull(it)?.yes?.size } ?: 0
            HomeVoted(poll, lead, count, over = !poll.isOpen(now))
        }

/** The events that concern me from tomorrow to [HomeWindow.UPCOMING_DAYS] days ahead, by date then hour. */
internal fun upcoming(me: Member, family: Family, events: List<Event>, today: LocalDate): List<Event> {
    val last = today.plusDays(HomeWindow.UPCOMING_DAYS)
    return events
        .filter { it.date > today && it.date <= last && concerns(it.target, me, family) }
        .sortedWith(compareBy<Event> { it.date }.thenBy(nullsLast()) { it.time })
}

/** The events of the next 7 days that concern me, by date. */
internal fun week(me: Member, family: Family, events: List<Event>, today: LocalDate): List<Event> {
    val last = today.plusDays(HomeWindow.WEEK_DAYS - 1)
    return events.filter { it.date in today..last && concerns(it.target, me, family) }
}

internal fun birthdays(family: Family, today: LocalDate): List<Birthday> =
    upcomingBirthdays(family.members, today).filter { it.date <= today.plusDays(HomeWindow.BIRTHDAY_DAYS) }

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
    trips: List<HomeTrip>,
    toVote: List<HomeTodo.Vote> = emptyList(),
    voted: List<HomeVoted> = emptyList(),
    pollsOn: Boolean = false,
): HomeUiState.Ready {
    val week = week(me, family, events, today)
    val birthdays = birthdays(family, today)
    return HomeUiState.Ready(
        today = today,
        // The polls have their own block now, so « À faire pour moi » no longer lists them.
        todos = todos(me, family, events, answers, help, today),
        week = week.take(HomeWindow.WEEK_SHOWN),
        weekMore = (week.size - HomeWindow.WEEK_SHOWN).coerceAtLeast(0),
        days = homeDays(me, today, week, help, birthdays),
        birthdays = birthdays,
        trips = trips,
        canCreateTrip = canCreateTrip(me),
        pollsToVote = toVote.sortedBy { it.date }.take(HomeWindow.TO_VOTE_SHOWN),
        voted = voted,
        pollsOn = pollsOn,
        canAskPoll = canAskPoll(me),
        upcoming = upcoming(me, family, events, today),
    )
}
