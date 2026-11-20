package fr.familia.app.feature.events.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.place.DirectionsButton
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayTitle
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.FormTileShape
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import fr.familia.app.feature.events.eventPlaceView
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * « Comptes de l'événement » (PRODUIT-57) opens for an adult who answered « Je viens », or for the
 * author of the event. « Peut-être » does not count, and a child never sees it.
 */
internal fun canOpenExpenses(state: EventDetailUiState.Success, me: Member): Boolean =
    me.profile == MemberProfile.Adult &&
        (state.event.authorId == me.id || state.answerers.any { it.isMe && it.status == RsvpStatus.Yes })

/** An event, read only, and "qui vient" for the member and their children. Stateless. */
@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onAnswer: (memberId: String, status: RsvpStatus) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** « Ajouter à mon agenda » (PRD, récit 30); hidden when null. */
    onAddToCalendar: (() -> Unit)? = null,
    /** « Partager » vers une messagerie (PRD, récit 31); hidden when null. */
    onShare: (() -> Unit)? = null,
    /** « Comptes de l'événement » (PRODUIT-57), set only for whom [canOpenExpenses] lets in; hidden when null. */
    onOpenExpenses: (() -> Unit)? = null,
    /** Above the title: the cover of the event (récit 44), when its module is on. */
    header: @Composable () -> Unit = {},
    // Last, so a trailing lambda stays the album.
    album: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // The common bar (récit 85): « Modifier » and « Partager » in its round, no more link at the bottom.
            FamiliaTopBar(
                title = stringResource(R.string.event_detail_title),
                onBack = onBack,
                actions = {
                    if (state is EventDetailUiState.Success && state.canManage) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.event_detail_edit))
                        }
                    }
                    if (onShare != null && state is EventDetailUiState.Success) ShareButton(onClick = onShare)
                    HelpButton(ScreenHelp.EventDetail)
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EventDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                EventDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.event_detail_missing_title),
                    body = stringResource(R.string.event_detail_missing_body),
                    action = {
                        Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_back_to_planning))
                        }
                    },
                )
                EventDetailUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.event_detail_error_title),
                    body = stringResource(R.string.planning_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is EventDetailUiState.Success -> Content(state, onAnswer, onAddToCalendar, onOpenExpenses, album, header)
            }
        }
    }
}

/**
 * A tile of the page of an event (récit 85), drawn like a [fr.familia.app.core.ui.form.FormTile]
 * but without its title: the sections (« On y va ensemble », « Qui apporte quoi »…) keep their own
 * for now, so each one only moves into a tile instead of sitting between two lines.
 */
@Composable
fun EventSectionTile(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = FormTileShape,
        color = LocalClayColors.current.card,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clip(FormTileShape)
                .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 21.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Content(
    state: EventDetailUiState.Success,
    onAnswer: (String, RsvpStatus) -> Unit,
    onAddToCalendar: (() -> Unit)?,
    onOpenExpenses: (() -> Unit)?,
    album: @Composable () -> Unit,
    header: @Composable () -> Unit,
) {
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()
        HeaderTile(state, onAddToCalendar, onOpenExpenses)
        FormTile(Icons.Outlined.Groups, stringResource(R.string.rsvp_title)) {
            state.answerers.forEach { AnswerRow(it, onAnswer) }
            if (state.sendFailed) {
                Text(
                    stringResource(R.string.rsvp_send_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // « N viennent » with their avatars; a tap shows every answer (DESIGN-5).
            ComingLine(state, onClick = { showAnswers = true })
        }
        // Each section in its tile, in the same order (récit 85).
        album()
    }
    if (showAnswers) {
        AlertDialog(
            onDismissRequest = { showAnswers = false },
            title = { Text(stringResource(R.string.rsvp_answers_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Summary(R.string.rsvp_summary_coming, state.coming)
                    Summary(R.string.rsvp_summary_maybe, state.maybe)
                    Summary(R.string.rsvp_summary_not_coming, state.notComing)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnswers = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

/**
 * The head of the page (récit 85): the box of the day and the title, the day and the hour, the
 * place in a band with « Itinéraire » (récit 74), who it is for, the note with its @, then
 * « Ajouter à mon agenda » and « Les comptes » when they are offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderTile(
    state: EventDetailUiState.Success,
    onAddToCalendar: (() -> Unit)?,
    onOpenExpenses: (() -> Unit)?,
) {
    val event = state.event
    val colors = MaterialTheme.colorScheme
    EventSectionTile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayBox(event.date)
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                val day = dayTitle(event.date, state.today)
                Text(
                    event.time?.let { stringResource(R.string.event_detail_when, day, formatTime(it)) } ?: day,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
        }
        // Under the time (récit 74), with the route in Google Maps or Waze as soon as there is an address.
        eventPlaceView(event, state.households, LocalFeatureFlags.current.isOn(Feature.Places))?.let { place ->
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Home, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text(place.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
                place.directions?.let { DirectionsButton(it) }
            }
        }
        Text(
            if (event.target is EventTarget.ForFamily) {
                stringResource(R.string.planning_by, state.authorName)
            } else {
                stringResource(R.string.event_detail_for_by, targetLabel(state.target), state.authorName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        event.note?.let { MentionText(it, event.mentionIds, style = MaterialTheme.typography.bodyLarge) }
        if (onAddToCalendar != null || onOpenExpenses != null) {
            // Secondary actions stay outlined (DESIGN-11): the one dark choice of the page is the answer.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onAddToCalendar?.let { PillButton(Icons.Outlined.CalendarMonth, stringResource(R.string.calendar_add), it) }
                onOpenExpenses?.let { PillButton(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.event_detail_expenses), it) }
            }
        }
    }
}

/** « SAM » over « 28 », in a pale square: the date is said by the line next to it. */
@Composable
private fun DayBox(date: LocalDate) {
    // The container pair of the theme, so the small « JEU » keeps 4.5:1 in every theme.
    val accent = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .widthIn(min = 60.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.FRANCE).trimEnd('.').uppercase(Locale.FRANCE),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Up to five avatars of those who come, then « 3 viennent · 1 peut-être » and « Voir ». */
@Composable
private fun ComingLine(state: EventDetailUiState.Success, onClick: () -> Unit) {
    val coming = state.coming.size
    val maybe = state.maybe.size
    val label = when {
        coming == 0 && maybe == 0 -> stringResource(R.string.rsvp_nobody_yet)
        else -> listOfNotNull(
            pluralStringResource(R.plurals.planning_coming, coming, coming).takeIf { coming > 0 },
            pluralStringResource(R.plurals.planning_maybe, maybe, maybe).takeIf { maybe > 0 },
        ).joinToString(" · ")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.rsvp_answers_open), onClick = onClick)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown = state.comingMembers.take(5)
        if (shown.isNotEmpty()) {
            Box(Modifier.padding(end = 12.dp)) {
                shown.forEachIndexed { index, member ->
                    MemberAvatar(member, size = 32.dp, modifier = Modifier.padding(start = (index * 22).dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.rsvp_answers_see),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * One person I answer for (me, then my children): the avatar, the name, then Oui, Peut-être, Non
 * as one radio group, the chosen one dark. The buttons go under the name when the text is large.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerRow(answerer: Answerer, onAnswer: (String, RsvpStatus) -> Unit) {
    val name = if (answerer.isMe) stringResource(R.string.rsvp_me) else answerer.member.firstName
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(answerer.member, size = 40.dp)
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp, end = 4.dp))
            }
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RsvpStatus.entries.forEach { status ->
                    FormChip(
                        label = stringResource(status.shortLabel()),
                        selected = answerer.status == status,
                        onClick = { onAnswer(answerer.member.id, status) },
                        description = stringResource(R.string.rsvp_chip_description, name, stringResource(status.label())),
                    )
                }
            }
        }
        if (answerer.pending) {
            Text(
                stringResource(R.string.rsvp_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun Summary(titleRes: Int, names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(titleRes, names.size), style = MaterialTheme.typography.titleMedium)
        Text(
            if (names.isEmpty()) stringResource(R.string.rsvp_summary_nobody) else names.joinToString(", "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun RsvpStatus.label(): Int = when (this) {
    RsvpStatus.Yes -> R.string.rsvp_yes
    RsvpStatus.Maybe -> R.string.rsvp_maybe
    RsvpStatus.No -> R.string.rsvp_no
}

/** The word on the button; [label] is what TalkBack reads. */
private fun RsvpStatus.shortLabel(): Int = when (this) {
    RsvpStatus.Yes -> R.string.rsvp_short_yes
    RsvpStatus.Maybe -> R.string.rsvp_maybe
    RsvpStatus.No -> R.string.rsvp_short_no
}

/** Fictional event for previews and screenshots. */
internal fun previewEventDetail(canManage: Boolean = true): EventDetailUiState.Success {
    fun member(id: String, name: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, name, null, null, "h1", profile, false, emptyList(), null)
    val today = LocalDate.of(2026, 12, 23)
    return EventDetailUiState.Success(
        event = Event(
            "e1", "Repas de Noël", LocalDate.of(2026, 12, 24), LocalTime.of(19, 30), "Chez les parents",
            "Chacun apporte un dessert.", EventTarget.ForFamily, "a", "Camille",
        ),
        today = today,
        target = TargetChoice(EventTarget.ForFamily, null),
        authorName = "Camille",
        canManage = canManage,
        answerers = listOf(
            Answerer(member("a", "Camille"), RsvpStatus.Yes, isMe = true),
            Answerer(member("c", "Noé", MemberProfile.ChildWithoutApp), null, isMe = false),
        ),
        coming = listOf("Camille", "Inès"),
        maybe = listOf("Jules"),
        notComing = emptyList(),
    )
}

@FamiliaPreviews
@Composable
private fun EventDetailScreenPreview() {
    FamiliaTheme { Surface { EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}) } }
}
