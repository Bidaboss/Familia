package fr.familia.app.core.money

import java.text.Normalizer
import java.util.Currency
import java.util.Locale

/** A currency a trip can use, with its French name. */
data class TripCurrency(val code: String, val name: String)

/**
 * The currencies of a trip (récit 105, règle 42): the euro and 42 others, searched by name or code.
 * The kuna of the prototype is left out: Croatia has used the euro since 2023.
 */
object Currencies {
    val EURO = TripCurrency("EUR", "Euro")

    val ALL: List<TripCurrency> = listOf(
        EURO,
        TripCurrency("MAD", "Dirham marocain"),
        TripCurrency("USD", "Dollar américain"),
        TripCurrency("GBP", "Livre sterling"),
        TripCurrency("JPY", "Yen"),
        TripCurrency("TND", "Dinar tunisien"),
        TripCurrency("CHF", "Franc suisse"),
        TripCurrency("CAD", "Dollar canadien"),
        TripCurrency("AUD", "Dollar australien"),
        TripCurrency("NZD", "Dollar néo-zélandais"),
        TripCurrency("DZD", "Dinar algérien"),
        TripCurrency("XOF", "Franc CFA (Afrique de l'Ouest)"),
        TripCurrency("XAF", "Franc CFA (Afrique centrale)"),
        TripCurrency("EGP", "Livre égyptienne"),
        TripCurrency("TRY", "Livre turque"),
        TripCurrency("ISK", "Couronne islandaise"),
        TripCurrency("NOK", "Couronne norvégienne"),
        TripCurrency("SEK", "Couronne suédoise"),
        TripCurrency("DKK", "Couronne danoise"),
        TripCurrency("PLN", "Zloty polonais"),
        TripCurrency("CZK", "Couronne tchèque"),
        TripCurrency("HUF", "Forint hongrois"),
        TripCurrency("RON", "Leu roumain"),
        TripCurrency("MXN", "Peso mexicain"),
        TripCurrency("BRL", "Réal brésilien"),
        TripCurrency("ARS", "Peso argentin"),
        TripCurrency("CLP", "Peso chilien"),
        TripCurrency("COP", "Peso colombien"),
        TripCurrency("PEN", "Sol péruvien"),
        TripCurrency("DOP", "Peso dominicain"),
        TripCurrency("THB", "Baht thaïlandais"),
        TripCurrency("VND", "Dong vietnamien"),
        TripCurrency("IDR", "Roupie indonésienne"),
        TripCurrency("INR", "Roupie indienne"),
        TripCurrency("CNY", "Yuan chinois"),
        TripCurrency("KRW", "Won sud-coréen"),
        TripCurrency("SGD", "Dollar de Singapour"),
        TripCurrency("AED", "Dirham des Émirats"),
        TripCurrency("ILS", "Shekel israélien"),
        TripCurrency("ZAR", "Rand sud-africain"),
        TripCurrency("KES", "Shilling kényan"),
        TripCurrency("MUR", "Roupie mauricienne"),
        TripCurrency("XPF", "Franc Pacifique (CFP)"),
    )

    private val byCode = ALL.associateBy { it.code }

    /** The currency of [code], the euro for null or a code this version does not know. */
    fun of(code: String?): TripCurrency = code?.let { byCode[it.uppercase(Locale.ROOT)] } ?: EURO

    /** By code (« thb ») or by a piece of the name, without accents (« dollar », « couronne »). */
    fun search(query: String): List<TripCurrency> {
        val q = fold(query)
        if (q.isEmpty()) return ALL
        return ALL.filter { it.code.lowercase(Locale.ROOT).startsWith(q) || fold(it.name).contains(q) }
    }

    /** The currencies of the countries of a trip (France and the euro left out), to offer first. */
    fun ofCountries(countryCodes: List<String>): List<TripCurrency> = countryCodes.mapNotNull { country ->
        runCatching { Currency.getInstance(Locale("", country.uppercase(Locale.ROOT))).currencyCode }.getOrNull()
    }.distinct().mapNotNull { byCode[it] }.filter { it != EURO }

    private fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.FRENCH), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
