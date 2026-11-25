package fr.familia.app.core.model

/**
 * The card of a member (PRD, récit 7 bis): their shoe size, tastes and hobbies or passions, so the
 * family picks the right present. Récit 114 adds « À savoir » ([allergies], [diet], [avoid]: « ne
 * pas offrir »), the sizes of « Goûts et passions » ([top], [bottom], [ring]: tour de doigt) and
 * « En ce moment » ([now], one free line). Free texts, blank when unknown. Every member of the
 * family reads it; the member, an adult of their household for a child, or the admin principal
 * changes it.
 */
data class MemberCard(
    val memberId: String,
    val shoes: String = "",
    val tastes: String = "",
    val hobbies: String = "",
    val top: String = "",
    val bottom: String = "",
    val ring: String = "",
    val allergies: String = "",
    val diet: String = "",
    val avoid: String = "",
    val now: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(shoes, tastes, hobbies, top, bottom, ring, allergies, diet, avoid, now).all { it.isBlank() }

    companion object {
        /** The longest size, « 42 ½ » or « 30-31 »: the rules check it too. */
        const val SIZE_MAX = 20

        /** The longest tastes, hobbies or « À savoir » text: the rules check it too. */
        const val TEXT_MAX = 300

        /** « En ce moment » is one line: the rules check it too. */
        const val NOW_MAX = 80
    }
}

/** The card as typed: each field trimmed, too long ones refused (null), so nothing is cut silently. */
fun MemberCard.cleaned(): MemberCard? {
    val c = copy(
        shoes = shoes.trim(), tastes = tastes.trim(), hobbies = hobbies.trim(),
        top = top.trim(), bottom = bottom.trim(), ring = ring.trim(),
        allergies = allergies.trim(), diet = diet.trim(), avoid = avoid.trim(), now = now.trim(),
    )
    val sizes = listOf(c.shoes, c.top, c.bottom, c.ring).all { it.length <= MemberCard.SIZE_MAX }
    val texts = listOf(c.tastes, c.hobbies, c.allergies, c.diet, c.avoid).all { it.length <= MemberCard.TEXT_MAX }
    return c.takeIf { sizes && texts && c.now.length <= MemberCard.NOW_MAX }
}
