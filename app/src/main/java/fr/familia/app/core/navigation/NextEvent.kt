package fr.familia.app.core.navigation

import fr.familia.app.core.data.eventOrder
import fr.familia.app.core.model.Event
import java.time.LocalDateTime

/**
 * « Voir où » of a module shown on an event (covoiturage, rappels, se répète, commentaires, qui
 * apporte quoi): the next event still to come (PRODUIT, 17/11). An event of today with no time
 * counts until the end of the day; one with a time that has passed does not. Null when nothing is
 * to come, and « Voir où » opens the Planning.
 */
internal fun nextEventId(events: List<Event>, now: LocalDateTime): String? = events
    .filter { event ->
        val today = now.toLocalDate()
        event.date.isAfter(today) || (event.date == today && (event.time == null || !event.time.isBefore(now.toLocalTime())))
    }
    .sortedWith(eventOrder)
    .firstOrNull()
    ?.id
