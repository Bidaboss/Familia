package fr.familia.app.core.model

import java.time.YearMonth

/**
 * The order in which the foyers launch the challenge, a month each from [startMonth]
 * (challengeRotation/current, récit 35 bis). The admin principal sets it in Administration.
 */
data class ChallengeRotation(val householdIds: List<String>, val startMonth: YearMonth) {
    /** The foyer whose turn [month] is, or null before the order starts. Same as hostOf in the rules and the server. */
    fun hostOf(month: YearMonth): String? {
        if (householdIds.isEmpty() || month < startMonth) return null
        val months = (month.year - startMonth.year) * 12 + (month.monthValue - startMonth.monthValue)
        return householdIds[months % householdIds.size]
    }

    companion object {
        /**
         * The order Administration shows in [month]: the foyers in their turn from [Schedule.start],
         * a month each. An order already set starts with the foyer of [month], so that saving it
         * unchanged changes nothing; a first order starts next month, so nobody is caught short.
         * The foyers deleted since are left out, the new ones come last, by name.
         */
        fun schedule(rotation: ChallengeRotation?, households: List<Household>, month: YearMonth): Schedule {
            val byId = households.associateBy { it.id }
            val current = rotation?.hostOf(month)
            val start = if (current != null) month else month.plusMonths(1)
            val turn = rotation?.householdIds.orEmpty().let { ids ->
                val from = rotation?.let { r -> r.hostOf(start)?.let { ids.indexOf(it) } } ?: 0
                if (ids.isEmpty() || from < 0) ids else ids.drop(from) + ids.take(from)
            }
            val saved = turn.mapNotNull { byId[it] }.distinctBy { it.id }
            val others = households.filter { h -> saved.none { it.id == h.id } }.sortedBy { it.name.lowercase() }
            return Schedule(start, saved + others, isSaved = rotation != null && others.isEmpty() && saved.size == rotation.householdIds.size)
        }
    }

    /** The order as Administration shows it: [households] launch the challenge from [start], a month each. */
    data class Schedule(val start: YearMonth, val households: List<Household>, val isSaved: Boolean)
}

enum class HostedStatus(val key: String) {
    Draft("draft"), Published("published"), Skipped("skipped");

    companion object {
        fun of(key: String?): HostedStatus? = entries.firstOrNull { it.key == key }
    }
}

/** The kinds of challenge of the guide (step 1): all within everyone's reach, nothing to buy. */
enum class HostedKind(val key: String) {
    Photo("photo"), Outing("outing"), Cooking("cooking"), Kindness("kindness"), Creative("creative");

    companion object {
        fun of(key: String?): HostedKind? = entries.firstOrNull { it.key == key }
    }
}

/** The Argile illustrations of the card (step 3). */
enum class HostedIllustration(val key: String) {
    Sun("sun"), Heart("heart"), Leaf("leaf"), Star("star"), Camera("camera"), Pot("pot");

    companion object {
        fun of(key: String?): HostedIllustration? = entries.firstOrNull { it.key == key }
    }
}

/** householdChallenges/{AAAA-MM}: the challenge a foyer prepares for its month. */
data class HostedChallenge(
    val month: YearMonth,
    val householdId: String,
    val status: HostedStatus,
    val kind: HostedKind,
    val title: String,
    val emoji: String,
    val illustration: HostedIllustration,
    val target: Int,
) {
    companion object {
        const val TITLE_MAX = 60
        const val TARGET_MIN = 3
        const val TARGET_MAX = 50
    }
}
