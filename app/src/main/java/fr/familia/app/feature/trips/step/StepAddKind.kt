package fr.familia.app.feature.trips.step

import fr.familia.app.core.model.StepType

/** What a « + » of « Le trajet » adds (DESIGN-99 A): a destination (a stay), or a transport. */
enum class StepAddKind { Destination, Transport }

/** The ways to travel between two destinations. */
val TRANSPORT_TYPES: List<StepType> = listOf(StepType.Flight, StepType.Train, StepType.Bus, StepType.Car)

/**
 * The types the form offers (E-17: no « Quel type d'étape ? » where the « + » already said it).
 * A destination has none to choose; a transport chooses its way; an old « Activité » can still
 * become a destination; a form opened without a kind keeps all of them, as before.
 */
fun stepTypeOptions(kind: StepAddKind?, existing: StepType?): List<StepType> = when {
    existing == StepType.Lodging -> emptyList()
    existing in TRANSPORT_TYPES -> TRANSPORT_TYPES
    existing != null -> StepType.entries
    kind == StepAddKind.Destination -> emptyList()
    kind == StepAddKind.Transport -> TRANSPORT_TYPES
    else -> StepType.entries
}

/** The type a new step starts with. */
fun presetStepType(kind: StepAddKind?): StepType? = when (kind) {
    StepAddKind.Destination -> StepType.Lodging
    StepAddKind.Transport -> StepType.Car
    null -> null
}
