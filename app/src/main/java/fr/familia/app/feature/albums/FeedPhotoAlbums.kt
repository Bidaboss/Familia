package fr.familia.app.feature.albums

import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.GoodNews
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One photo published in the fil, from a Bonne nouvelle. */
data class FeedPhoto(val newsId: String, val thumbnailPath: String, val authorName: String, val at: Instant)

/**
 * « Photos du fil » of one month (récit 71, maquette SF2): every photo published in the fil that
 * month, made by itself, newest first, never empty.
 */
data class FeedPhotoAlbum(val month: YearMonth, val photos: List<FeedPhoto>) {
    val cover: FeedPhoto get() = photos.first()

    /** Who published them, the newest first, each once. */
    val authorNames: List<String> get() = photos.map { it.authorName }.distinct()
}

/**
 * The « Photos du fil » albums [meId] sees, newest month first: the photos of the Bonnes nouvelles
 * the server has dated, less those [meId] chose to hide for themselves. One just sent waits for its
 * server time; Familia's own posts have no photo.
 */
fun feedPhotoAlbums(news: List<GoodNews>, meId: String, zone: ZoneId = FAMILY_ZONE): List<FeedPhotoAlbum> =
    news.asSequence()
        .filter { it.createdAt != null && !it.pending && !it.isHiddenFor(meId) && it.photoIds.isNotEmpty() }
        .flatMap { n -> n.photoIds.map { FeedPhoto(n.id, n.thumbnailPath(it), n.authorName, n.createdAt!!) } }
        .groupBy { YearMonth.from(it.at.atZone(zone)) }
        .map { (month, photos) -> FeedPhotoAlbum(month, photos.sortedWith(compareByDescending<FeedPhoto> { it.at }.thenBy { it.thumbnailPath })) }
        .sortedByDescending { it.month }

/** A tile of the grid of Albums: an album of an event or a trip, or the « Photos du fil » of the month. */
sealed interface AlbumTile {
    val key: String

    data class Album(val entry: AlbumEntry) : AlbumTile {
        override val key: String get() = "${entry.kind}-${entry.id}"
    }

    data class Feed(val album: FeedPhotoAlbum) : AlbumTile {
        override val key: String get() = "feed-${album.month}"
    }
}

/** One month of Albums, its tiles in a grid of 2: « Photos du fil » first, then the most recent album. */
data class AlbumMonthSection(val month: YearMonth, val tiles: List<AlbumTile>)

/** The albums and the « Photos du fil » ranged by month, newest month first; a month with nothing is left out. */
fun albumSections(albums: List<AlbumEntry>, feed: List<FeedPhotoAlbum>): List<AlbumMonthSection> {
    val byMonth = albums.groupBy { YearMonth.from(it.date) }
    val feedByMonth = feed.associateBy { it.month }
    return (byMonth.keys + feedByMonth.keys).distinct().sortedDescending().map { month ->
        val tiles = listOfNotNull(feedByMonth[month]?.let { AlbumTile.Feed(it) }) +
            byMonth[month].orEmpty().sortedWith(compareByDescending<AlbumEntry> { it.date }.thenBy { it.title.lowercase() }).map { AlbumTile.Album(it) }
        AlbumMonthSection(month, tiles)
    }
}
