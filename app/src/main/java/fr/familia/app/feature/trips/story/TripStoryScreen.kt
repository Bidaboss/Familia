package fr.familia.app.feature.trips.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.map.projectToUnit

/** What the story does; the route wires them to the ViewModel. */
class TripStoryActions(
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNextDay: () -> Unit = {},
    val onReplay: () -> Unit = {},
    val onHeart: () -> Unit = {},
    /** The comments are on the page of the word of the step. */
    val onOpenWord: (stepId: String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
)

private val SegmentOn = Color.White
private val SegmentOff = Color.White.copy(alpha = 0.35f)

/**
 * « Le voyage jour par jour » (récit 90, variante A): full-screen story of a day, dark like the
 * month slideshow. Nothing moves by itself: the right third or the arrow goes on, the left third or
 * the arrow goes back, a swipe too. Stateless.
 */
@Composable
fun TripStoryScreen(state: TripStoryUiState, actions: TripStoryActions) {
    when (state) {
        TripStoryUiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { LoadingState() }
        TripStoryUiState.Missing -> StoryMessage(actions) {
            EmptyState(Icons.Outlined.CardTravel, stringResource(R.string.story_missing_title), stringResource(R.string.story_missing_body))
        }
        TripStoryUiState.Error -> StoryMessage(actions) {
            EmptyState(
                icon = Icons.Outlined.CardTravel,
                title = stringResource(R.string.trips_error_title),
                body = stringResource(R.string.trips_error_body),
                action = { Button(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) } },
            )
        }
        is TripStoryUiState.Ready -> StoryReady(state, actions)
    }
}

@Composable
private fun StoryMessage(actions: TripStoryActions, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TextButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = 56.dp).padding(4.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.album_photo_close))
        }
        content()
    }
}

@Composable
private fun StoryReady(state: TripStoryUiState.Ready, actions: TripStoryActions) {
    val frame = state.frame
    val onSky = frame is StoryFrame.Text
    val ink = if (onSky) TripPathColors.Ink else Color.White
    Column(
        Modifier
            .fillMaxSize()
            .background(if (onSky) TripPathColors.Sky else Color.Black)
            // A swipe goes on or back, like the touch zones.
            .pointerInput(state.dayIndex, state.frameIndex) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        if (travelled < -SWIPE_PX) actions.onNext() else if (travelled > SWIPE_PX) actions.onPrevious()
                    },
                ) { _, delta -> travelled += delta }
            },
    ) {
        Segments(state.frames.size, state.frameIndex, onSky)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = actions.onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (onSky) Color.White else Color.White.copy(alpha = 0.16f),
                    contentColor = ink,
                ),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.album_photo_close))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.story_day, state.day.number),
                    color = ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(frenchDate(state.day.date, "EEEE d MMMM"), color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (frame) {
                is StoryFrame.Photo -> PhotoFrame(state, frame, actions)
                is StoryFrame.Text -> TextFrame(state, frame, actions)
                StoryFrame.End -> EndFrame(state, actions)
            }
            if (frame !is StoryFrame.End) Navigation(state, actions, ink = Color.White)
        }
    }
}

/** Under this much travelled (px), a drag is not a swipe. */
private const val SWIPE_PX = 80f

@Composable
private fun Segments(count: Int, index: Int, onSky: Boolean) {
    val on = if (onSky) TripPathColors.Ink else SegmentOn
    val off = if (onSky) TripPathColors.Ink.copy(alpha = 0.25f) else SegmentOff
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = "" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= index) on else off))
        }
    }
}

/** The touch zones (left and right thirds) and the two visible arrows, for those who cannot guess a zone. */
@Composable
private fun Navigation(state: TripStoryUiState.Ready, actions: TripStoryActions, ink: Color) {
    Row(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = actions.onPrevious))
        Spacer(Modifier.weight(1f))
        Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = actions.onNext))
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.frameIndex > 0) ArrowButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.story_previous), actions.onPrevious, ink)
        Spacer(Modifier.weight(1f))
        ArrowButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.story_next), actions.onNext, ink)
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, ink: Color) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = ink),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun PhotoFrame(state: TripStoryUiState.Ready, frame: StoryFrame.Photo, actions: TripStoryActions) {
    Box(Modifier.fillMaxSize()) {
        StorageImage(
            path = frame.photo.photoPath,
            contentDescription = stringResource(R.string.album_photo_by, frame.photo.authorName),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            showLoading = true,
        )
        // The word and the buttons sit on a dark panel: white stays readable on any photo.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.78f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordBlock(state, frame.story, Color.White, bodySize = 19.sp, actions)
        }
    }
}

@Composable
private fun TextFrame(state: TripStoryUiState.Ready, frame: StoryFrame.Text, actions: TripStoryActions) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 56.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WordBlock(state, frame.story, TripPathColors.Ink, bodySize = 25.sp, actions, noPhoto = true)
        }
    }
}

/** Who wrote it and for which step, the word, and the heart and comments buttons. */
@Composable
private fun WordBlock(
    state: TripStoryUiState.Ready,
    story: StoryWord,
    ink: Color,
    bodySize: androidx.compose.ui.unit.TextUnit,
    actions: TripStoryActions,
    noPhoto: Boolean = false,
) {
    val author = state.memberOf(story.word.authorId)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (author != null) MemberAvatar(author, size = 40.dp)
        Column {
            Text(author?.firstName ?: story.word.authorName, color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(story.step.place, color = ink, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    // Touched, the word shows in full.
    var open by remember(story.word.stepId, story.word.text) { mutableStateOf(false) }
    Text(
        story.word.text,
        color = ink,
        fontSize = bodySize,
        lineHeight = bodySize * 1.3f,
        fontWeight = if (noPhoto) FontWeight.Bold else FontWeight.Normal,
        maxLines = if (open || noPhoto) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    )
    if (noPhoto) Text(stringResource(R.string.story_no_photo), color = ink, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val heartLabel = stringResource(R.string.story_heart, state.hearts)
        OutlinedButton(
            onClick = actions.onHeart,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = heartLabel },
        ) {
            Icon(if (state.hearted) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(state.hearts.toString())
        }
        OutlinedButton(
            onClick = { actions.onOpenWord(story.step.id) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.story_comments))
        }
    }
    if (state.heartFailed) Text(stringResource(R.string.story_heart_failed), color = ink, style = MaterialTheme.typography.bodyMedium)
}

/** The route of the day on the sky of the trip, « Le trajet du jour », then « Jour 3 » and « Revoir ce jour ». */
@Composable
private fun EndFrame(state: TripStoryUiState.Ready, actions: TripStoryActions) {
    val day = state.day
    val placed = remember(day) { day.steps.filter { it.hasPosition } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(TripPathColors.Sky).clearAndSetSemantics { },
        ) {
            val points = projectToUnit(placed.map { GeoPoint(it.lat!!, it.lng!!) }, aspect = size.width / size.height)
                .map { Offset(it.x * size.width, it.y * size.height) }
            points.zipWithNext().forEach { (a, b) -> drawLine(TripPathColors.RoadDash, a, b, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round) }
            points.forEachIndexed { i, p ->
                drawCircle(if (i == points.lastIndex) TripPathColors.Sun else Color.White, radius = 14.dp.toPx(), center = p)
                drawCircle(TripPathColors.Ink, radius = 14.dp.toPx(), center = p, style = Stroke(3.dp.toPx()))
            }
        }
        Text(
            stringResource(R.string.story_route_title),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        val places = day.routeLabel
        val km = day.distanceKm
        val line = when {
            places == null -> stringResource(R.string.story_route_unplaced_only)
            km != null && !day.hasUnplaced -> stringResource(R.string.story_route_line, places, km)
            else -> stringResource(R.string.story_route_unplaced, places)
        }
        Text(line, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        if (state.hasNextDay) {
            Button(
                onClick = actions.onNextDay,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TripPathColors.Ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.story_next_day, state.days[state.dayIndex + 1].number))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
        OutlinedButton(
            onClick = actions.onReplay,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.story_replay)) }
        // The last day has only « Revoir ce jour » and « Fermer ».
    }
}

@Preview
@Composable
private fun StoryPreview() {
    FamiliaTheme { TripStoryScreen(previewTripStory(frame = 0), TripStoryActions()) }
}

/** A trip with a word on two days, for the previews and the accessibility tests. */
internal fun previewTripStory(frame: Int, withPhoto: Boolean = true, lastDay: Boolean = false): TripStoryUiState.Ready {
    val family = fr.familia.app.core.model.Family(
        listOf(fr.familia.app.core.model.Household("h1", "Les Martin")),
        listOf("Camille", "Inès", "Noé").mapIndexed { i, name ->
            fr.familia.app.core.model.Member("m$i", name, null, null, "h1", fr.familia.app.core.model.MemberProfile.Adult, false, emptyList(), null)
        },
    )
    fun step(id: String, day: Int, place: String, lat: Double?, lng: Double?) = fr.familia.app.core.model.TripStep(
        id, fr.familia.app.core.model.StepType.Lodging, place, java.time.LocalDate.of(2026, 4, day), null, null, null, null, lat = lat, lng = lng,
    )
    val steps = listOf(step("s1", 11, "Lyon", 45.76, 4.84), step("s2", 12, "Bordeaux", 44.84, -0.58), step("s3", 12, "La Palmyre", 45.68, -1.18))
    val photos = if (withPhoto) listOf(AlbumPhoto("trips/t1", "m0", "Camille", 1, null), AlbumPhoto("trips/t1", "m0", "Camille", 2, null)) else emptyList()
    fun word(stepId: String, text: String) = fr.familia.app.core.model.TripWord(
        stepId, text, photos.map { it.id }, "m0", "Camille", setOf("m1", "m2"),
    )
    val days = listOf(
        StoryDay(
            java.time.LocalDate.of(2026, 4, 11), 1,
            listOf(StoryWord(steps[0], word("s1", "Arrivés à Lyon, premier café sur la place."), photos)), listOf(steps[0]),
        ),
        StoryDay(
            java.time.LocalDate.of(2026, 4, 12), 2,
            listOf(StoryWord(steps[2], word("s3", "On a posé les valises au camping et filé à la plage. Léon a construit un château de sable plus haut que lui !"), photos)),
            listOf(steps[1], steps[2]),
        ),
    )
    val index = if (lastDay) 1 else 0
    val frames = storyFrames(days[index])
    return TripStoryUiState.Ready("La Palmyre", days, index, frames, frame.coerceIn(0, frames.lastIndex), "m1", family)
}
