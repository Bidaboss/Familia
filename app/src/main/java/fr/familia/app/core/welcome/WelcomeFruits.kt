package fr.familia.app.core.welcome

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.memberInitials
import fr.familia.app.core.ui.memberColor
import fr.familia.app.core.ui.pickGroups
import kotlin.math.PI
import kotlin.math.atan2

/**
 * What plays at each opening (retour de Kévin, 17/11), chosen in Apparence and kept on the phone.
 * [Long] is the welcome with the family (the default), [Short] the tree of the start screen alone,
 * [None] nothing: the app opens as soon as it is ready.
 */
enum class OpeningStyle(val key: String) {
    Long("long"),
    Short("short"),
    None("none"),
    ;

    companion object {
        fun of(key: String?): OpeningStyle = entries.firstOrNull { it.key == key } ?: Long
    }
}

/**
 * The opening chosen on this phone, and what the welcome last learned (spec DESIGN « animation
 * d'ouverture », 17/11). Kept on the phone only, whoever signs in.
 */
class WelcomePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    var style: OpeningStyle
        get() = OpeningStyle.of(prefs.getString(STYLE_KEY, null))
        set(value) = prefs.edit { putString(STYLE_KEY, value.key) }

    var seen: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    /** The module was last read off for this member: the next opening waits less for it. */
    var knownOff: Boolean
        get() = prefs.getBoolean(OFF_KEY, false)
        set(value) = prefs.edit { putBoolean(OFF_KEY, value) }

    private companion object {
        const val KEY = "welcomeAnimationSeen"
        const val OFF_KEY = "welcomeAnimationOff"
        const val STYLE_KEY = "openingStyle"
    }
}

/** One fruit of the icon's tree, in the 108 × 108 grid of the launcher icon. */
internal data class TreeFruit(val center: Offset, val radius: Float, val color: Color)

/** The 13 fruits of « l'olivier au soleil » (ic_launcher_foreground.xml), all of the same size since 18/11, with their colors. */
internal val TREE_FRUITS: List<TreeFruit> = listOf(
    TreeFruit(Offset(48.5f, 51f), 4.9f, Color(0xFF3E6B8A)),
    TreeFruit(Offset(59.5f, 51f), 4.9f, Color(0xFF9B4E8C)),
    TreeFruit(Offset(38.965f, 55.472f), 4.9f, Color(0xFFD9603B)),
    TreeFruit(Offset(38.965f, 44.528f), 4.9f, Color(0xFFE0A21F)),
    TreeFruit(Offset(44.823f, 36.894f), 4.9f, Color(0xFF2F8A6E)),
    TreeFruit(Offset(63.177f, 36.894f), 4.9f, Color(0xFF5562B5)),
    TreeFruit(Offset(69.035f, 44.528f), 4.9f, Color(0xFFC2456A)),
    TreeFruit(Offset(69.035f, 55.472f), 4.9f, Color(0xFF7A55B0)),
    TreeFruit(Offset(32.368f, 38.498f), 4.9f, Color(0xFF2D7FA8)),
    TreeFruit(Offset(42.498f, 28.368f), 4.9f, Color(0xFFF2B33D)),
    TreeFruit(Offset(65.502f, 28.368f), 4.9f, Color(0xFFE2574C)),
    TreeFruit(Offset(75.632f, 38.498f), 4.9f, Color(0xFFE27A3E)),
    TreeFruit(Offset(54f, 25.5f), 4.9f, Color(0xFF3BA39A)),
)

/** The same fruits, clockwise from the top, like the places on the circle. */
internal val TREE_SLOTS: List<TreeFruit> = TREE_FRUITS.sortedBy { clockwiseFromTop(it.center) }

/**
 * A member's fruit: [tree] is where it grows on the tree (null past the 13 fruits of the icon: it
 * comes out of the sun), and [place] its place on the circle, from 0 (at the top) clockwise.
 */
internal data class WelcomeFruit(
    val memberId: String,
    val initials: String,
    val color: Color,
    val tree: TreeFruit?,
    val place: Int,
)

/**
 * As many fruits as members, my household first then household by household (the rule of every
 * list of members, 17/11). The fruits of the tree are taken clockwise from the top, like the places
 * on the circle, so that no fruit crosses the others on its way.
 */
internal fun welcomeFruits(family: Family, me: Member): List<WelcomeFruit> {
    val ordered = pickGroups(family.members, family.households, me.householdId).flatMap { it.members }
    val initials = memberInitials(family.members)
    return ordered.mapIndexed { index, member ->
        WelcomeFruit(
            memberId = member.id,
            initials = initials[member.id] ?: member.firstName.take(1).uppercase(),
            color = memberColor(member),
            tree = TREE_SLOTS.getOrNull(index),
            place = index,
        )
    }
}

/** The angle around the sun (54, 54), from the top and clockwise, between 0 and 2π. */
internal fun clockwiseFromTop(point: Offset): Double {
    val angle = atan2((point.x - 54f).toDouble(), (54f - point.y).toDouble())
    return if (angle < 0) angle + 2 * PI else angle
}
