package fr.familia.app.core.share

import android.content.Context
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
