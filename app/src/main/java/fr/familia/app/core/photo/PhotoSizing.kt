package fr.familia.app.core.photo

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sizes of the photos sent to Storage (Bonnes nouvelles, voyages). The limits match
 * firebase/storage.rules: a photo above [PHOTO_LIMIT_BYTES] or a thumbnail above [THUMB_LIMIT_BYTES]
 * is refused by the server.
 */
object PhotoSizing {
    const val PHOTO_MAX_SIDE = 1600
    const val THUMB_MAX_SIDE = 400
    const val PHOTO_TARGET_BYTES = 300 * 1024
    const val THUMB_TARGET_BYTES = 30 * 1024
    const val PHOTO_LIMIT_BYTES = 512 * 1024
    const val THUMB_LIMIT_BYTES = 64 * 1024

    /** JPEG qualities tried in turn, until the file is small enough. */
    val QUALITIES = listOf(85, 75, 65, 55, 45)

    /** The size of a [width] × [height] picture whose longest side is at most [maxSide]. */
    fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Empty picture" }
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val ratio = maxSide.toDouble() / longest
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    /**
     * The largest power of two that still decodes the picture with its longest side at least
     * [maxSide], so a 12 MP photo is never fully loaded in memory.
     */
    fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
