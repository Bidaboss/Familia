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

/** One image of the cover: a place and its point, when the step has one. */
data class CoverPlace(val name: String, val point: GeoPoint?)

/** The places of the cover (règle 10): the main one first (the first step), then the others, at most [MAX_COVER_PLACES], each once. */
fun coverPlaces(trip: Trip, steps: List<TripStep>): List<CoverPlace> {
    val places = steps.map { step ->
        CoverPlace(step.place.substringBefore('→').substringBefore(',').trim(), step.takeIf { it.hasPosition }?.let { GeoPoint(it.lat!!, it.lng!!) })
    }.filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase() }
    return places.ifEmpty { listOf(CoverPlace(trip.title, null)) }.take(MAX_COVER_PLACES)
}

const val MAX_COVER_PLACES = 5

/** The time an image stays before the next one. */
private const val SLIDE_MILLIS = 4_000L

/**
 * The home of a planned trip (règle 10): the image of the main place, or several images that go by
 * every 4 s (stopped with « Pause », when the phone asks for no animation, or with TalkBack so the page does not change under the reading), « Voyage
 * programmé », the name of the trip, its month, its days and its kilometres.
 */
@Composable
fun TripCoverCarousel(
    trip: Trip,
    steps: List<TripStep>,
    photos: PlacePhotos?,
    token: String,
    modifier: Modifier = Modifier,
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
    Box(modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val place = places[page]
            Box(
                Modifier.fillMaxSize().semantics {
                    contentDescription = context.getString(R.string.trip_cover_slide, page + 1, places.size, place.name)
                },
            ) {
                PlaceCover(
                    place.name, place.point, trip.landscape, photos, token, Modifier.fillMaxSize(),
                    // Under « Voyage programmé », away from the pause button and the title: the licence asks for it to be read.
                    creditAlignment = Alignment.TopStart,
                    creditTop = 46.dp,
                )
            }
        }
        Text(
            stringResource(R.string.trip_cover_planned),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TripSheetColors.Ink,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(TripSheetColors.Highlight).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(trip.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val days = tripLength(trip)
            val parts = buildList {
                add(frenchDate(trip.startDate, "MMMM yyyy"))
                add(pluralStringResource(R.plurals.trips_days, days, days))
                figures?.distanceKm?.let { add(stringResource(R.string.figures_km, it)) }
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            if (places.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(places.size) { i ->
                        Box(Modifier.size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.5f)))
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
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(6.dp),
                )
            }
        }
    }
}
