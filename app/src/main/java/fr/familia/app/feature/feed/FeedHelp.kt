package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SideBySide
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.helprequests.declineHelp
import fr.familia.app.feature.helprequests.detail.HelpActionError
import fr.familia.app.feature.helprequests.needsMe
import fr.familia.app.feature.helprequests.takeHelp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * « Coup de main demandé ou pris » (récit 67): the requests of the window that [meId] may already
 * read in Entraide (those I asked for or was warned about), never a cancelled one. Open, it asks
 * with « Je m'en occupe » and « Pas dispo » when it waits for me; taken, it says who helps.
 */
class HelpFeedSource(private val requests: HelpRequestRepository, private val meId: String) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        requests.observeMine(meId).map { list -> helpForFeed(list, window.since, window.limit).map { HelpFeedCard(it, requests) } }
}

/** The requests of the fil: created since [since], not cancelled, the newest [limit]. */
fun helpForFeed(list: List<HelpRequest>, since: Instant, limit: Int): List<HelpRequest> = list
    .filter { r -> r.status != HelpStatus.Cancelled && r.createdAt?.isBefore(since) == false }
    .sortedByDescending { it.createdAt }
    .take(limit)

class HelpFeedCard(val request: HelpRequest, private val requests: HelpRequestRepository?) : FeedCard {
    override val key: String = "help:${request.id}"
    override val at: Instant = request.createdAt ?: Instant.EPOCH
    override val authorId: String = request.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Lilac
        val open = { navigation.onOpenHelpRequest(request.id) }
        val day = dayShortLabel(request.date, context.today)
        val volunteer = request.volunteerName
        if (request.status == HelpStatus.Taken && volunteer != null) {
            // Taken: one line, « Lucas s'en occupe : monter l'armoire · samedi ✓ ».
            FeedCardShell(tone = tone, onOpen = open) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    context.member(request.volunteerId)?.let { MemberAvatar(it, size = 30.dp) }
                    Text(
                        stringResource(R.string.feed_help_taken, volunteer, request.title, day),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.Check, contentDescription = null, tint = tone.label)
                }
            }
            return
        }
        val scope = rememberCoroutineScope()
        var busy by remember(request.id) { mutableStateOf(false) }
        var error by remember(request.id) { mutableStateOf<HelpActionError?>(null) }
        FeedCardShell(
            tone = tone,
            onOpen = open,
            header = {
                FeedHeader(
                    member = context.member(request.authorId),
                    name = request.authorName,
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                    label = stringResource(R.string.feed_label_help),
                    tone = tone,
                )
            },
        ) {
            Column {
                Text(request.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    request.startTime?.let { "$day · ${formatTime(it)}" } ?: day,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val repo = requests
            if (repo != null && !context.readOnly && needsMe(request, context.me, context.today)) {
                SideBySide { item ->
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                error = takeHelp(repo, request, context.me)
                                busy = false
                            }
                        },
                        modifier = item.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.feed_help_take)) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                error = declineHelp(repo, request, context.me)
                                busy = false
                            }
                        },
                        modifier = item.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.feed_help_no)) }
                }
            }
            error?.let {
                Text(
                    when (it) {
                        is HelpActionError.AlreadyTaken -> stringResource(R.string.feed_help_already_taken)
                        else -> stringResource(R.string.feed_help_failed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
