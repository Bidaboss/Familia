package fr.familia.app.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.familia.app.MainActivity
import fr.familia.app.R

/**
 * Receives the notifications of the Cloud Functions. When the app is closed, Android shows them by
 * itself; this class only shows them while the app is open, which Android does not do.
 */
class FamiliaMessagingService : FirebaseMessagingService() {

    @SuppressLint("MissingPermission") // Checked just below for Android 13 and later.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val eventId = message.data[EXTRA_EVENT_ID]
        val listId = message.data[EXTRA_LIST_ID]
        val tripId = message.data[EXTRA_TRIP_ID]
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
                listId?.let { putExtra(EXTRA_LIST_ID, it) }
                tripId?.let { putExtra(EXTRA_TRIP_ID, it) }
            }
        // Coups de main: the request to open, when the notification is about one.
        val helpRequestId = message.data[EXTRA_HELP_REQUEST_ID]
        helpRequestId?.let { open.putExtra(EXTRA_HELP_REQUEST_ID, it) }
        // Bonnes nouvelles: the good news to open.
        val goodNewsId = message.data[EXTRA_GOOD_NEWS_ID]
        goodNewsId?.let { open.putExtra(EXTRA_GOOD_NEWS_ID, it) }
        // Question de la semaine: the question to open.
        val weeklyQuestionId = message.data[EXTRA_WEEKLY_QUESTION_ID]
        weeklyQuestionId?.let { open.putExtra(EXTRA_WEEKLY_QUESTION_ID, it) }
        val pending = PendingIntent.getActivity(
            this,
            (eventId ?: listId ?: helpRequestId ?: tripId ?: goodNewsId ?: weeklyQuestionId).hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(this, notification.channelId?.takeIf { it in NotificationChannels.ids } ?: NotificationChannels.EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), built)
    }

    /**
     * A new token arrives rarely (reinstall, cleared data). The app registers the current token
     * each time it opens (NotificationRepository.registerThisDevice), which covers it.
     */
    override fun onNewToken(token: String) = Unit

    companion object {
        /** Same key as the `data` of the Cloud Functions. */
        const val EXTRA_EVENT_ID = "eventId"

        /** A gift list to open (V2), same key as the `data` of the Cloud Functions. */
        const val EXTRA_LIST_ID = "listId"

        /** Same key as the `data` of functions/src/helpRequests.ts. */
        const val EXTRA_HELP_REQUEST_ID = "helpRequestId"

        /** Same key as the `data` of functions/src/goodNews.ts. */
        const val EXTRA_GOOD_NEWS_ID = "goodNewsId"
        /** The trip of a « bien arrivé » or of its reminder (V3). */
        const val EXTRA_TRIP_ID = "tripId"

        /** Same key as the `data` of functions/src/weeklyQuestion.ts. */
        const val EXTRA_WEEKLY_QUESTION_ID = "weeklyQuestionId"
    }
}
