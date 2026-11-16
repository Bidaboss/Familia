package fr.familia.app.core.model

import java.time.YearMonth

/**
 * L'arbre de la famille (PRD, récit 35): familyTree/current, written by the server only. It never
 * shrinks, and holds no name (carte EXPERT-3). See docs/data-model.md.
 */
data class FamilyTree(val leaves: Int = 0, val bloomedMonths: List<String> = emptyList()) {
    /** The visible level, from 0 (a sprout) to 6 (a big tree): the same steps as TREE_LEVELS of the server. */
    val level: Int get() = LEVELS.count { leaves >= it } - 1

    /** Leaves still to grow before the next level, or null at the top. */
    val leavesToNextLevel: Int? get() = LEVELS.firstOrNull { it > leaves }?.let { it - leaves }

    fun bloomed(month: YearMonth): Boolean = month.toString() in bloomedMonths

    companion object {
        val LEVELS = listOf(0, 20, 60, 120, 250, 500, 1000)
    }
}

/** The challenge of the month, challenges/{AAAA-MM}: its gauge moves by itself. */
data class MonthChallenge(
    val month: YearMonth,
    val title: String,
    val emoji: String,
    val target: Int,
    val progress: Int,
    val achieved: Boolean,
    /** The entry of the list it comes from (ChallengeCatalog); null for an older challenge. */
    val choiceId: String? = null,
) {
    val fraction: Float get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** The tree dresses for the season: blossom, green, orange or bare with snow. */
enum class TreeSeason {
    Spring, Summer, Autumn, Winter;

    companion object {
        fun of(month: YearMonth): TreeSeason = when (month.monthValue) {
            3, 4, 5 -> Spring
            6, 7, 8 -> Summer
            9, 10, 11 -> Autumn
            else -> Winter
        }
    }
}
