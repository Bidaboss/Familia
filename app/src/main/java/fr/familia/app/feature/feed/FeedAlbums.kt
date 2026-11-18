package fr.familia.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.concerns
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.events.album.eventAlbumFolder
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import fr.familia.app.feature.yearago.MemoryAlbum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** An album the fil may read: an event of the Planning or a trip, and its folder of photos. */
data class FeedAlbum(val album: MemoryAlbum, val folder: String, val date: LocalDate)

/** An album whose event or trip is older than the window by more than this still gets new photos. */
const val FEED_ALBUM_MARGIN_DAYS = 30L

/** The albums the fil listens to, at most: one listener each. */
const val FEED_ALBUMS_MAX = 20

/**
 * The albums [meId] can read and the fil may show, before any photo is read (récit 67): the events
 * that concern them and the trips the whole family follows, from [since] less a margin up to
 * [today], the most recent first. A trip hidden from someone (a surprise, readerIds) never shows,
 * even to those who follow it: a phone shown around must never give it away.
 */
fun feedAlbums(trips: List<Trip>, events: List<Event>, family: Family, meId: String, since: LocalDate, today: LocalDate): List<FeedAlbum> {
    val me = family.members.firstOrNull { it.id == meId } ?: return emptyList()
    val from = since.minusDays(FEED_ALBUM_MARGIN_DAYS)
    val fromTrips = trips
        .filter { trip -> family.members.all { it.id in trip.readerIds } && !trip.startDate.isAfter(today) && !trip.endDate.isBefore(from) }
        .map { FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Trip, it.id, it.title), tripAlbumFolder(it.id), it.startDate) }
    val fromEvents = events
        .filter { !it.date.isAfter(today) && !it.date.isBefore(from) && concerns(it.target, me, family) }
        .map { FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Event, it.id, it.title), eventAlbumFolder(it.id), it.date) }
    return (fromTrips + fromEvents)
        .sortedWith(compareByDescending<FeedAlbum> { it.date }.thenBy { it.folder })
        .take(FEED_ALBUMS_MAX)
}

/**
 * The cards of the photos: one per album and per day they were added (in Paris), the photos added
 * since [since] only, newest first, [limit] cards at most. A photo just sent, with no server time
 * yet, waits for it.
 */
fun albumCards(albums: List<FeedAlbum>, photos: List<List<AlbumPhoto>>, since: Instant, limit: Int): List<AlbumFeedCard> =
    albums.zip(photos).flatMap { (album, list) ->
        list.filter { it.createdAt?.isBefore(since) == false }
            .groupBy { it.createdAt!!.atZone(FAMILY_ZONE).toLocalDate() }
            .map { (day, ofDay) -> AlbumFeedCard(album, day, ofDay.sortedByDescending { it.createdAt }) }
    }
        .sortedWith(compareByDescending<AlbumFeedCard> { it.at }.thenBy { it.key })
        .take(limit)

/**
 * « Nouvelles photos » (récit 67): the photos added to the albums of the events and of the trips,
 * grouped by album and by day. [tripsOn] and [eventsOn] follow the switches of the modules.
 */
class AlbumFeedSource(
    private val trips: TripRepository?,
    private val events: EventRepository,
    private val albums: AlbumPhotoRepository,
    private val family: Family,
    private val meId: String,
    private val today: LocalDate,
    private val tripsOn: Boolean,
    private val eventsOn: Boolean,
) : FeedSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> {
        val sinceDay = window.since.atZone(FAMILY_ZONE).toLocalDate()
        val followed = if (tripsOn && trips != null) trips.observeFollowedTrips(meId).catch { emit(emptyList()) } else flowOf(emptyList())
        val recent = if (eventsOn) {
            events.observeBetween(sinceDay.minusDays(FEED_ALBUM_MARGIN_DAYS), today).catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }
        return combine(followed, recent) { trs, evts -> feedAlbums(trs, evts, family, meId, sinceDay, today) }
            .distinctUntilChanged()
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // An album that cannot be read counts as empty: the rest of the fil still shows.
                    combine(list.map { album -> albums.observePhotos(album.folder).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } }) { it.toList() }
                        .map { photos -> albumCards(list, photos, window.since, window.limit) }
                }
            }
    }
}

/** How many thumbnails a card shows; the others are counted on the last one. */
private const val THUMBS = 3

class AlbumFeedCard(
    val album: FeedAlbum,
    val day: LocalDate,
    /** Newest first, never empty. */
    val photos: List<AlbumPhoto>,
) : FeedCard {
    override val key: String = "album:${album.folder}:$day"
    override val at: Instant = photos.first().createdAt ?: Instant.EPOCH
    override val authorId: String = photos.first().authorId

    /** Who added them, the newest first, each once. */
    val authorNames: List<String> get() = photos.map { it.authorName }.distinct()

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val newest = photos.first()
        val names = authorNames
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenAlbumPhoto(album.album, newest.id) },
            header = {
                FeedHeader(
                    member = context.member(newest.authorId),
                    name = if (names.size == 1) names.first() else pluralStringResource(R.plurals.feed_album_authors, names.size - 1, names.first(), names.size - 1),
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                    label = stringResource(R.string.feed_label_album),
                    tone = tone,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    album.album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.feed_album_photos, photos.size, photos.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The thumbnails are said by the line above: TalkBack reads the card once.
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val shown = photos.take(THUMBS)
                shown.forEachIndexed { index, photo ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        StorageImage(photo.thumbnailPath, null, Modifier.fillMaxSize())
                        val more = photos.size - THUMBS
                        if (index == shown.lastIndex && more > 0) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.feed_album_more, more),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                // Fewer photos than places: the thumbnails keep their size.
                repeat(THUMBS - shown.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
