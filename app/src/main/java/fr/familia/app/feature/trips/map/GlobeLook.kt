package fr.familia.app.feature.trips.map

import com.mapbox.maps.Style

/**
 * How a globe looks (retour E-48): the imagery and the sky around it. The admin compared three
 * variants on « Voyages » and kept C (25/11, 7 h 50); A, B and the long touch that went from one
 * to the other are gone.
 */
enum class GlobeLook(
    /** The letter of the variant, as the admin chose it: « C ». */
    val letter: String,
    val styleUri: String,
    /** The halo near the ground, the halo high up, the sky. */
    val color: String,
    val highColor: String,
    val spaceColor: String,
    val horizonBlend: Double,
    val starIntensity: Double,
    /** How big the globe of the list is: 1.1 fills about 85 % of the width of a phone. */
    val listZoom: Double,
) {
    /** Standard Satellite: the globe of « Voyages » chosen by the admin (25/11, 7 h 50, « Ok globe C »), and of the other globes. */
    Standard("C", Style.STANDARD_SATELLITE, "#BFD9F2", "#3C6FB5", "#050B1A", 0.04, 0.7, 1.0),
    ;

    companion object {
        /** The globe of the list: C, the one kept by the admin; A and B are gone. */
        val LIST_DEFAULT = Standard

        /** Pure rule: the variant kept under [name], [LIST_DEFAULT] when it is unknown. */
        fun of(name: String?): GlobeLook = entries.firstOrNull { it.name == name } ?: LIST_DEFAULT
    }
}

