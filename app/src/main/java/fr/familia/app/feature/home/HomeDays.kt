package fr.familia.app.feature.home

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import java.time.LocalDate
import java.time.LocalTime

/** The color of a day of the frise (R1 bis, DESIGN-15): violet to organise, green for a hand, terracotta for a party. */
enum class DayTone { Organise, Help, Party }

/** What a line of the chosen day opens. */
sealed interface DayOpen {
    data class Event(val id: String) : DayOpen
    data class Help(val id: String) : DayOpen
    data class Birthday(val memberId: String) : DayOpen
}

/** One line of the chosen day: its hour, what, who, and the word shown in the frise. */
data class DayItem(
    val time: LocalTime?,
    val what: String,
    val who: String?,
    val word: String,
    val tone: DayTone,
    val open: DayOpen,
)

/** One of the 7 days of the frise, from today. */
data class HomeDay(val date: LocalDate, val items: List<DayItem>) {
    /** The word of the first item, on a pill of its color; null on an empty day. */
    val first: DayItem? get() = items.firstOrNull()
}

/**
 * The 7 days of the frise from [today]: my events, the help I can give or took, and the birthdays.
 * The birthdays come first in their day (no hour), then by hour, the ones without an hour last.
 */
internal fun homeDays(
    me: Member,
    today: LocalDate,
    events: List<Event>,
    help: List<HelpRequest>,
    birthdays: List<Birthday>,
): List<HomeDay> = (0 until HomeWindow.WEEK_DAYS).map { offset ->
    val date = today.plusDays(offset)
    val party = birthdays.filter { it.date == date }.map {
        DayItem(null, it.member.firstName, null, shortWord(it.member.firstName), DayTone.Party, DayOpen.Birthday(it.member.id))
    }
    val dated = events.filter { it.date == date }.map {
        DayItem(it.time, it.title, null, shortWord(it.title), if (isFestive(it.title)) DayTone.Party else DayTone.Organise, DayOpen.Event(it.id))
    } + help.filter { it.date == date && (it.status == HelpStatus.Open || it.volunteerId == me.id) }.map {
        DayItem(it.startTime, it.title, it.authorName, shortWord(it.title), DayTone.Help, DayOpen.Help(it.id))
    }
    HomeDay(date, party + dated.sortedWith(compareBy(nullsLast()) { it.time }))
}

private val SMALL_WORDS = setOf("le", "la", "les", "l", "un", "une", "des", "du", "de", "d", "au", "aux", "chez", "pour", "à", "a")

/** « Repas d'anniversaire » gives « Repas », « Le judo de Lou » gives « Judo »: one short word for the frise. */
internal fun shortWord(title: String, max: Int = 8): String {
    val words = title.split(' ', '\'', '’', '-').map { it.trim(',', '.', '!', '?', ':') }.filter { it.isNotBlank() }
    val word = words.firstOrNull { it.lowercase() !in SMALL_WORDS } ?: words.firstOrNull() ?: return ""
    val cut = if (word.length > max) word.take(max) else word
    return cut.replaceFirstChar { it.uppercase() }
}

private val FESTIVE = listOf("anniv", "fête", "fete", "repas", "noël", "noel", "soirée", "apéro", "apero", "mariage", "baptême", "réveillon", "goûter")

internal fun isFestive(title: String): Boolean {
    val lower = title.lowercase()
    return FESTIVE.any { it in lower }
}
