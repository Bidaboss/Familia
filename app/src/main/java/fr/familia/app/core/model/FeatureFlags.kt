package fr.familia.app.core.model

import androidx.annotation.StringRes
import fr.familia.app.R

/**
 * Modules that can stay hidden from the family until they are finished and reviewed (PRD rév. 120:
 * the version shown on 21 November only shows what works). Each [key] is a field of
 * config/app.features. Every [dev] thread adds its own entry here, nothing else.
 */
enum class Feature(val key: String, @StringRes val labelRes: Int) {
    Home("home", R.string.feature_home),
    Gifts("gifts", R.string.feature_gifts),
    GiftNotebook("giftNotebook", R.string.feature_gift_notebook),
    HelpRequests("helpRequests", R.string.feature_help_requests),
    Trips("trips", R.string.feature_trips),
    GoodNews("goodNews", R.string.feature_good_news),
    Themes("themes", R.string.feature_themes),

    /**
     * Counting the time spent in the app (PRD, récit 1 nonies). Unlike the others, the switch
     * also governs the admin principal: nothing is counted until it is on for the family, once
     * Kévin has announced it.
     */
    Usage("usage", R.string.feature_usage),
    WeeklyQuestion("weeklyQuestion", R.string.feature_weekly_question),
    EventAlbums("eventAlbums", R.string.feature_event_albums),

    /** « Ajouter à mon agenda » on an event and a birthday (PRD, récit 30). */
    Calendar("calendar", R.string.feature_calendar),

    Souvenirs("souvenirs", R.string.feature_souvenirs),
    /** Sondage de date et sondage libre (PRD, récits 2 bis et 2 ter). */
    Polls("polls", R.string.feature_polls),
}

/**
 * Which modules the current member sees. A module is on for the family once switched on in
 * Administration; the admin principal always sees every module, to try it first.
 */
data class FeatureFlags(val switchedOn: Map<String, Boolean> = emptyMap(), val isOwner: Boolean = false) {
    fun isOn(feature: Feature): Boolean = isOwner || switchedOn[feature.key] == true

    /** Switched on for the whole family (what Administration shows). */
    fun isOnForFamily(feature: Feature): Boolean = switchedOn[feature.key] == true
}
