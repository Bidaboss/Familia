package fr.familia.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import fr.familia.app.R

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

    private data class Spec(val id: String, val name: Int, val description: Int, val importance: Int)

    private val specs = listOf(
        Spec(EVENTS, R.string.channel_events, R.string.channel_events_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(REMINDERS, R.string.channel_reminders, R.string.channel_reminders_description, NotificationManager.IMPORTANCE_HIGH),
        Spec(GIFTS, R.string.channel_gifts, R.string.channel_gifts_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(HELP_REQUESTS, R.string.channel_help_requests, R.string.channel_help_requests_description, NotificationManager.IMPORTANCE_HIGH),
        Spec(GOOD_NEWS, R.string.channel_good_news, R.string.channel_good_news_description, NotificationManager.IMPORTANCE_DEFAULT),
    )

    /** Every id the app knows: a notice for another channel falls back on [EVENTS]. */
    val ids: Set<String> = specs.map { it.id }.toSet()

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Creating an existing channel only updates its name and description, never the member's choice.
        manager.createNotificationChannels(
            specs.map { spec ->
                NotificationChannel(spec.id, context.getString(spec.name), spec.importance)
                    .apply { description = context.getString(spec.description) }
            },
        )
    }
}
