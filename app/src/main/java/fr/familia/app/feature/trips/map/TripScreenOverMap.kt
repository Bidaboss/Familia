package fr.familia.app.feature.trips.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.BuildConfig
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/**
 * A screen of the trip [tripId] in the sheet under its map (récit 111, règle 8: « tous les écrans
 * doivent pouvoir se tirer vers le haut »): Valise, Documents, a place, the word of a step… pull up
 * to the whole screen and go down again like the home of the trip. [focusId]: the step to choose
 * on the map. Without the map (TalkBack, no network, no step placed), [content] alone, as before.
 */
@Composable
fun TripScreenOverMap(container: AppContainer?, tripId: String, focusId: String? = null, content: @Composable () -> Unit) {
    val repository = container?.tripRepository
    val trip by remember(repository, tripId) {
        repository?.observeTrip(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf<Trip?>(null)
    }.collectAsStateWithLifecycle(null)
    val steps by remember(repository, tripId) {
        repository?.observeSteps(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList<TripStep>())
    }.collectAsStateWithLifecycle(emptyList())
    TripMapBackdrop(token = BuildConfig.MAPBOX_PUBLIC_TOKEN, trip = trip, steps = steps, focusId = focusId) { content() }
}
