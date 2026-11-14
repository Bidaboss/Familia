package fr.familia.app.core.model

import java.time.LocalDate
import java.time.LocalTime

/** Who an event is for (PRD, récit 2). Stored on the event so the security rules can check it. */
sealed interface EventTarget {
    data class ForSelf(val memberId: String) : EventTarget
    data class ForChild(val memberId: String) : EventTarget
    data class ForHousehold(val householdId: String) : EventTarget
    data object ForFamily : EventTarget
}

/**
 * One event of the planning. [authorName] is the author's first name when the event was created,
 * shown only once the author has been removed from the family.
 */
data class Event(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val place: String?,
    val note: String?,
    val target: EventTarget,
    val authorId: String,
    val authorName: String,
)
