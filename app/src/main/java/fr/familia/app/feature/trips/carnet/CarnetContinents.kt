package fr.familia.app.feature.trips.carnet

import java.util.Locale

/** The five continents of the carnet (récit 107), as the prototype counts them: Turkey and Cyprus in Asia. */
enum class Continent { Europe, Africa, America, Asia, Oceania }

private val CONTINENT_CODES: Map<Continent, String> = mapOf(
    Continent.Europe to "AD AL AT AX BA BE BG BY CH CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT JE LI LT LU LV MC MD " +
        "ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA XK",
    Continent.Africa to "AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG EH ER ET GA GH GM GN GQ GW KE KM LR LS LY MA MG ML MR MU MW " +
        "MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TG TN TZ UG YT ZA ZM ZW",
    Continent.America to "AG AI AR AW BB BL BM BO BQ BR BS BZ CA CL CO CR CU CW DM DO EC FK GD GF GL GP GT GY HN HT JM KN KY " +
        "LC MF MQ MS MX NI PA PE PM PR PY SR SV SX TC TT US UY VC VE VG VI",
    Continent.Asia to "AE AF AM AZ BD BH BN BT CN CY GE HK ID IL IN IQ IR JO JP KG KH KP KR KW KZ LA LB LK MM MN MO MV MY NP " +
        "OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE",
    Continent.Oceania to "AS AU CK FJ FM GU KI MH MP NC NF NR NU NZ PF PG PN PW SB TK TO TV VU WF WS",
)

private val CONTINENT_OF: Map<String, Continent> = CONTINENT_CODES.flatMap { (continent, codes) -> codes.split(' ').map { it to continent } }.toMap()

/** The continent of the country [code] (ISO 3166, two letters); null for one the carnet does not place (Antarctica). */
fun continentOf(code: String): Continent? = CONTINENT_OF[code.uppercase(Locale.ROOT)]

/** The flag of [code] as an emoji (two regional letters); empty for a code that is not two letters. */
fun flagOf(code: String): String {
    val upper = code.uppercase(Locale.ROOT)
    if (upper.length != 2 || upper.any { it !in 'A'..'Z' }) return ""
    return upper.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** The name of the country [code] in French (« Portugal »). */
fun countryName(code: String): String =
    Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale.FRENCH).ifBlank { code.uppercase(Locale.ROOT) }

/** The country of the family: always in the passport, « chez toi ». */
const val HOME_COUNTRY = "FR"
