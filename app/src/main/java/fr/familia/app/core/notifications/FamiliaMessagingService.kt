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
        // Souvenirs: one's own page.
        val souvenirKey = message.data[EXTRA_SOUVENIR_KEY]
        souvenirKey?.let { open.putExtra(EXTRA_SOUVENIR_KEY, it) }
        // Sondages: the poll to open.
        val pollId = message.data[EXTRA_POLL_ID]
        pollId?.let { open.putExtra(EXTRA_POLL_ID, it) }
        // Rejoignez-moi: the Accueil, where its card is.
        val joinMeId = message.data[EXTRA_JOIN_ME_ID]
        joinMeId?.let { open.putExtra(EXTRA_JOIN_ME_ID, it) }
        // Capsule temporelle: the capsule that opened.
        val capsuleId = message.data[EXTRA_CAPSULE_ID]
        capsuleId?.let { open.putExtra(EXTRA_CAPSULE_ID, it) }
        // La semaine de la famille: its screen (récit 36).
        val recapWeek = message.data[EXTRA_RECAP_WEEK]
        recapWeek?.let { open.putExtra(EXTRA_RECAP_WEEK, it) }
        // Vœux d'anniversaire: the page of the member celebrated (récit 36).
        val birthdayOf = message.data[EXTRA_BIRTHDAY_OF]
        birthdayOf?.let { open.putExtra(EXTRA_BIRTHDAY_OF, it) }
        // La bienvenue: the page of the newcomer (récit 43).
        val welcomeOf = message.data[EXTRA_WELCOME_OF]
        welcomeOf?.let { open.putExtra(EXTRA_WELCOME_OF, it) }
        val thinkOfMeOf = message.data[EXTRA_THINK_OF_ME_OF]
        thinkOfMeOf?.let { open.putExtra(EXTRA_THINK_OF_ME_OF, it) }
        // « Une idée ? Un souci ? » (récit 6 sexies): Retours or « Mes demandes ».
        val feedback = message.data[EXTRA_FEEDBACK]
        feedback?.let { open.putExtra(EXTRA_FEEDBACK, it) }
        val pending = PendingIntent.getActivity(
            this,
            (eventId ?: listId ?: helpRequestId ?: tripId ?: goodNewsId ?: weeklyQuestionId ?: souvenirKey ?: pollId ?: joinMeId ?: capsuleId ?: recapWeek ?: birthdayOf ?: welcomeOf ?: thinkOfMeOf ?: feedback).hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = notification.channelId?.takeIf { it in NotificationChannels.ids } ?: NotificationChannels.EVENTS
        val built = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(
                if (channel in NotificationChannels.privateIds) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC,
            )
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // Notices with the same tag replace each other: the cards of vœux of the day (récit 36).
        val tag = message.data[EXTRA_NOTIFICATION_TAG]
        if (tag != null) {
            NotificationManagerCompat.from(this).notify(tag, 0, built)
        } else {
            NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), built)
        }
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
        const val EXTRA_SOUVENIR_KEY = "souvenirKey"

        /** Same key as the `data` of functions/src/polls.ts. */
        const val EXTRA_POLL_ID = "pollId"
        const val EXTRA_JOIN_ME_ID = "joinMeId"

        /** Same key as the `data` of functions/src/index.ts for a capsule notice. */
        const val EXTRA_CAPSULE_ID = "capsuleId"

        /** The Sunday of « La semaine de la famille », same key as functions/src/recap.ts. */
        const val EXTRA_RECAP_WEEK = "recapWeek"

        /** The member celebrated, same key as functions/src/birthdayWishes.ts. */
        const val EXTRA_BIRTHDAY_OF = "birthdayOf"
        const val EXTRA_WELCOME_OF = "welcomeOf"
        const val EXTRA_THINK_OF_ME_OF = "thinkOfMeOf"
        const val EXTRA_NOTIFICATION_TAG = "notificationTag"
        /** "list", "mine" or "crashes", same key as functions/src/feedback.ts and crashes.ts. */
        const val EXTRA_FEEDBACK = "feedback"
    }
}
