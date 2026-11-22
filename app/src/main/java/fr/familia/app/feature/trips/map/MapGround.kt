package fr.familia.app.feature.trips.map

/** What lies under the points of the map of a trip. */
enum class MapGround { Landscape, Streets }

/**
 * Which ground the map takes (PRODUIT-147). The streets only when the module is on for this member,
 * the public token is in this build (only « APK de publication » has it) and the phone has a
 * network; in every other case the map drawn on the landscape of the trip stays, with no message.
 */
fun mapGround(streetsOn: Boolean, token: String, hasNetwork: Boolean): MapGround =
    if (streetsOn && token.isNotBlank() && hasNetwork) MapGround.Streets else MapGround.Landscape
