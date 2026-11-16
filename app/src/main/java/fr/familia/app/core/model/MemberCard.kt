package fr.familia.app.core.model

/**
 * The card of a member (PRD, récit 7 bis): their shoe size, tastes and hobbies or passions, so the
 * family picks the right present. Clothing sizes and allergies were dropped after the first tests
 * (nobody fills them in). Free texts, blank when unknown. Every member of the family reads it.
 */
data class MemberCard(
    val memberId: String,
    val shoes: String = "",
    val tastes: String = "",
    val hobbies: String = "",
) {
    val isEmpty: Boolean get() = shoes.isBlank() && tastes.isBlank() && hobbies.isBlank()

    companion object {
        /** The longest shoe size, « 42 ½ » or « 30-31 »: the rules check it too. */
        const val SIZE_MAX = 20

        /** The longest tastes or hobbies: the rules check it too. */
        const val TEXT_MAX = 300
    }
}

/** The card as typed: each field trimmed, too long ones refused (null), so nothing is cut silently. */
fun MemberCard.cleaned(): MemberCard? {
    val c = copy(shoes = shoes.trim(), tastes = tastes.trim(), hobbies = hobbies.trim())
    return c.takeIf {
        c.shoes.length <= MemberCard.SIZE_MAX && c.tastes.length <= MemberCard.TEXT_MAX && c.hobbies.length <= MemberCard.TEXT_MAX
    }
}
