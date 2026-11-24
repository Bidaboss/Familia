package fr.familia.app.feature.trips.now

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.detail.placeName
import fr.familia.app.feature.trips.detail.tripTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate

/**
 * The card of the Accueil the day before a trip and on its first day (récit 109): « Demain, départ
 * pour La Palmyre », with « Valise » and « Voir le trajet ». Only for those who go: the others
 * keep « En ce moment » once the trip has started. No notification goes with it.
 */
data class TripDeparture(
    val tripId: String,
    val title: String,
    /** The first destination of the trip; null before one is placed (the title is said instead). */
    val place: String?,
    /** true the day before, false on the day itself. */
    val tomorrow: Boolean,
)

/**
 * The trip [memberId] goes on tomorrow or today, or null. With several, the one that leaves first,
 * then the first by name, so the card never jumps from one to the other.
 */
fun tripDeparture(trips: List<Trip>, memberId: String, today: LocalDate): TripDeparture? {
    val trip = trips.filter { memberId in it.participantIds && (it.startDate == today || it.startDate == today.plusDays(1)) }
        .minWithOrNull(compareBy<Trip>({ it.startDate }, { it.title.lowercase() }, { it.id }))
        ?: return null
    return TripDeparture(trip.id, trip.title, place = null, tomorrow = trip.startDate != today)
}

/** The first destination of the trip, named as « Le programme » names it (the part before the comma). */
fun firstDestination(trip: Trip, steps: List<TripStep>): String? =
    tripTimeline(trip, steps).destinations.firstOrNull()?.step?.let(::placeName)?.takeIf { it.isNotBlank() }

/** The departure of tomorrow or today, with its first destination, updated live; nothing on error. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeTripDeparture(trips: TripRepository, memberId: String, today: Flow<LocalDate> = currentDayFlow()): Flow<TripDeparture?> =
    combine(trips.observeFollowedTrips(memberId), today) { list, day ->
        tripDeparture(list, memberId, day)?.let { found -> found to list.first { it.id == found.tripId } }
    }
        .distinctUntilChanged()
        .flatMapLatest { pair ->
            if (pair == null) {
                flowOf(null)
            } else {
                val (departure, trip) = pair
                trips.observeSteps(trip.id)
                    .map { steps -> departure.copy(place = firstDestination(trip, steps)) }
                    .onStart { emit(departure) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(departure)
                    }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }

/** For the Accueil: the departure of tomorrow or today, or null when there is none or the trips are off. */
@Composable
fun rememberTripDeparture(container: AppContainer?, member: Member): TripDeparture? {
    val on = LocalFeatureFlags.current.isOn(Feature.Trips)
    val trips = container?.tripRepository
    val flow = remember(on, trips, member.id) {
        if (!on || trips == null) emptyFlow() else observeTripDeparture(trips, member.id)
    }
    val departure by flow.collectAsStateWithLifecycle(initialValue = null)
    return departure
}

/** The card: on the sky of the trips, the day said in big, then « Valise » and « Voir le trajet », 56 dp each. */
@Composable
fun TripDepartureCard(state: TripDeparture, onPacking: (() -> Unit)?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val where = state.place ?: state.title
    val headline = stringResource(if (state.tomorrow) R.string.departure_tomorrow else R.string.departure_today, where)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TripPathColors.Sky)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (state.tomorrow) R.string.departure_label_tomorrow else R.string.departure_label_today),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TripPathColors.InkSoft,
            )
            Text(
                headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TripPathColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            if (state.place != null) Text(state.title, fontSize = 15.sp, color = TripPathColors.InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // « Valise » only while packingList is on; « Voir le trajet » then takes the whole row.
            if (onPacking != null) FilledTonalButton(
                onClick = onPacking,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = TripPathColors.Ink, contentColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.departure_packing)) }
            OutlinedButton(
                onClick = onOpen,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = TripPathColors.Ink),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.departure_open)) }
        }
    }
}

@FamiliaPreviews
@Composable
private fun TripDepartureCardPreview() {
    FamiliaTheme {
        Surface {
            TripDepartureCard(TripDeparture("t1", "Vacances d'été", "La Palmyre", tomorrow = true), onPacking = {}, onOpen = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
