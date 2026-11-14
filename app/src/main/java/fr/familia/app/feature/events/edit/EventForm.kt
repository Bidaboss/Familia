package fr.familia.app.feature.events.edit

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import java.time.LocalDate
import java.time.LocalTime

/** What is being typed in the event form. */
data class EventForm(
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val place: String = "",
    val note: String = "",
    val target: EventTarget? = null,
) {
    fun toEvent(id: String, authorId: String, authorName: String): Event = Event(
        id = id,
        title = title.trim(),
        date = requireNotNull(date),
        time = time,
        place = place.trim().ifEmpty { null },
        note = note.trim().ifEmpty { null },
        target = requireNotNull(target),
        authorId = authorId,
        authorName = authorName,
    )

    companion object {
        fun from(event: Event) = EventForm(
            title = event.title,
            date = event.date,
            time = event.time,
            place = event.place.orEmpty(),
            note = event.note.orEmpty(),
            target = event.target,
        )
    }
}

enum class EventFormError { TitleMissing, TitleTooLong, DateMissing, PlaceTooLong, NoteTooLong, TargetMissing }

const val MAX_TITLE = 80
const val MAX_PLACE = 120
const val MAX_NOTE = 1000

/** Same limits as the security rules; "a title or a date missing prevents saving" (PRD, récit 2). */
fun validate(form: EventForm, allowedTargets: List<EventTarget>): Set<EventFormError> = buildSet {
    val title = form.title.trim()
    if (title.isEmpty()) add(EventFormError.TitleMissing)
    if (title.length > MAX_TITLE) add(EventFormError.TitleTooLong)
    if (form.date == null) add(EventFormError.DateMissing)
    if (form.place.trim().length > MAX_PLACE) add(EventFormError.PlaceTooLong)
    if (form.note.trim().length > MAX_NOTE) add(EventFormError.NoteTooLong)
    if (form.target == null || form.target !in allowedTargets) add(EventFormError.TargetMissing)
}
