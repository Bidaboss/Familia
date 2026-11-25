package fr.familia.app.feature.events.edit

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventSeries
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.model.RepeatEnd
import fr.familia.app.core.model.SeriesRule
import fr.familia.app.core.model.choiceOf
import fr.familia.app.core.model.ruleFor
import fr.familia.app.core.photo.PhotoEdit
import java.time.LocalDate
import java.time.LocalTime

/** What is being typed in the event form. */
data class EventForm(
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val place: String = "",
    val note: String = "",
    /** The members named « @Prénom » in [note] (l'admin, 19/11). */
    val mentionIds: List<String> = emptyList(),
    val target: EventTarget? = null,
    /** The cover (récit 44): sent by the ViewModel before the event, not kept in the draft. */
    val photo: PhotoEdit = PhotoEdit.Unchanged,
    /** « Se répète » (récit 54), and how it ends. */
    val repeat: RepeatChoice = RepeatChoice.None,
    val end: RepeatEnd = RepeatEnd.Never,
    /** « Où ? » (récit 74): [place] is the text of « Autre » or of « Adresse », or the household's name. */
    val placeKind: PlaceKind = PlaceKind.Other,
    val placeHouseholdId: String? = null,
) {
    /** Another choice of « Où ? »: the name of a household never stays as a text to edit. */
    fun withPlaceKind(kind: PlaceKind): EventForm = if (kind == placeKind) this else copy(
        placeKind = kind,
        placeHouseholdId = null,
        place = if (placeKind == PlaceKind.Household || kind == PlaceKind.Household) "" else place,
    )

    /** « Chez un foyer »: [household] chosen, its name is the text every screen shows. */
    fun atHousehold(household: Household): EventForm =
        copy(placeKind = PlaceKind.Household, placeHouseholdId = household.id, place = household.name)

    /** What is saved: no text, no place at all; a household is kept only with its id. */
    private val savedPlace: Triple<String?, PlaceKind, String?>
        get() {
            val text = place.trim().ifEmpty { null } ?: return Triple(null, PlaceKind.Other, null)
            return when {
                placeKind == PlaceKind.Household && placeHouseholdId == null -> Triple(text, PlaceKind.Other, null)
                placeKind == PlaceKind.Household -> Triple(text, PlaceKind.Household, placeHouseholdId)
                else -> Triple(text, placeKind, null)
            }
        }

    /** The rule the form describes, from its date; null when it does not repeat. */
    fun rule(): SeriesRule? = date?.let { ruleFor(repeat, end, it) }

    /** The series of the form, with [id], its author, [rule] and what [base] already holds. */
    fun toSeries(id: String, authorId: String, authorName: String, rule: SeriesRule, base: EventSeries? = null): EventSeries =
        EventSeries(
            id = id,
            title = title.trim(),
            time = time,
            place = savedPlace.first,
            note = note.trim().ifEmpty { null },
            target = requireNotNull(target),
            authorId = authorId,
            authorName = authorName,
            rule = rule,
            exdates = base?.exdates.orEmpty(),
            applyFrom = base?.applyFrom,
            placeKind = savedPlace.second,
            placeHouseholdId = savedPlace.third,
            mentionIds = savedMentions,
        )

    fun toEvent(id: String, authorId: String, authorName: String, coverPhoto: String? = null): Event = Event(
        id = id,
        title = title.trim(),
        date = requireNotNull(date),
        time = time,
        place = savedPlace.first,
        note = note.trim().ifEmpty { null },
        target = requireNotNull(target),
        authorId = authorId,
        authorName = authorName,
        coverPhoto = coverPhoto,
        placeKind = savedPlace.second,
        placeHouseholdId = savedPlace.third,
        mentionIds = savedMentions,
    )

    /** No note, nobody named. */
    private val savedMentions: List<String>
        get() = if (note.isBlank()) emptyList() else mentionIds.distinct()

    companion object {
        fun from(event: Event) = EventForm(
            title = event.title,
            date = event.date,
            time = event.time,
            place = event.place.orEmpty(),
            note = event.note.orEmpty(),
            mentionIds = event.mentionIds,
            target = event.target,
            placeKind = event.placeKind,
            placeHouseholdId = event.placeHouseholdId,
        )

        /** An occurrence of [series]: the form shows how the series repeats. */
        fun from(event: Event, series: EventSeries): EventForm {
            val (repeat, end) = choiceOf(series.rule)
            return from(event).copy(repeat = repeat, end = end)
        }
    }
}

enum class EventFormError {
    TitleMissing, TitleTooLong, DateMissing, PlaceTooLong, NoteTooLong, TargetMissing,
    /** « Se termine le » before the first day, or a number of times out of 2 to 200 (récit 54). */
    RepeatEndInvalid,
    /** A new event more than a year in the past: an anniversary typed on the birth date (retour d'essai 20/11). */
    DateTooOld,
}

const val MAX_TITLE = 80
const val MAX_PLACE = 120
const val MAX_NOTE = 1000

/** Same limits as the security rules; "a title or a date missing prevents saving" (PRD, récit 2). */
fun validate(form: EventForm, allowedTargets: List<EventTarget>, oldest: LocalDate? = null): Set<EventFormError> = buildSet {
    val title = form.title.trim()
    if (title.isEmpty()) add(EventFormError.TitleMissing)
    if (title.length > MAX_TITLE) add(EventFormError.TitleTooLong)
    if (form.date == null) add(EventFormError.DateMissing)
    if (form.date != null && oldest != null && form.date < oldest) add(EventFormError.DateTooOld)
    if (form.place.trim().length > MAX_PLACE) add(EventFormError.PlaceTooLong)
    if (form.note.trim().length > MAX_NOTE) add(EventFormError.NoteTooLong)
    if (form.target == null || form.target !in allowedTargets) add(EventFormError.TargetMissing)
    if (form.repeat != RepeatChoice.None) {
        val end = form.end
        val bad = when (end) {
            RepeatEnd.Never -> false
            is RepeatEnd.OnDay -> form.date != null && end.day < form.date
            is RepeatEnd.After -> end.times !in 2..EventSeries.MAX_COUNT
        }
        if (bad) add(EventFormError.RepeatEndInvalid)
    }
}
