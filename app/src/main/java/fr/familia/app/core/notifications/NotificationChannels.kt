package fr.familia.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import fr.familia.app.R
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.FeatureFlags

/**
 * One channel per kind of notification (PRD, récit 6 quinquies), so each can be turned off in the
 * Android settings. Their ids match the `channel` sent by the Cloud Functions (Channel in
 * firebase/functions/src/logic.ts). An id never changes once created: Android keeps the member's
 * choice under it. `events` is the « Planning » channel. A channel is created with its module only:
 * an empty category in the settings would puzzle the family. Each module thread adds its own
 * (goodNews, helpRequests, trips, comments, weekly: PRD récit 6 quinquies), with the same id in
 * Channel of logic.ts.
 */
object NotificationChannels {
    const val EVENTS = "events"
    const val REMINDERS = "reminders"
    const val GIFTS = "gifts"
    const val HELP_REQUESTS = "helpRequests"
    const val GOOD_NEWS = "goodNews"
    const val TRIPS = "trips"
    const val COMMENTS = "comments"
    const val WEEKLY = "weekly"

    /** [feature]: the module that uses the channel; it is created only once that module shows. */
    private data class Spec(val id: String, val name: Int, val description: Int, val importance: Int, val feature: Feature? = null)

    private val specs = listOf(
        Spec(EVENTS, R.string.channel_events, R.string.channel_events_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(REMINDERS, R.string.channel_reminders, R.string.channel_reminders_description, NotificationManager.IMPORTANCE_HIGH),
        Spec(GIFTS, R.string.channel_gifts, R.string.channel_gifts_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Gifts),
        Spec(HELP_REQUESTS, R.string.channel_help_requests, R.string.channel_help_requests_description, NotificationManager.IMPORTANCE_HIGH, Feature.HelpRequests),
        Spec(GOOD_NEWS, R.string.channel_good_news, R.string.channel_good_news_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.GoodNews),
        Spec(TRIPS, R.string.channel_trips, R.string.channel_trips_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Trips),
        // Grouped reactions to one's Bonnes nouvelles for now (PRD rév. 174); later the comments too.
        Spec(COMMENTS, R.string.channel_comments, R.string.channel_comments_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.GoodNews),
        // The question of the week for now (récit 37); later the other weekly rendez-vous (AVIS-14).
        Spec(WEEKLY, R.string.channel_weekly, R.string.channel_weekly_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.WeeklyQuestion),
    )

    /** Every id the app knows: a notice for another channel falls back on [EVENTS]. */
    val ids: Set<String> = specs.map { it.id }.toSet()

    /**
     * Creates the channels of the modules that show: at start the planning ones only, then again
     * each time the modules change ([features]). A channel stays once created: deleting it would
     * lose the member's choice.
     */
    fun create(context: Context, features: FeatureFlags = FeatureFlags()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Creating an existing channel only updates its name and description, never the member's choice.
        manager.createNotificationChannels(
            specs.filter { spec -> spec.feature == null || features.isOn(spec.feature) }.map { spec ->
                NotificationChannel(spec.id, context.getString(spec.name), spec.importance)
                    .apply { description = context.getString(spec.description) }
            },
        )
    }
}
