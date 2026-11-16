package fr.familia.app.feature.expenses

import kotlin.math.roundToLong

/** The fewest and the most shares a household may weigh: half a child, up to twenty people. */
const val SHARE_MIN = 0.5
const val SHARE_MAX = 20.0

private val SHARE = Regex("""^(\d{1,2})(?:[.,](\d))?$""")

/**
 * The shares of a household typed by the creator, « 1,5 » or « 2 »: a number from [SHARE_MIN] to
 * [SHARE_MAX], by halves. Null for anything else, so a typo never weighs on the balance.
 */
fun parseShare(text: String): Double? {
    val match = SHARE.matchEntire(text.trim()) ?: return null
    val tenths = match.groupValues[1].toLong() * 10 + (match.groupValues[2].ifEmpty { "0" }.toLong())
    if (tenths % 5 != 0L) return null
    val value = tenths / 10.0
    return value.takeIf { it in SHARE_MIN..SHARE_MAX }
}

/** The shares, the French way: « 1,5 », « 2 ». */
fun shareText(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10},${tenths % 10}"
}

/**
 * The shares typed for each household, checked: null as soon as one of them is wrong. Every
 * household of [households] keeps a value, so none drops out of the balance.
 */
fun parseShares(typed: Map<String, String>, households: Collection<String>): Map<String, Double>? {
    val parsed = households.associateWith { id -> parseShare(typed[id].orEmpty()) ?: return null }
    return parsed.takeIf { it.isNotEmpty() }
}
