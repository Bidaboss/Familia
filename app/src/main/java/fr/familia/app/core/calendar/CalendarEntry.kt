package fr.familia.app.core.calendar

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import java.time.LocalDate
import java.time.ZoneId

/**
 * What « Ajouter à mon agenda » hands to the phone's calendar (PRD, récit 30): a copy, never kept
 * in sync. Times are those of Paris, the family's time zone. Pure, so it is tested on the JVM.
 */
data class CalendarEntry(
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    val description: String,
    /** RFC 5545 rule; a birthday comes back every year. */
    val rrule: String? = null,
) {
    companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /** An event without a time lasts the whole day; with one, an hour (the member adjusts it). */
        const val DEFAULT_MINUTES = 60L

        fun forEvent(event: Event, createdFrom: String): CalendarEntry {
            val note = listOfNotNull(event.note?.takeIf { it.isNotBlank() }, createdFrom).joinToString("\n\n")
            return if (event.time == null) {
                allDay(event.title, event.date, event.place, note)
            } else {
                val begin = event.date.atTime(event.time).atZone(PARIS)
                CalendarEntry(
                    title = event.title,
                    beginMillis = begin.toInstant().toEpochMilli(),
                    endMillis = begin.plusMinutes(DEFAULT_MINUTES).toInstant().toEpochMilli(),
                    allDay = false,
                    location = event.place?.takeIf { it.isNotBlank() },
                    description = note,
                )
            }
        }

        /** [title] is « Anniversaire de Camille »: without the age, since it comes back each year. */
        fun forBirthday(birthday: Birthday, title: String, createdFrom: String): CalendarEntry =
            allDay(title, birthday.date, null, createdFrom).copy(rrule = "FREQ=YEARLY")

        private fun allDay(title: String, day: LocalDate, place: String?, note: String) = CalendarEntry(
            title = title,
            beginMillis = day.atStartOfDay(PARIS).toInstant().toEpochMilli(),
            endMillis = day.plusDays(1).atStartOfDay(PARIS).toInstant().toEpochMilli(),
            allDay = true,
            location = place?.takeIf { it.isNotBlank() },
            description = note,
        )
    }
}
