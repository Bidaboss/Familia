package fr.familia.app.feature.albums.book

import java.io.File

/**
 * The copies of the full photos kept while « Faire le livre photo » is open (photo-book/ in the
 * cache). Beyond [MAX_BYTES] a photo is no longer kept: the book is still made, the photo is only
 * downloaded again the next time. A phone short of room keeps its room for the PDF itself.
 */
object PhotoBookCache {
    /** About 500 photos of an album; far more than the PDF needs at once. */
    const val MAX_BYTES: Long = 150L * 1024 * 1024

    /** Whether [bytes] more still fit in [folder] under [max]. */
    fun hasRoomFor(folder: File, bytes: Int, max: Long = MAX_BYTES): Boolean {
        val used = folder.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
        return used + bytes <= max
    }
}
