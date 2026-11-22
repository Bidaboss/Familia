package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.feature.trips.list.tripLength
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * « Le voyage en chiffres » (récit 89, variante A): what the trip counts by itself, never typed,
 * never compared with another trip or another member. [distanceKm] is null until two steps have a
 * position; [placed] is how many steps count in it.
 */
data class TripFigures(
    val steps: Int,
    val days: Int,
    val places: Int,
    val photos: Int,
    val distanceKm: Int?,
    val placed: Int,
) {
    /** Only places worth a number: one place is not a figure. */
    val showPlaces: Boolean get() = places >= 2
    val showPhotos: Boolean get() = photos > 0

    /** « 4 étapes sur 5 »: the distance leaves out a step with no position. */
    val showsUnplacedLine: Boolean get() = distanceKm != null && placed < steps
}

/** The figures of [trip] with [steps] in their order and [photoCount] photos of its album; null with no step. */
fun tripFigures(trip: Trip, steps: List<TripStep>, photoCount: Int): TripFigures? {
    if (steps.isEmpty()) return null
    val placed = steps.filter { it.hasPosition }
    val distance = if (placed.size >= 2) {
        placed.zipWithNext { a, b -> flightKm(a.lat!!, a.lng!!, b.lat!!, b.lng!!) }.sum().roundToInt()
    } else {
        null
    }
    return TripFigures(
        steps = steps.size,
        days = tripLength(trip),
        places = steps.map { it.place.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size,
        photos = photoCount,
        distanceKm = distance,
        placed = placed.size,
    )
}

/** As the crow flies between two points, in kilometres (haversine, earth radius 6371 km). */
fun flightKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** The tile of the figures, under the steps. A figure at 0 never shows. */
@Composable
fun TripFiguresTile(figures: TripFigures, modifier: Modifier = Modifier) {
    FormTile(Icons.Outlined.Star, stringResource(R.string.figures_title), modifier) {
        val cells = buildList<Pair<Int, String>> {
            add(figures.steps to pluralStringResource(R.plurals.figures_steps, figures.steps))
            add(figures.days to pluralStringResource(R.plurals.figures_days, figures.days))
            if (figures.showPlaces) add(figures.places to pluralStringResource(R.plurals.figures_places, figures.places))
            if (figures.showPhotos) add(figures.photos to pluralStringResource(R.plurals.figures_photos, figures.photos))
        }
        // At 130 % and more the grid goes to one column: the figures stay readable.
        val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
        cells.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (value, label) -> FigureCell(value.toString(), label, Modifier.weight(1f)) }
                if (row.size < columns) Box(Modifier.weight((columns - row.size).toFloat()))
            }
        }
        figures.distanceKm?.let { km ->
            FigureCell(stringResource(R.string.figures_km, km), stringResource(R.string.figures_distance), Modifier.fillMaxWidth())
            if (figures.showsUnplacedLine) {
                FormHint(stringResource(R.string.figures_unplaced, figures.placed, figures.steps))
            }
        } ?: FormHint(stringResource(R.string.figures_distance_later))
    }
}

@Composable
private fun FigureCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(12.dp)
            // One phrase for TalkBack: « 5 étapes ».
            .semantics(mergeDescendants = true) { },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}
