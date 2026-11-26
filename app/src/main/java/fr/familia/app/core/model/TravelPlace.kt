package fr.familia.app.core.model

import java.time.LocalDate

/**
 * A destination added by hand to « Carnet de baroudeur » (récit 107, règle 38): a trip already done
 * outside the app, kept by its member only. No step, no kilometre, no photo: a simple mark on the
 * planisphere and one more country in the carnet. [date] is the day it started, always in the past;
 * [days] how long it lasted. [countryCode] (ISO 3166, two letters) is null when the phone could not
 * tell the country.
 */
data class TravelPlace(
    val id: String,
    val name: String,
    val country: String?,
    val countryCode: String?,
    val lat: Double,
    val lng: Double,
    val date: LocalDate,
    val days: Int,
    /**
     * « Ajouter un voyage » (retour de l'admin du 26/11): the places of one trip added together share
     * [tripId] (the id of its first place) and its optional [tripName]. Null for a place added alone
     * before: those whose days follow each other are read as one trip ([fr.familia.app.feature.trips.carnet.manualGroups]).
     */
    val tripId: String? = null,
    val tripName: String? = null,
) {
    companion object {
        const val MAX_NAME = 80
        const val MAX_COUNTRY = 60
        const val MAX_DAYS = 365
    }
}

/** Why a hand-added destination cannot be saved, in the order the sheet asks for them. */
enum class TravelPlaceProblem { NoPlace, NoDate, DateNotPast, Days }

/**
 * What stops the sheet « Ajouter une destination » (règle 38): a place must be chosen, the date
 * must be before [today], and the trip lasts 1 to [TravelPlace.MAX_DAYS] days. Null when it can be saved.
 */
fun travelPlaceProblem(hasPlace: Boolean, date: LocalDate?, days: Int?, today: LocalDate): TravelPlaceProblem? = when {
    !hasPlace -> TravelPlaceProblem.NoPlace
    date == null -> TravelPlaceProblem.NoDate
    !date.isBefore(today) -> TravelPlaceProblem.DateNotPast
    days == null || days < 1 || days > TravelPlace.MAX_DAYS -> TravelPlaceProblem.Days
    else -> null
}
