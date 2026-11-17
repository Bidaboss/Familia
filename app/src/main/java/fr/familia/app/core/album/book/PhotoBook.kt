package fr.familia.app.core.album.book

import fr.familia.app.core.album.AlbumPhoto
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Le livre photo d'un album (PRD rév. 253, récit 59), behind Feature.PhotoBook: from the album of
 * a trip or an event, the member picks the photos, their order and the ones shown big; the phone
 * makes a PDF (A4 landscape or square) with the engine of récits 47 and 53, and the share sheet opens.
 * Every photo of the album goes in, as the family already sees them (PRD rév. 254); only the
 * reminder of carte EXPERT-6 comes before a share outside Familia.
 */

/** Which album the book is made from: [kind] is « trip » or « event », [id] its id. */
@Serializable
data class PhotoBookDestination(val kind: String, val id: String) {
    companion object {
        const val TRIP = "trip"
        const val EVENT = "event"
    }
}

/** The size of a page, in PDF points (1/72 inch). */
enum class PhotoBookFormat(val width: Int, val height: Int) {
    /** A4 landscape, for a printer at home. */
    Landscape(842, 595),

    /** 21 × 21 cm, the size of most photo books in shops. */
    Square(595, 595),
}

/** A photo of the book, with its caption: who took it and when. */
data class BookPhoto(val photo: AlbumPhoto, val caption: String, val big: Boolean = false)

/** A step of the trip, on the last page: its kind as said in French (« Train »), where and when it left. */
data class BookStep(val kind: String, val place: String, val date: LocalDate)

/** What the printer needs. */
data class PhotoBookContent(
    val title: String,
    /** « du 3 au 9 août 2026 », or the day of the event. */
    val dates: String,
    val format: PhotoBookFormat,
    val cover: AlbumPhoto?,
    val pages: List<List<BookPhoto>>,
    val steps: List<BookStep>,
) {
    val photos: List<AlbumPhoto> get() = (listOfNotNull(cover) + pages.flatten().map { it.photo }).distinctBy { it.id }
}

/** At most this many photos on a page; a photo shown big is alone on its page. */
const val PHOTOS_PER_PAGE = 4

/** The pages, in the order of [photos]: groups of up to four, each big photo on a page of its own. */
fun bookPages(photos: List<BookPhoto>): List<List<BookPhoto>> {
    val pages = mutableListOf<List<BookPhoto>>()
    val current = mutableListOf<BookPhoto>()
    fun flush() {
        if (current.isNotEmpty()) pages += current.toList()
        current.clear()
    }
    photos.forEach { photo ->
        if (photo.big) {
            flush()
            pages += listOf(photo)
        } else {
            current += photo
            if (current.size == PHOTOS_PER_PAGE) flush()
        }
    }
    flush()
    return pages
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

/** « Camille, 4 août 2026 », or the first name alone when the server time has not come yet. */
fun bookCaption(photo: AlbumPhoto, zone: ZoneId = ZoneId.systemDefault()): String =
    photo.createdAt?.let { "${photo.authorName}, ${dayFormat.format(it.atZone(zone))}" } ?: photo.authorName

/** « 4 août 2026 », or « du 3 au 9 août 2026 », « du 30 juillet au 2 août 2026 », across years too. */
fun bookDates(start: LocalDate, end: LocalDate = start): String {
    if (!end.isAfter(start)) return dayFormat.format(start)
    val first = when {
        start.year != end.year -> dayFormat.format(start)
        start.month != end.month -> DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH).format(start)
        else -> start.dayOfMonth.toString()
    }
    return "du $first au ${dayFormat.format(end)}"
}

/** « livre-photo-vacances-a-la-mer.pdf »: plain letters, so every phone and photo shop accepts it. */
fun photoBookFileName(title: String): String {
    val plain = java.text.Normalizer.normalize(title.lowercase(Locale.FRENCH), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
        .trim('-')
    return if (plain.isEmpty()) "livre-photo.pdf" else "livre-photo-$plain.pdf"
}

/** The title of the cover, 60 characters at most so it fits on two lines. */
const val PHOTO_BOOK_TITLE_MAX = 60
