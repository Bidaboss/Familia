package fr.familia.app.core.model

import java.time.Instant

/** A turn done (« Noël 2026 chez … »): the household, an optional label, when it was said. */
data class TurnDone(val householdId: String, val label: String, val at: Instant)

/** An exchange of places proposed by [byName] for household [fromId] with household [toId]. */
data class TurnSwap(
    val fromId: String,
    val toId: String,
    val fromIndex: Int,
    val toIndex: Int,
    val byId: String,
    val byName: String,
)

/**
 * A rotation between households (« À qui le tour ? », PRD récit 58), in the order its author
 * chose. [current] is the place in [householdIds] of the household whose turn it is; [history] the
 * turns done, the newest last. No counter, no ranking.
 */
data class Turn(
    val id: String,
    val title: String,
    val householdIds: List<String>,
    val current: Int,
    val history: List<TurnDone>,
    val swap: TurnSwap?,
    val createdBy: String,
) {
    /** The household whose turn it is. */
    val currentHouseholdId: String? get() = householdIds.getOrNull(current)

    /** The household after it, round the rotation. */
    val nextHouseholdId: String?
        get() = if (householdIds.size < 2) null else householdIds[(current + 1) % householdIds.size]

    /** « C'est fait »: the turn goes to the next household, with one line more in the history. */
    fun done(label: String, at: Instant): Turn {
        val household = currentHouseholdId ?: return this
        return copy(
            history = history + TurnDone(household, label.trim().take(LABEL_MAX), at),
            current = (current + 1) % householdIds.size,
        )
    }

    /** « On passe notre tour »: the household whose turn it is changes place with the next one. */
    fun skipped(): Turn {
        if (householdIds.size < 2) return this
        return copy(householdIds = exchanged(current, (current + 1) % householdIds.size), swap = null)
    }

    /** An exchange proposed by [me] for their household with [toHouseholdId]; null when it cannot be. */
    fun swapAsk(me: Member, toHouseholdId: String): TurnSwap? {
        val from = householdIds.indexOf(me.householdId)
        val to = householdIds.indexOf(toHouseholdId)
        if (from < 0 || to < 0 || from == to || swap != null) return null
        return TurnSwap(me.householdId, toHouseholdId, from, to, me.id, me.firstName)
    }

    /** The exchange accepted: the two households change places. */
    fun swapAccepted(): Turn {
        val s = swap ?: return this
        if (householdIds.getOrNull(s.fromIndex) != s.fromId || householdIds.getOrNull(s.toIndex) != s.toId) return copy(swap = null)
        return copy(householdIds = exchanged(s.fromIndex, s.toIndex), swap = null)
    }

    private fun exchanged(i: Int, j: Int): List<String> =
        householdIds.toMutableList().also { val a = it[i]; it[i] = it[j]; it[j] = a }

    companion object {
        const val TITLE_MAX = 60
        const val LABEL_MAX = 40
        const val HOUSEHOLDS_MIN = 2
        const val HOUSEHOLDS_MAX = 12
    }
}
