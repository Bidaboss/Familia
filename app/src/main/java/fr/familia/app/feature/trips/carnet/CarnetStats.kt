package fr.familia.app.feature.trips.carnet

import fr.familia.app.feature.trips.detail.flightKm
import java.time.LocalDate
import kotlin.math.roundToInt

/** One place of a finished trip or of a hand-added destination, with its country when the phone found it. */
data class CarnetStop(val name: String, val lat: Double, val lng: Double, val countryCode: String?)

/**
 * One finished trip as the carnet counts it (récit 107, règle 37): its days, its kilometres as the
 * crow flies between the placed steps, its photos. A hand-added destination ([manual]) has no
 * kilometre and no photo (règle 38).
 */
data class CarnetTrip(
    val start: LocalDate,
    val days: Int,
    val stops: List<CarnetStop>,
    val photos: Int = 0,
    val manual: Boolean = false,
) {
    val km: Int
        get() = if (manual) 0 else stops.zipWithNext { a, b -> flightKm(a.lat, a.lng, b.lat, b.lng) }.sum().roundToInt()
}

/** The four ranks of the prototype, by countries: 0, 3, 6, then 12. */
enum class CarnetRank(val countries: Int) { Hiker(0), Explorer(3), GlobeTrotter(6), GreatTraveller(12) }

/** The six trophies of the carnet, in their order on screen. */
enum class Trophy { FirstTrip, ThreeCountries, TwoContinents, FiveCountries, TenThousandKm, AroundTheWorld }

/** One stamp of the passport: a country and the month of the first trip there; null for the home country. */
data class Stamp(val code: String, val first: LocalDate?)

/** The farthest place from home, as the crow flies. */
data class Farthest(val name: String, val km: Int)

/** Everything « Carnet de baroudeur » shows, counted on the phone; nothing is stored. */
data class Carnet(
    val stamps: List<Stamp>,
    val rank: CarnetRank,
    val next: CarnetRank?,
    val continents: Set<Continent>,
    val days: Int,
    val km: Int,
    val trips: Int,
    val photos: Int,
    val destinations: Int,
    val farthest: Farthest?,
    val trophies: List<Pair<Trophy, Boolean>>,
    /** The points of the planisphere: those of the trips, then the hand-added ones (a simple mark). */
    val tripPoints: List<Pair<Double, Double>>,
    val manualPoints: List<Pair<Double, Double>>,
) {
    val countries: Int get() = stamps.size

    /** Out of 195 countries, rounded. */
    val worldPercent: Int get() = (countries * 100.0 / WORLD_COUNTRIES).roundToInt()

    /** How many times around the Earth, as « 0,13 ». */
    val laps: Double get() = km / EARTH_KM
}

const val WORLD_COUNTRIES = 195
const val EARTH_KM = 40_075.0

/** The carnet of [trips] (finished ones and hand-added destinations); [home] is the point of the household, when known. */
fun carnetOf(trips: List<CarnetTrip>, home: Pair<Double, Double>?): Carnet {
    val firstByCountry = linkedMapOf<String, LocalDate?>(HOME_COUNTRY to null)
    trips.sortedBy { it.start }.forEach { trip ->
        trip.stops.mapNotNull { it.countryCode?.uppercase() }.filter { it.length == 2 }.forEach { code ->
            if (code !in firstByCountry) firstByCountry[code] = trip.start
        }
    }
    val stamps = firstByCountry.map { (code, first) -> Stamp(code, first) }
    val count = stamps.size
    val rank = CarnetRank.entries.last { count >= it.countries }
    val next = CarnetRank.entries.firstOrNull { it.countries > count }
    val continents = stamps.mapNotNull { continentOf(it.code) }.toSet()
    val km = trips.sumOf { it.km }
    val farthest = home?.let { (lat, lng) ->
        trips.flatMap { it.stops }
            .map { it to flightKm(lat, lng, it.lat, it.lng).roundToInt() }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }
            ?.let { (stop, distance) -> Farthest(stop.name, distance) }
    }
    val trophies = listOf(
        Trophy.FirstTrip to trips.isNotEmpty(),
        Trophy.ThreeCountries to (count >= 3),
        Trophy.TwoContinents to (continents.size >= 2),
        Trophy.FiveCountries to (count >= 5),
        Trophy.TenThousandKm to (km >= 10_000),
        Trophy.AroundTheWorld to (km >= EARTH_KM),
    )
    return Carnet(
        stamps = stamps,
        rank = rank,
        next = next,
        continents = continents,
        days = trips.sumOf { it.days },
        km = km,
        trips = trips.size,
        photos = trips.sumOf { it.photos },
        destinations = trips.flatMap { it.stops }.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size,
        farthest = farthest,
        trophies = trophies,
        tripPoints = trips.filterNot { it.manual }.flatMap { it.stops }.map { it.lat to it.lng },
        manualPoints = trips.filter { it.manual }.flatMap { it.stops }.map { it.lat to it.lng },
    )
}
