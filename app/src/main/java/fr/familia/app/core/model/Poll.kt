package fr.familia.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A poll (PRD, récits 2 bis et 2 ter): a date poll proposes 2 to 5 dates, each answered yes,
 * maybe or no; a free poll proposes 2 to 6 texts, one or several chosen. Its question and options
 * never change once asked.
 */
data class Poll(
    val id: String,
    val kind: Kind,
    val question: String,
    /** For a date poll, « 2026-12-19 » or « 2026-12-19 12:30 »; see [dateOption]. */
    val options: List<String>,
    val multiple: Boolean,
    val askedIds: List<String>,
    val viewerIds: List<String>,
    val eventId: String?,
    val closesAt: Instant?,
    val closed: Boolean,
    val chosen: Int?,
    val createdEventId: String?,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant?,
) {
    enum class Kind(val key: String) {
        Date("date"),
        Choice("choice"),
    }

    /** Votes are taken until it is closed or its end date passes. */
    fun isOpen(now: Instant): Boolean = !closed && (closesAt == null || now < closesAt)

    companion object {
        const val MAX_QUESTION = 120
        const val MAX_OPTION = 80
        const val MIN_OPTIONS = 2
        const val MAX_DATES = 5
        const val MAX_CHOICES = 6

        fun maxOptions(kind: Kind) = if (kind == Kind.Date) MAX_DATES else MAX_CHOICES

        /** « 2026-12-19 12:30 » → the day and the time; null when it is not a date option. */
        fun dateOption(option: String): Pair<LocalDate, LocalTime?>? = runCatching {
            val parts = option.split(" ")
            LocalDate.parse(parts[0]) to parts.getOrNull(1)?.let { LocalTime.parse(it) }
        }.getOrNull()

        fun dateOptionKey(day: LocalDate, time: LocalTime?): String =
            if (time == null) day.toString() else "$day ${time.toString().take(5)}"
    }
}

enum class DateAnswer(val key: String) {
    Yes("yes"),
    Maybe("maybe"),
    No("no"),
}

/** One member's vote: for a date poll one answer per date, for a free poll the indexes chosen. */
data class PollVote(
    val memberId: String,
    val answers: List<DateAnswer> = emptyList(),
    val choices: List<Int> = emptyList(),
    val votedBy: String,
)

/** How many chose each option of a free poll, or said yes / maybe to each date. */
data class OptionTally(val yes: List<String>, val maybe: List<String> = emptyList())

fun tally(poll: Poll, votes: List<PollVote>): List<OptionTally> = poll.options.indices.map { i ->
    when (poll.kind) {
        Poll.Kind.Choice -> OptionTally(votes.filter { i in it.choices }.map { it.memberId })
        Poll.Kind.Date -> OptionTally(
            yes = votes.filter { it.answers.getOrNull(i) == DateAnswer.Yes }.map { it.memberId },
            maybe = votes.filter { it.answers.getOrNull(i) == DateAnswer.Maybe }.map { it.memberId },
        )
    }
}

/**
 * The options in the lead: most yes (then most maybe, for dates). Empty while nobody answered,
 * so the screen highlights nothing.
 */
fun leaders(poll: Poll, votes: List<PollVote>): Set<Int> {
    val tallies = tally(poll, votes)
    val score = tallies.map { it.yes.size * 1000 + it.maybe.size }
    val best = score.maxOrNull() ?: return emptySet()
    if (best == 0) return emptySet()
    return score.indices.filter { score[it] == best }.toSet()
}

/** The members asked who have not answered yet. */
fun notAnswered(poll: Poll, votes: List<PollVote>): List<String> {
    val answered = votes.map { it.memberId }.toSet()
    return poll.askedIds.filter { it !in answered }
}

/**
 * Who can see a poll asking [askedIds]: the author first (the rules check it), the members asked,
 * then the responsible adults of the children asked, who answer for them.
 */
fun pollViewers(authorId: String, askedIds: List<String>, members: List<Member>): List<String> {
    val byId = members.associateBy { it.id }
    val parents = askedIds.flatMap { byId[it]?.parentIds.orEmpty() }
    return (listOf(authorId) + askedIds + parents).distinct()
}
