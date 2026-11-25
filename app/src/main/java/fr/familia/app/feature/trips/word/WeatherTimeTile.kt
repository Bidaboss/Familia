package fr.familia.app.feature.trips.word

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.place.OpenMeteoWeather
import fr.familia.app.core.place.PlaceWeather
import fr.familia.app.core.place.WeatherNow
import fr.familia.app.core.place.weatherWords
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.feature.trips.TripTile
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * « Météo et heure » (récit 105, règle 45): one line in the panel of a destination, « 12 °C,
 * couvert · 23 h 40 sur place ». Only the point of the step is sent. Hidden without network.
 */
@Composable
fun WeatherTimeTile(lat: Double, lng: Double, weather: PlaceWeather?) {
    var now by remember(lat, lng) { mutableStateOf<WeatherNow?>(null) }
    LaunchedEffect(lat, lng, weather) {
        now = weather?.now(lat, lng)
    }
    val current = now ?: return
    // The local time moves on while the screen is open.
    var instant by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            instant = Instant.now()
        }
    }
    TripTile(Icons.Outlined.WbSunny, stringResource(R.string.weather_title)) {
        Text(weatherLine(current, instant), style = MaterialTheme.typography.bodyLarge)
        FormHint(stringResource(R.string.weather_source, OpenMeteoWeather.SOURCE))
    }
}

/** « 12 °C, couvert · 23 h 40 sur place », and the difference with France when there is one. */
internal fun weatherLine(weather: WeatherNow, at: Instant, home: ZoneId = ZoneId.of("Europe/Paris")): String {
    val time = at.atZone(weather.zone).format(DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRANCE))
    val gap = (weather.zone.rules.getOffset(at).totalSeconds - home.rules.getOffset(at).totalSeconds) / 3600.0
    val gapText = when {
        gap == 0.0 -> ""
        gap % 1.0 == 0.0 -> String.format(Locale.FRANCE, " (%+d h)", gap.roundToInt())
        else -> String.format(Locale.FRANCE, " (%+.1f h)", gap)
    }
    return "${weather.temperatureC.roundToInt()} °C, ${weatherWords(weather.code)} · $time sur place$gapText"
}
