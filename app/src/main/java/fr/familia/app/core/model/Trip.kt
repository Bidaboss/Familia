package fr.familia.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A trip (PRD, V3): a shared travel log. [participantIds] take part and fill in the steps;
 * [readerIds] follow it and are the only ones who can read it (récit 12). Participants always
 * follow it. [creatorName] keeps the trip readable once its creator has left the family.
 */
data class Trip(
    val id: String,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val participantIds: List<String>,
    val readerIds: List<String>,
    val creatorId: String,
    val creatorName: String,
)

/** The kinds of step of récit 11, in the order of the form. */
enum class StepType(val firestoreValue: String) {
    Flight("flight"),
    Train("train"),
    Car("car"),
    Lodging("lodging"),
    Activity("activity"),
    ;

    companion object {
        fun fromFirestore(value: String?): StepType? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** « Bien arrivé », said once by one participant for everyone of the trip (récit 14). */
data class TripArrival(val at: Instant?, val memberId: String, val name: String)

/**
 * One step of a trip: where, when it leaves and when it should arrive. No position is ever stored:
 * the family only knows what the participants type and the « bien arrivé » they tap.
 */
data class TripStep(
    val id: String,
    val type: StepType,
    val place: String,
    val departDate: LocalDate,
    val departTime: LocalTime?,
    val arriveDate: LocalDate?,
    val arriveTime: LocalTime?,
    val note: String?,
    val arrival: TripArrival? = null,
)
