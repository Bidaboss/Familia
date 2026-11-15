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
        place = place.trim(),
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

const val MAX_STEP_PLACE = 120
const val MAX_STEP_NOTE = 1000

/** Same limits as the security rules. */
fun validate(form: StepForm): Set<StepFormError> = buildSet {
    val place = form.place.trim()
    if (place.isEmpty()) add(StepFormError.PlaceMissing)
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
