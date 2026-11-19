package fr.familia.app.feature.trips

import kotlinx.serialization.Serializable

/*
 * Routes of the trip screens (PRD, V3). Trips have no tab (carte DESIGN-4): they open from the
 * planning, and later from the « En voyage » card of the Accueil tab, with a back arrow.
 */

/** The trips the member follows, with « Nouveau voyage ». */
@Serializable
data object TripsDestination

/**
 * One trip: its steps and « bien arrivé ». [open]: the part to show first, the name of a
 * TripPart (« Étapes » or « À prévoir » from the list, récit 80); null: the top.
 */
@Serializable
data class TripDetailDestination(val tripId: String, val open: String? = null)

/** Trip form: a new trip when [tripId] is null. */
@Serializable
data class TripEditDestination(val tripId: String? = null)

/** Step form: a new step of [tripId] when [stepId] is null. */
@Serializable
data class TripStepEditDestination(val tripId: String, val stepId: String? = null)

/** All the photos of [tripId] (récit 15). */
@Serializable
data class TripPhotosDestination(val tripId: String, val photoId: String? = null)
