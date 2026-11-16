package fr.familia.app.feature.expenses

import fr.familia.app.core.model.Member
import kotlin.math.roundToLong

/** The fewest and the most shares a household may weigh: half a share, up to twenty. */
const val SHARE_MIN = 0.5
const val SHARE_MAX = 20.0

/** The shares of one person: none (a baby who eats nothing), up to ten, by halves. */
const val PERSON_SHARE_MIN = 0.0
const val PERSON_SHARE_MAX = 10.0

private val SHARE = Regex("""^(\d{1,2})(?:[.,](\d))?$""")

/**
 * The shares of a household typed by the creator, « 1,5 » or « 2 »: a number from [SHARE_MIN] to
 * [SHARE_MAX], by halves. Null for anything else, so a typo never weighs on the balance.
 */
fun parseShare(text: String, min: Double = SHARE_MIN, max: Double = SHARE_MAX): Double? {
    val match = SHARE.matchEntire(text.trim()) ?: return null
    val tenths = match.groupValues[1].toLong() * 10 + (match.groupValues[2].ifEmpty { "0" }.toLong())
    if (tenths % 5 != 0L) return null
    val value = tenths / 10.0
    return value.takeIf { it in min..max }
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

/** The shares of one person typed by the creator, « 1 », « 0,5 » or « 0 ». */
fun parsePersonShare(text: String): Double? = parseShare(text, PERSON_SHARE_MIN, PERSON_SHARE_MAX)

/** The shares of a book, typed person by person: what each person weighs, and the sum for each household. */
data class BookShares(val memberShares: Map<String, Double>, val households: Map<String, Double>)

/**
 * The shares typed for each of [people], checked: null as soon as one is wrong, or when a household
 * would weigh nothing. The households of [current] with nobody among [people] (a household that only
 * paid, or whose people left) keep their shares, so none drops out of the balance.
 */
fun parsePersonShares(typed: Map<String, String>, people: List<Member>, current: Map<String, Double>): BookShares? {
    if (people.isEmpty()) return null
    val byPerson = people.associate { p -> p.id to (parsePersonShare(typed[p.id].orEmpty()) ?: return null) }
    val byHousehold = householdShares(people, byPerson)
    if (byHousehold.values.any { it < SHARE_MIN }) return null
    return BookShares(byPerson, current + byHousehold)
}
