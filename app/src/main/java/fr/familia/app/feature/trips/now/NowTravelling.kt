package fr.familia.app.feature.trips.now

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.trips.TripPhase
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import fr.familia.app.feature.trips.tripPhase
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
import java.time.temporal.ChronoUnit

/**
 * « En ce moment » on the Accueil (R1 bis, §2 point 6): a trip under way that this member follows.
 * [others] are the first names of those who go, without this member; [withMe] when they go too.
 */
data class NowTravelling(
    val tripId: String,
    val title: String,
    val others: List<String>,
    val withMe: Boolean,
    /** 1 on the first day of the trip. */
    val day: Int,
    val photos: Int = 0,
)

/**
 * The trip under way among [trips], or null. With several, the one that started first, then the
 * first by name, so the card never jumps from one to the other.
 */
fun nowTravelling(trips: List<Trip>, family: Family, memberId: String, today: LocalDate): NowTravelling? {
    val trip = trips.filter { tripPhase(it, today) == TripPhase.Ongoing }
        .minWithOrNull(compareBy<Trip>({ it.startDate }, { it.title.lowercase() }, { it.id }))
        ?: return null
    return NowTravelling(
        tripId = trip.id,
        title = trip.title,
        others = firstNames(trip.participantIds.filter { it != memberId }, family),
        withMe = memberId in trip.participantIds,
        day = ChronoUnit.DAYS.between(trip.startDate, today).toInt() + 1,
    )
}

/** The trip under way and the number of its photos, updated live; nothing on error. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeNowTravelling(
    trips: TripRepository,
    members: MemberRepository,
    photos: AlbumPhotoRepository?,
    memberId: String,
    today: Flow<LocalDate> = currentDayFlow(),
): Flow<NowTravelling?> =
    combine(trips.observeFollowedTrips(memberId), members.observeFamily(), today) { list, family, day ->
        nowTravelling(list, family, memberId, day)
    }
        .distinctUntilChanged()
        .flatMapLatest { now ->
            when {
                now == null -> flowOf(null)
                photos == null -> flowOf(now)
                else -> photos.observePhotos(tripAlbumFolder(now.tripId))
                    .map { now.copy(photos = it.size) }
                    .onStart { emit(now) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(now)
                    }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(null)
        }

/** For the Accueil: the trip under way, or null when there is none or the trips are off. */
@Composable
fun rememberNowTravelling(container: AppContainer?, member: Member): NowTravelling? {
    val on = LocalFeatureFlags.current.isOn(Feature.Trips)
    val trips = container?.tripRepository
    val members = container?.memberRepository
    val flow = remember(on, trips, members, member.id) {
        if (!on || trips == null || members == null) emptyFlow() else observeNowTravelling(trips, members, container?.albumPhotoRepository, member.id)
    }
    val now by flow.collectAsStateWithLifecycle(initialValue = null)
    return now
}

/**
 * The card « En ce moment »: a pale blue background, a small landscape, « Paméla et Vincent sont à
 * Séville » and « Jour 3, 18 photos ». The whole card opens the trip.
 */
@Composable
fun NowTravellingCard(state: NowTravelling, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val headline = when {
        state.withMe && state.others.isEmpty() -> stringResource(R.string.now_travelling_me, state.title)
        state.withMe -> stringResource(R.string.now_travelling_me_with, state.title, joinNames(state.others))
        state.others.size == 1 -> stringResource(R.string.now_travelling_one, state.others.first(), state.title)
        else -> stringResource(R.string.now_travelling_many, joinNames(state.others), state.title)
    }
    val detail = stringResource(
        R.string.now_travelling_detail,
        state.day,
        pluralStringResource(R.plurals.trip_photo_count, state.photos, state.photos),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TripPathColors.Sky)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.now_travelling_open), onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SmallLandscape()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.now_travelling_label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TripPathColors.InkSoft)
            Text(headline, style = MaterialTheme.typography.titleMedium, color = TripPathColors.Ink)
            Text(detail, fontSize = 15.sp, color = TripPathColors.InkSoft)
        }
    }
}

/** A sun, two dunes and a dotted path, 56 dp: decorative. */
@Composable
private fun SmallLandscape() {
    Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        drawRect(Color.White.copy(alpha = 0.5f))
        drawCircle(TripPathColors.Sun, radius = w * 0.14f, center = Offset(w * 0.75f, h * 0.25f))
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.55f)
                quadraticTo(w * 0.35f, h * 0.38f, w * 0.6f, h * 0.55f)
                quadraticTo(w * 0.85f, h * 0.7f, w, h * 0.5f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            TripPathColors.DuneMid,
        )
        drawPath(
            Path().apply {
                moveTo(0f, h * 0.8f)
                quadraticTo(w * 0.5f, h * 0.65f, w, h * 0.78f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            TripPathColors.DuneNear,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.15f, h * 0.95f)
                quadraticTo(w * 0.7f, h * 0.8f, w * 0.45f, h * 0.62f)
            },
            TripPathColors.RoadDash,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

@FamiliaPreviews
@Composable
private fun NowTravellingCardPreview() {
    FamiliaTheme {
        Surface {
            NowTravellingCard(
                NowTravelling("t1", "Corse", listOf("Camille", "Noé"), withMe = false, day = 3, photos = 18),
                onOpen = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
