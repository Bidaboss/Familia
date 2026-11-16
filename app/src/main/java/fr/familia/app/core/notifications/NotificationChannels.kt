package fr.familia.app.core.notifications

import android.app.Notification
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
    const val ADMIN = "admin"
    const val SOUVENIRS = "souvenirs"
    const val TREE = "tree"
    const val RECAP = "recap"
    const val BIRTHDAYS = "birthdays"
    const val WELCOME = "welcome"
    const val FEEDBACK = "feedback"
    const val JOIN_ME = "joinMe"
    const val CAPSULES = "capsules"

    /**
     * [feature]: the module that uses the channel; it is created only once that module shows.
     * [ownerOnly]: a channel of the admin principal, never shown on the phones of the family.
     */
    private data class Spec(
        val id: String,
        val name: Int,
        val description: Int,
        val importance: Int,
        val feature: Feature? = null,
        val ownerOnly: Boolean = false,
        /** A second module that also uses the channel: either one creates it. */
        val orFeature: Feature? = null,
    )

    private val specs = listOf(
        Spec(EVENTS, R.string.channel_events, R.string.channel_events_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(REMINDERS, R.string.channel_reminders, R.string.channel_reminders_description, NotificationManager.IMPORTANCE_HIGH),
        Spec(GIFTS, R.string.channel_gifts, R.string.channel_gifts_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Gifts),
        Spec(HELP_REQUESTS, R.string.channel_help_requests, R.string.channel_help_requests_description, NotificationManager.IMPORTANCE_HIGH, Feature.HelpRequests),
        Spec(GOOD_NEWS, R.string.channel_good_news, R.string.channel_good_news_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.GoodNews),
        Spec(TRIPS, R.string.channel_trips, R.string.channel_trips_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Trips),
        // Grouped reactions to one's Bonnes nouvelles (PRD rév. 174) and the comments of an event (récit 28).
        Spec(
            COMMENTS, R.string.channel_comments, R.string.channel_comments_description, NotificationManager.IMPORTANCE_DEFAULT,
            Feature.GoodNews, orFeature = Feature.Comments,
        ),
        // The question of the week for now (récit 37); later the other weekly rendez-vous (AVIS-14).
        Spec(WEEKLY, R.string.channel_weekly, R.string.channel_weekly_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.WeeklyQuestion),
        // The consumption alert of the database (récit 1 sexies), for the admin principal only.
        Spec(ADMIN, R.string.channel_admin, R.string.channel_admin_description, NotificationManager.IMPORTANCE_DEFAULT, ownerOnly = true),
        Spec(SOUVENIRS, R.string.channel_souvenirs, R.string.channel_souvenirs_description, NotificationManager.IMPORTANCE_LOW, Feature.Souvenirs),
        Spec(TREE, R.string.channel_tree, R.string.channel_tree_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Tree),
        // « Rejoignez-moi » (récit 33): someone waits for me, so it rings.
        Spec(JOIN_ME, R.string.channel_join_me, R.string.channel_join_me_description, NotificationManager.IMPORTANCE_HIGH, Feature.Places),
        Spec(CAPSULES, R.string.channel_capsules, R.string.channel_capsules_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Capsules),
        // « La semaine de la famille » (récit 36): once a week, it does not ring.
        Spec(RECAP, R.string.channel_recap, R.string.channel_recap_description, NotificationManager.IMPORTANCE_LOW, Feature.Recap),
        // Vœux d'anniversaire (récit 36): « C'est l'anniversaire de … » and the cards received.
        Spec(BIRTHDAYS, R.string.channel_birthdays, R.string.channel_birthdays_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.BirthdayWishes),
        Spec(WELCOME, R.string.channel_welcome, R.string.channel_welcome_description, NotificationManager.IMPORTANCE_DEFAULT, Feature.Welcome),
        // « Une idée ? Un souci ? » (récit 6 sexies): the answer to one's own request. No module switch.
        Spec(FEEDBACK, R.string.channel_feedback, R.string.channel_feedback_description, NotificationManager.IMPORTANCE_DEFAULT),
    )

    /**
     * Channels that may hold a surprise (a gift, a time capsule): the lock screen shows only that a
     * notification came, never its text (audit 2.2). Same list as PRIVATE_CHANNELS in index.ts.
     */
    val privateIds: Set<String> = setOf(GIFTS, CAPSULES)

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
            specs.filter { spec ->
                (spec.feature == null || features.isOn(spec.feature) || (spec.orFeature != null && features.isOn(spec.orFeature))) &&
                    (!spec.ownerOnly || features.isOwner)
            }.map { spec ->
                NotificationChannel(spec.id, context.getString(spec.name), spec.importance)
                    .apply {
                        description = context.getString(spec.description)
                        if (spec.id in privateIds) lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
            },
        )
    }
}
