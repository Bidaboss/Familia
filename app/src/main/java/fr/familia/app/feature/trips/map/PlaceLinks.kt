package fr.familia.app.feature.trips.map

import java.net.URLEncoder

/** The kind of idea a link opens: where to eat, where to sleep, what Tripadvisor says. */
enum class PlaceLinkKind { Restaurants, Hotels, Tripadvisor }

data class PlaceLink(val kind: PlaceLinkKind, val url: String)

/**
 * The links « Restaurants près de… », « Hôtels à… » and « Voir sur Tripadvisor » for a [place]
 * (récit 96, PRODUIT-149). Nothing is read, sent or kept by Familia: a link only opens Google Maps
 * (no key needed, `api=1`) or the Tripadvisor search in the phone, with the name of the place and
 * nothing else (never the name of a member or the title of the trip). Null for a blank place.
 */
fun placeLinks(place: String): List<PlaceLink>? {
    val name = place.trim().take(MAX_PLACE).takeIf { it.isNotEmpty() } ?: return null
    return listOf(
        PlaceLink(PlaceLinkKind.Restaurants, "https://www.google.com/maps/search/?api=1&query=${encode("restaurants $name")}"),
        PlaceLink(PlaceLinkKind.Hotels, "https://www.google.com/maps/search/?api=1&query=${encode("hôtels $name")}"),
        PlaceLink(PlaceLinkKind.Tripadvisor, "https://www.tripadvisor.fr/Search?q=${encode(name)}"),
    )
}

/** A place longer than that is cut: Google Maps links stop at 2 048 characters. */
private const val MAX_PLACE = 120

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
