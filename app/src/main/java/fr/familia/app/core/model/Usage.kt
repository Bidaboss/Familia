package fr.familia.app.core.model

import androidx.annotation.StringRes
import fr.familia.app.R
import java.time.LocalDate

/**
 * The parts of the app whose time is counted (PRD, récit 1 nonies). A screen opened from a tab
 * counts for that tab: an event for Planning, a gift list for Cadeaux. Only these keys reach
 * Firestore: never what the member read, wrote or opened.
 */
enum class UsageScreen(val key: String, @StringRes val labelRes: Int) {
    Home("home", R.string.nav_home),
    Planning("planning", R.string.nav_planning),
    Gifts("gifts", R.string.nav_gifts),
    HelpRequests("help", R.string.nav_help_requests),
    GoodNews("goodNews", R.string.usage_screen_good_news),
    WeeklyQuestion("weeklyQuestion", R.string.usage_screen_weekly_question),
    Trips("trips", R.string.usage_screen_trips),
    Family("family", R.string.nav_members),
    Other("other", R.string.usage_screen_other),
    ;

    companion object {
        fun fromKey(key: String): UsageScreen? = entries.firstOrNull { it.key == key }

        /**
         * The part of the app of a navigation route, such as
         * « fr.familia.app.core.navigation.EventDetailDestination/{eventId} ». The longer names
         * come first: « HelpRequest… » is Entraide while « HelpDestination » is the help page of Famille.
         */
        fun forRoute(route: String?): UsageScreen? {
            val name = route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.') ?: return null
            return when {
                name.startsWith("HelpRequest") -> HelpRequests
                name.startsWith("Home") -> Home
                name.startsWith("Planning") || name.startsWith("Event") || name.startsWith("Poll") -> Planning
                name.startsWith("Gift") || name.startsWith("Birthday") || name.startsWith("Notebook") -> Gifts
                name.startsWith("GoodNews") -> GoodNews
                name.startsWith("WeeklyQuestion") -> WeeklyQuestion
                name.startsWith("Trip") -> Trips
                name.startsWith("Member") || name.startsWith("Appearance") || name.startsWith("NotificationSettings") ||
                    name.startsWith("Export") || name.startsWith("Administration") || name.startsWith("Help") ||
                    name.startsWith("UpdateCheck") || name.startsWith("Usage") -> Family
                else -> Other
            }
        }
    }
}

/** One day of use on one phone: how many times the app was opened and the seconds per screen key. */
data class DayUsage(val opens: Int = 0, val seconds: Map<String, Int> = emptyMap()) {
    val totalSeconds: Int get() = seconds.values.sum()

    fun withSeconds(screen: UsageScreen, added: Int): DayUsage =
        if (added <= 0) this else copy(seconds = seconds + (screen.key to ((seconds[screen.key] ?: 0) + added).coerceAtMost(MAX_SECONDS)))

    fun withOpen(): DayUsage = copy(opens = (opens + 1).coerceAtMost(MAX_OPENS))

    companion object {
        /** The bounds the security rules also check. */
        const val MAX_SECONDS = 86_400
        const val MAX_OPENS = 500
    }
}

/** A day of use as stored in usage/{memberId}/days/{day}. */
data class UsageDay(val memberId: String, val day: LocalDate, val usage: DayUsage)
