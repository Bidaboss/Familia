package fr.familia.app.core.share

import fr.familia.app.core.model.Trip
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.familia.app.core.photo.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Poll
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import java.time.LocalDate
import java.util.Locale

/*
 * What each item says once shared (PRD, récit 31). Nothing hidden goes out: no « qui vient », no
 * note, no answer of a poll, no reservation. Only what the image shows and the link.
 */

private fun lower(text: String) = text.replaceFirstChar { it.lowercase(Locale.FRANCE) }

/** « Samedi 21 novembre à 12 h 30 », or the day alone. */
private fun eventWhen(context: Context, event: Event, today: LocalDate): String {
    val day = formatDay(event.date, today)
    return event.time?.let { context.getString(R.string.share_when_time, day, formatTime(it)) } ?: day
}

fun eventShare(context: Context, event: Event, today: LocalDate = LocalDate.now()): ShareContent {
    val day = eventWhen(context, event, today)
    val place = event.place?.takeIf { it.isNotBlank() }
    val text = if (place != null) {
        context.getString(R.string.share_event_text_place, event.title, lower(day), place)
    } else {
        context.getString(R.string.share_event_text, event.title, lower(day))
    }
    return ShareContent(event.title, listOfNotNull(day, place), text, shareLink(ShareKind.Event, event.id))
}

fun pollShare(context: Context, poll: Poll): ShareContent = ShareContent(
    title = poll.question,
    lines = listOf(context.getString(R.string.share_poll_line)),
    text = context.getString(R.string.share_poll_text, poll.question),
    link = shareLink(ShareKind.Poll, poll.id),
)

/** A birthday, without the member's photo in this first version: a child's photo needs EXPERT-2. */
fun birthdayShare(context: Context, birthday: Birthday, today: LocalDate = LocalDate.now()): ShareContent {
    val name = birthday.member.firstName
    val age = context.resources.getQuantityString(R.plurals.birthday_age, birthday.age, birthday.age)
    val day = formatDay(birthday.date, today)
    return ShareContent(
        title = context.getString(R.string.planning_birthday_of, name),
        lines = listOf(day, age),
        text = context.getString(R.string.share_birthday_text, name, age, lower(day)),
        link = shareLink(ShareKind.Birthday, birthday.member.id),
    )
}

/**
 * A trip: its title and its dates only. Neither the participants, the steps, the arrivals nor the
 * photos go out; without a photo, the rule of carte EXPERT-2 has nothing to check.
 */
fun tripShare(context: Context, trip: Trip, today: LocalDate = LocalDate.now()): ShareContent {
    val start = lower(formatDay(trip.startDate, today))
    val dates = if (trip.startDate == trip.endDate) {
        context.getString(R.string.trip_dates_one_day, start)
    } else {
        context.getString(R.string.trip_dates, start, lower(formatDay(trip.endDate, today)))
    }
    return ShareContent(
        title = trip.title,
        lines = listOf(dates),
        text = context.getString(R.string.share_trip_text, trip.title, lower(dates)),
        link = shareLink(ShareKind.Trip, trip.id),
    )
}

/** A good news: whose it is and its text; [photo] only once [goodNewsPhotoAllowed] said yes (carte EXPERT-2). */
fun goodNewsShare(context: Context, news: GoodNews, whoseName: String, photo: Bitmap? = null): ShareContent {
    val title = context.getString(R.string.share_goodnews_title, whoseName)
    val text = news.text.trim()
    val excerpt = if (text.length > SHARE_EXCERPT_MAX) text.take(SHARE_EXCERPT_MAX).trimEnd() + "…" else text
    return ShareContent(
        title = title,
        lines = listOf(excerpt),
        text = context.getString(R.string.share_goodnews_text, title, excerpt),
        link = shareLink(ShareKind.GoodNews, news.id),
        photo = photo,
    )
}

/**
 * A coup de main still open: its kind, its title and when. Neither the place, the note, the
 * child helped nor who was warned goes out; the link opens it only for those it was asked to.
 */
fun helpRequestShare(context: Context, request: HelpRequest, today: LocalDate = LocalDate.now()): ShareContent {
    val day = formatDay(request.date, today)
    val start = request.startTime
    val end = request.endTime
    val whenText = when {
        start != null && end != null -> context.getString(R.string.helprequest_when_range, day, formatTime(start), formatTime(end))
        start != null -> context.getString(R.string.helprequest_when_from, day, formatTime(start))
        end != null -> context.getString(R.string.helprequest_when_until, day, formatTime(end))
        else -> day
    }
    val kind = context.getString(
        when (request.kind) {
            HelpKind.Childcare -> R.string.helprequest_kind_childcare
            HelpKind.Ride -> R.string.helprequest_kind_ride
            HelpKind.Other -> R.string.helprequest_kind_other
        },
    )
    return ShareContent(
        title = request.title,
        lines = listOf(context.getString(R.string.share_help_line, kind), whenText),
        text = context.getString(R.string.share_help_text, request.title, lower(whenText)),
        link = shareLink(ShareKind.HelpRequest, request.id),
    )
}

/** Enough of a good news to read on the image and in the message: the rest is in Familia. */
private const val SHARE_EXCERPT_MAX = 160

/**
 * The photo at [path], made small enough for the 1080 px image of a share, or null: the share
 * then goes without it rather than not at all.
 */
suspend fun sharePhoto(storage: PhotoStorage?, path: String): Bitmap? {
    val bytes = storage?.bytes(path) ?: return null
    return withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1080) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Opens the share sheet for [content], or says that the phone has no app to share with. */
fun share(context: Context, content: ShareContent) {
    if (!shareContent(context, content, context.getString(R.string.share_chooser))) {
        Toast.makeText(context, R.string.share_no_app, Toast.LENGTH_LONG).show()
    }
}

/** « Partager » in a top bar or on a row, 56 dp like every target of the app. Stateless. */
@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(56.dp)) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_button))
    }
}
