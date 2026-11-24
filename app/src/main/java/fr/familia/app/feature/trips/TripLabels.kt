package fr.familia.app.feature.trips

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import fr.familia.app.R
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.ui.formatDay
import java.util.Locale

/** "Du samedi 10 juillet au mardi 20 juillet", or "Le samedi 10 juillet" for a day trip. */
@Composable
fun tripDatesLabel(trip: Trip, today: java.time.LocalDate): String {
    val start = formatDay(trip.startDate, today).lowercase(Locale.FRANCE)
    return if (trip.startDate == trip.endDate) {
        stringResource(R.string.trip_dates_one_day, start)
    } else {
        stringResource(R.string.trip_dates, start, formatDay(trip.endDate, today).lowercase(Locale.FRANCE))
    }
}

@Composable
fun stepTypeLabel(type: StepType): String = stringResource(
    when (type) {
        StepType.Flight -> R.string.trip_step_type_flight
        StepType.Train -> R.string.trip_step_type_train
        StepType.Bus -> R.string.trip_step_type_bus
        StepType.Car -> R.string.trip_step_type_car
        StepType.Lodging -> R.string.trip_step_type_lodging
        StepType.Activity -> R.string.trip_step_type_activity
    },
)

/** Decorative: the type is always written next to it. */
fun stepTypeIcon(type: StepType): ImageVector = when (type) {
    StepType.Flight -> Icons.Outlined.Flight
    StepType.Train -> Icons.Outlined.Train
    StepType.Bus -> Icons.Outlined.DirectionsBus
    StepType.Car -> Icons.Outlined.DirectionsCar
    StepType.Lodging -> Icons.Outlined.Hotel
    StepType.Activity -> Icons.Outlined.LocalActivity
}

/** A section title of the trip screens, read as a heading by TalkBack. */
@Composable
fun TripSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}
