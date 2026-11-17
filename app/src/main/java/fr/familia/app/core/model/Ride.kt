package fr.familia.app.core.model

import java.time.LocalTime

/**
 * « On y va ensemble » (PRD, récit 52): the car of [driverId] for an event, with its [seats], a town
 * and a time to leave (both optional, never a position), and the members in it.
 */
data class Ride(
    val driverId: String,
    val seats: Int,
    val from: String?,
    val leaveAt: LocalTime?,
    val passengers: List<String>,
) {
    val seatsLeft: Int get() = (seats - passengers.size).coerceAtLeast(0)

    companion object {
        const val MIN_SEATS = 1
        const val MAX_SEATS = 8
        const val MAX_FROM = 40
    }
}

/** « J'ai besoin d'une place » for [memberId], asked by [askedBy] (themselves or a responsible adult). */
data class RideNeed(val memberId: String, val askedBy: String)
