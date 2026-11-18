package fr.familia.app.feature.polls.edit

import fr.familia.app.core.model.Poll
import java.time.LocalDate
import java.time.LocalTime

/** One proposed date of a date poll; [day] is null until it is picked. */
data class DateChoice(val day: LocalDate? = null, val time: LocalTime? = null)

/** What the « Nouveau sondage » form holds. The question and options never change once asked. */
data class PollForm(
    val kind: Poll.Kind = Poll.Kind.Date,
    val question: String = "",
    val dates: List<DateChoice> = listOf(DateChoice(), DateChoice()),
    val choices: List<String> = listOf("", ""),
    val multiple: Boolean = false,
    val askedIds: Set<String> = emptySet(),
    /** The last day answers are taken; null: until the author ends it. */
    val closesOn: LocalDate? = null,
    /** Asked from an event: what it is about, to fill the question; never stored. */
    val motive: PollMotive? = null,
) {
    /**
     * The options as stored: the dates picked, without duplicates and in order, or the texts
     * typed, trimmed and without duplicates. Empty slots are left out.
     */
    fun options(): List<String> = when (kind) {
        Poll.Kind.Date -> dates
            .mapNotNull { choice -> choice.day?.let { it to choice.time } }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second != null }, { it.second }))
            .map { (day, time) -> Poll.dateOptionKey(day, time) }
        Poll.Kind.Choice -> choices.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    }

    val canAddOption: Boolean
        get() = when (kind) {
            Poll.Kind.Date -> dates.size < Poll.MAX_DATES
            Poll.Kind.Choice -> choices.size < Poll.MAX_CHOICES
        }

    val canRemoveOption: Boolean
        get() = when (kind) {
            Poll.Kind.Date -> dates.size > Poll.MIN_OPTIONS
            Poll.Kind.Choice -> choices.size > Poll.MIN_OPTIONS
        }
}

enum class PollFormError {
    QuestionMissing,
    QuestionTooLong,
    TooFewOptions,
    OptionTooLong,
    DatePast,
    NobodyAsked,
    ClosesPast,
}

fun validate(form: PollForm, today: LocalDate): Set<PollFormError> = buildSet {
    val question = form.question.trim()
    if (question.isEmpty()) add(PollFormError.QuestionMissing)
    if (question.length > Poll.MAX_QUESTION) add(PollFormError.QuestionTooLong)
    if (form.options().size < Poll.MIN_OPTIONS) add(PollFormError.TooFewOptions)
    if (form.kind == Poll.Kind.Choice && form.choices.any { it.trim().length > Poll.MAX_OPTION }) add(PollFormError.OptionTooLong)
    if (form.kind == Poll.Kind.Date && form.dates.any { it.day != null && it.day < today }) add(PollFormError.DatePast)
    if (form.askedIds.isEmpty()) add(PollFormError.NobodyAsked)
    if (form.closesOn != null && form.closesOn < today) add(PollFormError.ClosesPast)
}
