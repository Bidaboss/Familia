package fr.familia.app.feature.discover

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.edit
import fr.familia.app.R
import fr.familia.app.core.model.Feature
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A module that « À découvrir » shows for one week on the Accueil (AVIS-19, PRD récit 29), in this
 * default order. [feature] is the switch it opens: the card stays away while it is off. A child
 * reads [childTitleRes] and [childBodyRes] where they are set: no accounts, no offer of help.
 */
enum class DiscoverModule(
    val key: String,
    val feature: Feature,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val childTitleRes: Int = titleRes,
    @StringRes val childBodyRes: Int = bodyRes,
) {
    WeeklyQuestion("weeklyQuestion", Feature.WeeklyQuestion, R.string.discover_weekly_question_title, R.string.discover_weekly_question_body),
    HelpRequests(
        "helpRequests",
        Feature.HelpRequests,
        R.string.discover_help_requests_title,
        R.string.discover_help_requests_body,
        childBodyRes = R.string.discover_help_requests_body_child,
    ),

    /** Les sondages, and « Qui apporte quoi » that comes with an event. */
    Polls("polls", Feature.Polls, R.string.discover_polls_title, R.string.discover_polls_body),
    Recipes("recipes", Feature.Recipes, R.string.discover_recipes_title, R.string.discover_recipes_body),

    /** Les voyages and their accounts. */
    Trips(
        "trips",
        Feature.Trips,
        R.string.discover_trips_title,
        R.string.discover_trips_body,
        childTitleRes = R.string.discover_trips_title_child,
        childBodyRes = R.string.discover_trips_body_child,
    ),
    Capsules("capsules", Feature.Capsules, R.string.discover_capsules_title, R.string.discover_capsules_body),
    Genealogy("genealogy", Feature.Genealogy, R.string.discover_genealogy_title, R.string.discover_genealogy_body),
}

object Discover {
    /** The first card, the question of the week, on Monday 30/11/2026; then one module a week. */
    val FIRST_MONDAY: LocalDate = LocalDate.of(2026, 11, 30)
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /**
     * The module of the week of [today], or null: before the first Monday, once the list is done,
     * while its switch is off or once I opened it or touched « Plus tard » ([dismissed] keys).
     * The modules of the tutorial ([inTutorial]) are left out of the list, so the next one moves up.
     */
    fun moduleOfTheWeek(
        today: LocalDate,
        isOn: (Feature) -> Boolean,
        inTutorial: Set<DiscoverModule> = emptySet(),
        dismissed: Set<String> = emptySet(),
        order: List<DiscoverModule> = DiscoverModule.entries,
    ): DiscoverModule? {
        if (today.isBefore(FIRST_MONDAY)) return null
        val week = ChronoUnit.WEEKS.between(FIRST_MONDAY, today).toInt()
        val module = order.filterNot { it in inTutorial }.getOrNull(week) ?: return null
        return module.takeIf { isOn(it.feature) && it.key !in dismissed }
    }

    /**
     * The order the admin principal chose ([keys] of config/app.discoverOrder, AVIS-19 partie 2): the
     * known keys first, once each, then the modules left out in the default order. Empty: the default.
     */
    fun order(keys: List<String>): List<DiscoverModule> {
        val chosen = keys.distinct().mapNotNull { key -> DiscoverModule.entries.firstOrNull { it.key == key } }
        return chosen + DiscoverModule.entries.filterNot { it in chosen }
    }

    /** The Monday each module of [order] shows, or null for those the tutorial already shows. */
    fun mondays(order: List<DiscoverModule>, inTutorial: Set<DiscoverModule>): Map<DiscoverModule, LocalDate> =
        order.filterNot { it in inTutorial }.mapIndexed { i, module -> module to FIRST_MONDAY.plusWeeks(i.toLong()) }.toMap()

    /** The modules the tutorial already shows (AVIS-18): Entraide, while its module is on. */
    fun inTutorial(isOn: (Feature) -> Boolean): Set<DiscoverModule> =
        setOfNotNull(DiscoverModule.HelpRequests.takeIf { isOn(Feature.HelpRequests) })
}

/** The order of « À découvrir » from config/app, provided by FamiliaApp; the default elsewhere. */
val LocalDiscoverOrder = compositionLocalOf { DiscoverModule.entries.toList() }

/** The modules a member opened or put off, kept on the phone. */
interface DismissedDiscoveries {
    fun load(memberId: String): Set<String>
    fun save(memberId: String, keys: Set<String>)
}

class DiscoverPreferences(context: Context) : DismissedDiscoveries {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)
    override fun load(memberId: String): Set<String> = prefs.getStringSet(key(memberId), emptySet()).orEmpty().toSet()
    override fun save(memberId: String, keys: Set<String>) = prefs.edit { putStringSet(key(memberId), keys) }
    private fun key(memberId: String) = "discoverDismissed-$memberId"
}
