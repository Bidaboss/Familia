package fr.familia.app.feature.trips.safety

import java.util.Locale

/** What a number is for, in French. */
enum class EmergencyKind(val label: String) {
    All("Urgences (tout)"),
    Police("Police"),
    Ambulance("Ambulance"),
    Fire("Pompiers"),
    AmbulanceAndFire("Ambulance et pompiers"),
    TouristPolice("Police touristique"),
}

data class EmergencyNumber(val kind: EmergencyKind, val number: String)

/**
 * The emergency numbers of the countries abroad (récit 105, règle 43): a fixed table in the app,
 * nothing asked to a server. France is left out on purpose (choix produit, règle 43). A
 * country that is not in the table shows nothing, rather than a number that may be wrong.
 */
object EmergencyNumbers {
    private fun eu(vararg more: EmergencyNumber): List<EmergencyNumber> = listOf(EmergencyNumber(EmergencyKind.All, "112")) + more

    private fun n(kind: EmergencyKind, number: String) = EmergencyNumber(kind, number)

    /** The countries where 112 is the only number to know (European number, answered in several languages). */
    private val ONLY_112 = setOf(
        "BG", "CY", "CZ", "DK", "EE", "ES", "FI", "HR", "HU", "IE", "IS", "LI", "LT", "LV", "MC", "AD", "MT",
        "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "SM", "VA", "TR",
    )

    private val TABLE: Map<String, List<EmergencyNumber>> = mapOf(
        "BE" to eu(n(EmergencyKind.Police, "101")),
        "DE" to eu(n(EmergencyKind.Police, "110")),
        "AT" to eu(n(EmergencyKind.Police, "133"), n(EmergencyKind.Ambulance, "144"), n(EmergencyKind.Fire, "122")),
        "CH" to eu(n(EmergencyKind.Police, "117"), n(EmergencyKind.Ambulance, "144"), n(EmergencyKind.Fire, "118")),
        "IT" to eu(n(EmergencyKind.Police, "113"), n(EmergencyKind.Ambulance, "118"), n(EmergencyKind.Fire, "115")),
        "LU" to eu(n(EmergencyKind.Police, "113")),
        "GR" to eu(n(EmergencyKind.Police, "100"), n(EmergencyKind.Ambulance, "166")),
        "GB" to listOf(n(EmergencyKind.All, "999"), n(EmergencyKind.All, "112")),
        "US" to listOf(n(EmergencyKind.All, "911")),
        "CA" to listOf(n(EmergencyKind.All, "911")),
        "MX" to listOf(n(EmergencyKind.All, "911")),
        "AU" to listOf(n(EmergencyKind.All, "000"), n(EmergencyKind.All, "112")),
        "NZ" to listOf(n(EmergencyKind.All, "111")),
        "MA" to listOf(n(EmergencyKind.Police, "19"), n(EmergencyKind.AmbulanceAndFire, "15")),
        "TN" to listOf(n(EmergencyKind.Police, "197"), n(EmergencyKind.Ambulance, "190"), n(EmergencyKind.Fire, "198")),
        "JP" to listOf(n(EmergencyKind.Police, "110"), n(EmergencyKind.AmbulanceAndFire, "119")),
        "TH" to listOf(n(EmergencyKind.TouristPolice, "1155"), n(EmergencyKind.Police, "191"), n(EmergencyKind.Ambulance, "1669")),
        "BR" to listOf(n(EmergencyKind.Police, "190"), n(EmergencyKind.Ambulance, "192"), n(EmergencyKind.Fire, "193")),
    ) + ONLY_112.associateWith { eu() }

    /** The numbers of [countryCode], or an empty list for France or a country not in the table. */
    fun of(countryCode: String?): List<EmergencyNumber> {
        val code = countryCode?.uppercase(Locale.ROOT) ?: return emptyList()
        if (code == "FR") return emptyList()
        return TABLE[code].orEmpty()
    }

    /** The countries of [codes] that have numbers, in order, each once. */
    fun abroad(codes: List<String>): List<String> = codes.map { it.uppercase(Locale.ROOT) }.distinct().filter { of(it).isNotEmpty() }
}
