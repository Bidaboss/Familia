package fr.familia.app.feature.trips.phrases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.TripStep
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The countries of the steps of a trip that have « Phrases utiles » (récit 105), found by the
 * geocoder of the phone from the point each step already has (nothing new is stored). A step the
 * phone cannot place, or that is in France, counts for nothing. Remembered for the life of the app so
 * the trip and the phrases screen do not ask twice.
 */
object TripCountries {
    private val known = ConcurrentHashMap<String, String>()

    private fun keyOf(lat: Double, lng: Double): String = "${(lat * 10).roundToInt()}/${(lng * 10).roundToInt()}"

    /** The country codes abroad, in the order of the steps, each once. */
    suspend fun abroad(steps: List<TripStep>, countryOf: suspend (Double, Double) -> String?): List<String> {
        val codes = steps.mapNotNull { step ->
            val lat = step.lat ?: return@mapNotNull null
            val lng = step.lng ?: return@mapNotNull null
            val key = keyOf(lat, lng)
            known[key] ?: countryOf(lat, lng)?.also { known[key] = it }
        }
        return PhraseBook.abroad(codes)
    }
}

/** The countries abroad of [steps] with a table of phrases; empty while they are looked up or when none. */
@Composable
fun rememberAbroadCountries(container: AppContainer?, steps: List<TripStep>): List<String> {
    val locator = container?.placeLocator
    var state by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(steps, locator) {
        state = if (locator == null) emptyList() else TripCountries.abroad(steps, locator::countryCodeOf)
    }
    return state
}
