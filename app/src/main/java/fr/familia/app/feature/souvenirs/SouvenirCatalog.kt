package fr.familia.app.feature.souvenirs

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.R
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters

/**
 * The families of souvenirs ([DESIGN], SouvenirMedal): a light tint for the medal and a dark one
 * for the icon and the badge, over 4.5:1. They stay the same in every theme.
 */
enum class SouvenirFamily(val light: Color, val dark: Color) {
    Share(Color(0xFFF2B33D), Color(0xFF6B4200)),
    Help(Color(0xFF8DB58A), Color(0xFF163A19)),
    Trips(Color(0xFF7FB6D6), Color(0xFF0F3B57)),
    Loyalty(Color(0xFFE79A6E), Color(0xFF6A2508)),
    /** Fêtes: the challenges of the family (récit 35), the same image everywhere. */
    Party(Color(0xFFC9A2D6), Color(0xFF4A1F5C)),
}

/** The souvenirs of the server (functions/src/souvenirs.ts), by key, in the order they show. */
enum class SouvenirKind(
    val key: String,
    val family: SouvenirFamily,
    val icon: ImageVector,
    @StringRes val badge: Int,
    @StringRes val title: Int,
    /** What is left to do, before it is earned: a plural of the gestures left, or a sentence. */
    @PluralsRes val leftPlural: Int? = null,
    @StringRes val hint: Int? = null,
    val counter: ((SouvenirCounters) -> Int)? = null,
    val threshold: Int = 1,
) {
    FirstGoodNews("firstGoodNews", SouvenirFamily.Share, Icons.Rounded.Campaign, R.string.souvenir_badge_first, R.string.souvenir_first_good_news, hint = R.string.souvenir_hint_first_good_news),
    Reactions10("reactions10", SouvenirFamily.Share, Icons.Rounded.Favorite, R.string.souvenir_badge_10, R.string.souvenir_reactions_10, leftPlural = R.plurals.souvenir_left_reactions, counter = { it.reactions }, threshold = 10),
    FirstWeeklyAnswer("firstWeeklyAnswer", SouvenirFamily.Share, Icons.Rounded.ChatBubble, R.string.souvenir_badge_first, R.string.souvenir_first_weekly_answer, hint = R.string.souvenir_hint_first_weekly_answer),
    WeeklyAnswers10("weeklyAnswers10", SouvenirFamily.Share, Icons.Rounded.Forum, R.string.souvenir_badge_10, R.string.souvenir_weekly_answers_10, leftPlural = R.plurals.souvenir_left_weekly_answers, counter = { it.weeklyAnswers }, threshold = 10),
    HelpGiven5("helpGiven5", SouvenirFamily.Help, Icons.Rounded.VolunteerActivism, R.string.souvenir_badge_5, R.string.souvenir_help_given_5, leftPlural = R.plurals.souvenir_left_help, counter = { it.helpGiven }, threshold = 5),
    FirstArrival("firstArrival", SouvenirFamily.Trips, Icons.Rounded.WhereToVote, R.string.souvenir_badge_first, R.string.souvenir_first_arrival, hint = R.string.souvenir_hint_first_arrival),
    OneYear("oneYear", SouvenirFamily.Loyalty, Icons.Rounded.Cake, R.string.souvenir_badge_year, R.string.souvenir_one_year, hint = R.string.souvenir_hint_one_year),
    ;

    /** Gestures still to do; null for a souvenir earned in one go. */
    fun left(counters: SouvenirCounters): Int? = counter?.let { (threshold - it(counters)).coerceAtLeast(1) }

    companion object {
        fun of(key: String): SouvenirKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The souvenirs to show on one's own page: every one earned, and the next one of each family not
 * earned yet (never a global count nor a percentage).
 */
fun nextSouvenirs(earned: List<Souvenir>): List<SouvenirKind> {
    val have = earned.map { it.key }.toSet()
    return SouvenirFamily.entries.mapNotNull { family -> SouvenirKind.entries.firstOrNull { it.family == family && it.key !in have } }
}
