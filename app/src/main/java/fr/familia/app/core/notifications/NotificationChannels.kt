package fr.familia.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import fr.familia.app.R

/**
 * The two kinds of notifications, so each can be turned off in the Android settings. Their ids
 * match the `channel` sent by the Cloud Functions (firebase/functions/src/logic.ts).
 */
object NotificationChannels {
    const val EVENTS = "events"
    const val REMINDERS = "reminders"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_events_description) },
                NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_reminders_description) },
            ),
        )
    }
}
