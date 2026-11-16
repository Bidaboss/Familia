package fr.familia.app.core.model

/**
 * The card of a member (PRD, récit 7 bis): sizes, shoe size, allergies and tastes, so the family
 * picks the right present and the right menu. Every field is free text (« 38 », « M », « 10 ans »),
 * blank when unknown. Every member of the family reads it.
 */
data class MemberCard(
    val memberId: String,
    val top: String = "",
    val bottom: String = "",
    val shoes: String = "",
    val allergies: String = "",
    val tastes: String = "",
) {
    val isEmpty: Boolean get() = listOf(top, bottom, shoes, allergies, tastes).all { it.isBlank() }

    companion object {
        /** The longest size or shoe size, « 12-14 ans » or « 42 ½ »: the rules check it too. */
        const val SIZE_MAX = 20

        /** The longest allergies or tastes. */
        const val TEXT_MAX = 300
    }
}

/** The card as typed: each field trimmed, too long ones refused (null), so nothing is cut silently. */
fun MemberCard.cleaned(): MemberCard? {
    val c = copy(top = top.trim(), bottom = bottom.trim(), shoes = shoes.trim(), allergies = allergies.trim(), tastes = tastes.trim())
    val sizesOk = listOf(c.top, c.bottom, c.shoes).all { it.length <= MemberCard.SIZE_MAX }
    val textsOk = listOf(c.allergies, c.tastes).all { it.length <= MemberCard.TEXT_MAX }
    return c.takeIf { sizesOk && textsOk }
}
