package fr.familia.app.feature.trips.step

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripArrival
import fr.familia.app.core.model.TripStep
import java.time.LocalDate
import java.time.LocalTime

/** What is being typed in the step form (PRD, récit 11). */
data class StepForm(
    val type: StepType = StepType.Flight,
    val place: String = "",
    val departDate: LocalDate? = null,
    val departTime: LocalTime? = null,
    val arriveDate: LocalDate? = null,
    val arriveTime: LocalTime? = null,
    val note: String = "",
) {
    /** The step to save; [arrival] is kept as it is, the form never changes it. */
    fun toStep(id: String, arrival: TripArrival?): TripStep = TripStep(
        id = id,
        type = type,
        place = if (type in TRANSPORT_TYPES) routePlace(place) else placeName(place),
        departDate = requireNotNull(departDate),
        departTime = departTime,
        // An arrival time alone is on the day of departure.
        arriveDate = arriveDate ?: departDate.takeIf { arriveTime != null },
        arriveTime = arriveTime,
        note = note.trim().ifEmpty { null },
        arrival = arrival,
    )

    companion object {
        fun from(step: TripStep) = StepForm(
            type = step.type,
            place = step.place,
            departDate = step.departDate,
            departTime = step.departTime,
            arriveDate = step.arriveDate,
            arriveTime = step.arriveTime,
            note = step.note.orEmpty(),
        )
    }
}

enum class StepFormError { PlaceMissing, PlaceTooLong, DepartMissing, ArriveBeforeDepart, NoteTooLong }

/** Between the two towns of a transport, as the place was always written (E-64): « Paris → Bordeaux ». */
const val ROUTE_ARROW = " → "

/**
 * The two towns of a transport kept in [place] (E-64): « Départ de » and « Arrivée à ». The text is
 * cut as typed, spaces kept, so a town is typed letter by letter; an old place with no arrow is the
 * departure, « Paris->Bordeaux » or « Paris→Bordeaux » are cut too.
 */
fun routeParts(place: String): Pair<String, String> {
    val exact = place.indexOf(ROUTE_ARROW)
    if (exact >= 0) return place.substring(0, exact) to place.substring(exact + ROUTE_ARROW.length)
    val loose = Regex("\\s*(→|->)\\s*").find(place) ?: return place to ""
    return place.substring(0, loose.range.first) to place.substring(loose.range.last + 1)
}

/** The place of a transport from its two fields, as typed; empty when both are. */
fun routeOf(from: String, to: String): String = if (from.isEmpty() && to.isEmpty()) "" else from + ROUTE_ARROW + to

/** The place saved for a transport: each town written as the family reads it, « Paris → Bordeaux ». */
fun routePlace(place: String): String {
    val (from, to) = routeParts(place)
    return listOf(placeName(from), placeName(to)).filter { it.isNotEmpty() }.joinToString(ROUTE_ARROW)
}

const val MAX_STEP_PLACE = 120
const val MAX_STEP_NOTE = 1000

/** Same limits as the security rules. */
fun validate(form: StepForm): Set<StepFormError> = buildSet {
    val place = form.place.trim()
    // E-64: a transport has two towns; one of them is enough to save, as an old step with one place.
    val typed = if (form.type in TRANSPORT_TYPES) routeParts(form.place).toList().joinToString("").trim() else place
    if (typed.isEmpty()) add(StepFormError.PlaceMissing)
    if (place.length > MAX_STEP_PLACE) add(StepFormError.PlaceTooLong)
    if (form.departDate == null) add(StepFormError.DepartMissing)
    val depart = form.departDate
    val arrive = form.arriveDate ?: depart
    if (depart != null && arrive != null) {
        val sameDayEarlier = arrive == depart && form.departTime != null && form.arriveTime != null && form.arriveTime < form.departTime
        if (arrive < depart || sameDayEarlier) add(StepFormError.ArriveBeforeDepart)
    }
    if (form.note.trim().length > MAX_STEP_NOTE) add(StepFormError.NoteTooLong)
}
