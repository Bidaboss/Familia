package fr.familia.app.core.money

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** How many units of each currency make 1 €, as published on [date]. */
data class RatesTable(val rates: Map<String, Double>, val date: LocalDate) {
    /** 1 € in [code]; null when the source has no rate for it. The euro is always 1. */
    fun perEuro(code: String): Double? = if (code == "EUR") 1.0 else rates[code]?.takeIf { it > 0 }
}

/**
 * The exchange rates of « Monnaie » (récit 105, règle 42). Never an invented rate: a real one with
 * its date, or none. Only the euro is asked for: nothing about the trip leaves the phone.
 */
interface ExchangeRates {
    /** The latest rates, from the network or the last ones kept; null when there are none at all. */
    suspend fun latest(): RatesTable?
}

/**
 * ExchangeRate-API, open access (open.er-api.com): free, no key, one update a day, about 160
 * currencies; the screen credits it, as its terms ask. The answer is kept on the phone, so the
 * rate of the day stays readable without network, with its date.
 */
class OpenExchangeRates(private val prefs: SharedPreferences, private val now: () -> Instant = Instant::now) : ExchangeRates {
    @Volatile private var memory: RatesTable? = null

    override suspend fun latest(): RatesTable? {
        val kept = memory ?: prefs.getString(KEY_JSON, null)?.let(::parseRates)?.also { memory = it }
        val fetchedAt = prefs.getLong(KEY_FETCHED, 0L)
        if (kept != null && now().toEpochMilli() - fetchedAt < FRESH_MS) return kept
        val fresh = download() ?: return kept
        memory = fresh.second
        prefs.edit().putString(KEY_JSON, fresh.first).putLong(KEY_FETCHED, now().toEpochMilli()).apply()
        return fresh.second
    }

    private suspend fun download(): Pair<String, RatesTable>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                val connection = URL(URL_EURO).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode != 200) return@runCatching null
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    if (text.length > MAX_BYTES) return@runCatching null
                    parseRates(text)?.let { text to it }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    companion object {
        const val SOURCE_NAME = "ExchangeRate-API"
        const val SOURCE_URL = "https://www.exchangerate-api.com"
        private const val URL_EURO = "https://open.er-api.com/v6/latest/EUR"
        private const val USER_AGENT = "Familia/1.0 (application familiale)"
        private const val KEY_JSON = "rates-json"
        private const val KEY_FETCHED = "rates-fetched-at"
        private const val FRESH_MS = 6 * 60 * 60 * 1000L
        private const val TIMEOUT_MS = 8_000L
        private const val MAX_BYTES = 64 * 1024
    }
}

/**
 * Reads an answer of open.er-api.com: « result » success, base EUR, the date of the last update
 * and the rates. Null for anything else, so a strange answer never shows a rate.
 */
fun parseRates(json: String): RatesTable? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    if (root["result"]?.jsonPrimitive?.contentOrNull != "success") return@runCatching null
    if (root["base_code"]?.jsonPrimitive?.contentOrNull != "EUR") return@runCatching null
    val seconds = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull ?: return@runCatching null
    val rates = root["rates"]?.jsonObject ?: return@runCatching null
    val wanted = Currencies.ALL.map { it.code }.toSet()
    val table = rates.entries
        .filter { it.key in wanted }
        .mapNotNull { (code, value) -> value.jsonPrimitive.doubleOrNull?.takeIf { it > 0 && it.isFinite() }?.let { code to it } }
        .toMap()
    if (table.isEmpty()) return@runCatching null
    RatesTable(table, Instant.ofEpochSecond(seconds).atOffset(ZoneOffset.UTC).toLocalDate())
}.getOrNull()
