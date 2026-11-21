package fr.familia.app.feature.announcements

import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.ui.graphics.Color
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Announcement
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.canSendAnnouncement
import fr.familia.app.core.model.leavesAtEight
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.frenchDateTime
import fr.familia.app.core.ui.pickGroups
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    family: Family,
    me: Member,
    candidates: List<Member>,
    title: String,
    onTitle: (String) -> Unit,
    text: String,
    onText: (String) -> Unit,
    urgent: Boolean,
    onUrgent: (Boolean) -> Unit,
    selected: Set<String>,
    onSelect: (Set<String>) -> Unit,
    sending: Boolean,
    /** « Déjà envoyées »; null while it loads. */
    sent: List<Announcement>?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSend: () -> Unit,
    failed: Boolean,
    onFailureShown: () -> Unit,
    now: () -> Instant = Instant::now,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val others = stringResource(R.string.announcements_others)
    val groups = remember(candidates, family, me.householdId, others) { pickGroups(candidates, family, me.householdId, others) }
    val canSend = !sending && canSendAnnouncement(title, text, selected)
    Scaffold(
        modifier = Modifier.themeBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            StickyActionBar(
                pluralStringResource(R.plurals.announcements_send, selected.size, selected.size),
                onClick = { confirming = true },
                enabled = canSend,
                busy = sending,
            )
        },
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.announcements_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "help") {
                Text(
                    stringResource(R.string.announcements_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "message") {
                FormTile(icon = Icons.Outlined.Campaign, title = stringResource(R.string.announcements_tile_message)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitle,
                    label = { Text(stringResource(R.string.announcements_field_title)) },
                    supportingText = { Text(stringResource(R.string.announcements_count, title.length, Announcement.MAX_TITLE)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    label = { Text(stringResource(R.string.announcements_field_text)) },
                    supportingText = { Text(stringResource(R.string.announcements_count, text.length, Announcement.MAX_TEXT)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = urgent, role = Role.Checkbox, onValueChange = onUrgent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = urgent, onCheckedChange = null)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.announcements_urgent), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.announcements_urgent_help),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }
            item(key = "to") {
                FormTile(icon = Icons.Outlined.Groups, title = stringResource(R.string.announcements_to)) {
                    HouseholdMemberPicker(groups = groups, selected = selected, onChange = onSelect, enabled = !sending)
                }
            }
            item(key = "preview") {
                FormTile(icon = Icons.Outlined.NotificationsActive, title = stringResource(R.string.announcements_preview)) {
                    NotificationPreview(title, text)
                }
            }
            item(key = "sent-title") { Heading(R.string.announcements_sent_title) }
            when {
                sent == null -> item(key = "sent-loading") { LoadingState() }
                sent.isEmpty() -> item(key = "sent-empty") {
                    Text(stringResource(R.string.announcements_sent_empty), style = MaterialTheme.typography.bodyMedium)
                }
                else -> items(sent, key = { "sent-${it.id}" }) { a -> SentRow(a, onClick = { onOpen(a.id) }) }
            }
        }
    }
    if (confirming) {
        val atEight = leavesAtEight(urgent, now())
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(pluralStringResource(R.plurals.announcements_confirm_title, selected.size, selected.size)) },
            text = {
                Text(stringResource(if (atEight) R.string.announcements_confirm_morning else R.string.announcements_confirm_now))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onSend()
                    },
                    enabled = canSend,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.announcements_confirm_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            title = { Text(stringResource(R.string.announcements_failed_title)) },
            text = { Text(stringResource(R.string.announcements_failed_body)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun Heading(res: Int) {
    Text(
        stringResource(res),
        style = dayHeaderTextStyle(),
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** What the phones will show: the title, then the start of the message. */
@Composable
private fun NotificationPreview(title: String, text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.announcements_preview_app),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                title.trim().ifEmpty { stringResource(R.string.announcements_preview_no_title) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.trim().replace(Regex("\\s+"), " ").ifEmpty { stringResource(R.string.announcements_preview_no_text) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One message already sent, in its card (refonte, lot 3). */
@Composable
private fun SentRow(a: Announcement, onClick: () -> Unit) {
    ClayCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    a.createdAt?.let { frenchDateTime(it) },
                    pluralStringResource(R.plurals.announcements_sent_to, a.recipientIds.size, a.recipientIds.size),
                    deliveryLabel(a),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun deliveryLabel(a: Announcement): String = stringResource(
    when (a.delivery) {
        Announcement.Delivery.Pending -> R.string.announcements_delivery_pending
        Announcement.Delivery.Sent -> if (a.urgent) R.string.announcements_delivery_urgent else R.string.announcements_delivery_sent
        Announcement.Delivery.Morning -> R.string.announcements_delivery_morning
        Announcement.Delivery.Refused -> R.string.announcements_delivery_refused
    },
)

/** The whole message, as its notification opens it. One way: nothing to answer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(state: AnnouncementState, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.themeBackground(),
        containerColor = Color.Transparent,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.announcement_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                AnnouncementState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                AnnouncementState.Gone -> EmptyState(
                    icon = Icons.Outlined.Campaign,
                    title = stringResource(R.string.announcement_gone_title),
                    body = stringResource(R.string.announcement_gone_body),
                )
                is AnnouncementState.Ready -> {
                    val a = state.announcement
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(a.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(a.text, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.announcement_signature, a.authorName),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        a.createdAt?.let {
                            Text(
                                frenchDateTime(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun AnnouncementsScreenPreview() {
    fun m(id: String, name: String, home: String) =
        Member(id, name, null, null, home, MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)
    val me = m("camille", "Camille", "h1")
    val others = listOf(m("jules", "Jules", "h1"), m("ines", "Inès", "h2"), m("noe", "Noé", "h2"))
    val family = Family(listOf(Household("h1", "Les Martin"), Household("h2", "Les Bernard")), listOf(me) + others)
    FamiliaTheme {
        AnnouncementsScreen(
            family = family, me = me, candidates = others,
            title = "Repas de dimanche", onTitle = {}, text = "On se retrouve à midi chez Inès.", onText = {},
            urgent = false, onUrgent = {}, selected = others.map { it.id }.toSet(), onSelect = {},
            sending = false,
            sent = listOf(
                Announcement("a1", "Nouvelle version", "Pense à la mettre à jour.", false, listOf("jules", "ines"), "camille", "Camille", Instant.now(), Announcement.Delivery.Sent),
            ),
            onBack = {}, onOpen = {}, onSend = {}, failed = false, onFailureShown = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnnouncementScreenPreview() {
    FamiliaTheme {
        AnnouncementScreen(
            state = AnnouncementState.Ready(
                Announcement("a1", "Repas de dimanche", "On se retrouve à midi chez Inès. Apportez un dessert !", false, listOf("ines"), "camille", "Camille", Instant.now(), Announcement.Delivery.Sent),
            ),
            onBack = {},
        )
    }
}
