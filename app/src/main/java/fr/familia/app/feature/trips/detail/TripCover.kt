package fr.familia.app.feature.trips.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.R
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.place.PlacePhotos
import fr.familia.app.core.power.rememberBatterySaving
import fr.familia.app.feature.trips.PlaceCover
import fr.familia.app.feature.trips.list.animationsOff
import fr.familia.app.feature.trips.list.talkBackOn
import fr.familia.app.feature.trips.list.tripLength
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.map.GeoPoint
import kotlinx.coroutines.delay

/** One image of the cover: a place and its point, when the step has one; [fromStep] false for the title of a trip without a destination. */
data class CoverPlace(val name: String, val point: GeoPoint?, val fromStep: Boolean = true)

/**
 * The places of the cover (règle 10): the destinations only, in the order of the trip, never a transport
 * nor its departure (retour du 25/11 soir), at most [MAX_COVER_PLACES], each once. Without a destination,
 * the title of the trip and the drawn landscape.
 */
fun coverPlaces(trip: Trip, steps: List<TripStep>): List<CoverPlace> {
    val places = tripTimeline(trip, steps).destinations.map { row ->
        CoverPlace(placeName(row.step), row.step.takeIf { it.hasPosition }?.let { GeoPoint(it.lat!!, it.lng!!) })
    }.filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase() }
    return places.ifEmpty { listOf(CoverPlace(trip.title, null, fromStep = false)) }.take(MAX_COVER_PLACES)
}

const val MAX_COVER_PLACES = 5

/** The time an image stays before the next one. */
private const val SLIDE_MILLIS = 4_000L

/**
 * The big photo card of a trip (règle 10, fiche F1 §5.1): the photo of the main place, or several that
 * go by every 4 s (stopped with « Pause », when the phone asks for no animation, or with TalkBack so the
 * page does not change under the reading). Over it: « ● Voyage programmé » (« En cours », « Terminé »),
 * the name of the place shown, the name of the trip, its month, its days and its kilometres.
 */
@Composable
fun TripCoverCarousel(
    trip: Trip,
    steps: List<TripStep>,
    photos: PlacePhotos?,
    modifier: Modifier = Modifier,
    today: java.time.LocalDate = java.time.LocalDate.now(),
) {
    val places = coverPlaces(trip, steps)
    val pager = rememberPagerState { places.size }
    val context = LocalContext.current
    var paused by rememberSaveable { mutableStateOf(animationsOff(context) || talkBackOn(context)) }
    // Règle 46: the carousel stops turning by itself while the battery is saved.
    val saving = rememberBatterySaving()
    LaunchedEffect(paused, saving, places.size) {
        if (paused || saving || places.size < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_MILLIS)
            pager.animateScrollToPage((pager.currentPage + 1) % places.size)
        }
    }
    val figures = tripFigures(trip, steps, 0)
    val days = tripLength(trip)
    val phase = when {
        today.isBefore(trip.startDate) -> R.string.trip_cover_planned
        today.isAfter(trip.endDate) -> R.string.trip_cover_over
        else -> R.string.trip_cover_ongoing
    }
    Box(modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(24.dp))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val place = places[page]
            Box(
                Modifier.fillMaxSize().semantics {
                    contentDescription = context.getString(R.string.trip_cover_slide, page + 1, places.size, place.name)
                },
            ) {
                PlaceCover(
                    // Without a step, the name is the free title of the trip: it never goes to Wikipedia (B1 of #586).
                    place.name.takeIf { place.fromStep }, place.point, trip.landscape, photos, Modifier.fillMaxSize(),
                    // Under « Voyage programmé », away from the pause button and the title: the licence asks for it to be read.
                    creditAlignment = Alignment.TopStart,
                    creditTop = 44.dp,
                )
            }
        }
        // « ● Voyage programmé »: dark blue at 85 %, 24 dp high (F1).
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 14.dp).height(24.dp)
                .clip(RoundedCornerShape(12.dp)).background(CoverBlue.copy(alpha = 0.85f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Text(stringResource(phase), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The place of the photo shown, above the name of the trip.
            Text(
                places.getOrNull(pager.currentPage)?.name.orEmpty(),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(x = (-8).dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Text(trip.title, fontFamily = Fredoka, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    CoverFigure(frenchDate(trip.startDate, "MMMM"), trip.startDate.year.toString())
                    CoverFigure("$days", pluralStringResource(R.plurals.trip_cover_figure_days, days))
                    figures?.distanceKm?.let { km ->
                        CoverFigure(java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(km), stringResource(R.string.trip_cover_figure_km))
                    }
                }
                if (places.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        repeat(places.size) { i ->
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        }
        if (places.size > 1) {
            IconButton(
                onClick = { paused = !paused },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(56.dp),
            ) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.trip_cover_play else R.string.trip_cover_pause),
                    tint = Color.White,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(CoverBlue.copy(alpha = 0.7f)).padding(12.dp),
                )
            }
        }
    }
}

/** The dark blue of the labels over the photo (F1: rgb(23,51,63)). */
private val CoverBlue = Color(0xFF17333F)

/** « août » over « 2027 », « 8 » over « JOURS » (F1: 19 sp heavy, then 12 sp capitals). */
@Composable
private fun CoverFigure(value: String, label: String) {
    Column {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
    }
}
