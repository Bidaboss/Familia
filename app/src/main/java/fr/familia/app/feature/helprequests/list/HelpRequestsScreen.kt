package fr.familia.app.feature.helprequests.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.feature.helprequests.detail.HelpActionError
import fr.familia.app.feature.helprequests.todo.HelpRequestTodo
import fr.familia.app.feature.helprequests.todo.HelpRequestTodoCard
import fr.familia.app.feature.helprequests.todo.helpActionErrorText
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import java.time.LocalDate
import java.time.LocalTime

/**
 * The « Coups de main » tab (PRD, récit 15). Stateless. With [r1bis] (présentation R1 bis, §4),
 * the requests that wait for me come first, in big, with their buttons on the card.
 */
@Composable
fun HelpRequestsScreen(
    state: HelpRequestsUiState,
    onAsk: () -> Unit,
    onOpenRequest: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    r1bis: Boolean = false,
    error: HelpActionError? = null,
    onTake: (HelpRequest) -> Unit = {},
    onDecline: (HelpRequest) -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            HelpRequestsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            HelpRequestsUiState.Error -> EmptyState(
                icon = Icons.Outlined.VolunteerActivism,
                title = stringResource(R.string.helprequest_error_title),
                body = stringResource(R.string.helprequest_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is HelpRequestsUiState.Success ->
                if (state.isEmpty) {
                    EmptyState(
                        icon = Icons.Outlined.VolunteerActivism,
                        title = stringResource(R.string.helprequest_empty_title),
                        body = stringResource(R.string.helprequest_empty_body),
                    )
                } else if (r1bis) {
                    R1bisRequestList(state, onOpenRequest, onTake, onDecline)
                } else {
                    RequestList(state, onOpenRequest)
                }
        }
        if (state !is HelpRequestsUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = onAsk,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.helprequest_ask)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    helpActionErrorText(error)?.let { text ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.helprequest_error_dialog_title)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/**
 * R1 bis (maquette R4Entraide): « Coups de main », then « On a besoin de toi » with « Je m'en
 * occupe » / « Pas dispo », my own requests, « Déjà pris en charge », and last the ones I said no to.
 */
@Composable
private fun R1bisRequestList(
    state: HelpRequestsUiState.Success,
    onOpenRequest: (String) -> Unit,
    onTake: (HelpRequest) -> Unit,
    onDecline: (HelpRequest) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = ClaySpacing.screen, top = 16.dp, end = ClaySpacing.screen, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "title") {
            Text(
                stringResource(R.string.helprequest_r1bis_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (state.forMe.isNotEmpty()) {
            item(key = "needsYou") { SectionLabel(stringResource(R.string.helprequest_r1bis_needs_you)) }
            items(state.forMe, key = { "forMe-" + it.request.id }) { todo: HelpRequestTodo ->
                HelpRequestTodoCard(
                    todo = todo,
                    onTake = { onTake(todo.request) },
                    onDecline = { onDecline(todo.request) },
                    onOpen = { onOpenRequest(todo.request.id) },
                    large = true,
                )
            }
        }
        if (state.mine.isNotEmpty()) {
            item(key = "mine") { SectionLabel(stringResource(R.string.helprequest_r1bis_mine)) }
            items(state.mine, key = { "mine-" + it.request.id }) { row -> RequestCard(row, state.today, onOpenRequest) }
        }
        if (state.taken.isNotEmpty()) {
            item(key = "handled") { SectionLabel(stringResource(R.string.helprequest_r1bis_handled)) }
            items(state.taken, key = { "taken-" + it.request.id }) { row -> HandledRow(row, state.today, onOpenRequest) }
        }
        if (state.saidNo.isNotEmpty()) {
            item(key = "saidNo") { SectionLabel(stringResource(R.string.helprequest_r1bis_said_no)) }
            items(state.saidNo, key = { "no-" + it.request.id }) { row -> RequestCard(row, state.today, onOpenRequest) }
        }
    }
}

/** « Déjà pris en charge »: the volunteer's avatar, the title and « Camille s'en occupe, demain ». */
@Composable
private fun HandledRow(row: HelpRequestRow, today: LocalDate, onOpenRequest: (String) -> Unit) {
    val request = row.request
    val tint = ClayTint.Plain
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        tint = tint,
        onClick = { onOpenRequest(request.id) },
        onClickLabel = stringResource(R.string.helprequest_open),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            row.volunteer?.let { MemberAvatar(it, size = 40.dp) }
            Column {
                ClayCardTitle(request.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (row.iAmVolunteer) {
                        stringResource(R.string.helprequest_r1bis_handled_by_me, whenLabel(request, today))
                    } else {
                        stringResource(R.string.helprequest_r1bis_handled_by, request.volunteerName.orEmpty(), whenLabel(request, today))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
            }
        }
    }
}

@Composable
private fun RequestList(state: HelpRequestsUiState.Success, onOpenRequest: (String) -> Unit) {
    // The bottom padding keeps the last card above the « Demander » button.
    LazyColumn(
        contentPadding = PaddingValues(start = ClaySpacing.screen, top = 16.dp, end = ClaySpacing.screen, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        // One card in full relief (DESIGN-11): the first request someone else asks, the one to answer.
        val first = state.open.firstOrNull { !it.isMine }?.request?.id
        if (state.open.isNotEmpty()) {
            item(key = "open") { SectionTitle(stringResource(R.string.helprequest_section_open)) }
            items(state.open, key = { it.request.id }) { row -> RequestCard(row, state.today, onOpenRequest, emphasis = row.request.id == first) }
        }
        if (state.taken.isNotEmpty()) {
            item(key = "taken") { SectionTitle(stringResource(R.string.helprequest_section_taken)) }
            items(state.taken, key = { it.request.id }) { row -> RequestCard(row, state.today, onOpenRequest) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun RequestCard(row: HelpRequestRow, today: LocalDate, onOpenRequest: (String) -> Unit, emphasis: Boolean = false) {
    val request = row.request
    // Tints say who an event is for (charte, « Style Argile »): a request keeps the plain card.
    val tint = ClayTint.Plain
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        tint = tint,
        onClick = { onOpenRequest(request.id) },
        onClickLabel = stringResource(R.string.helprequest_open),
        emphasis = emphasis,
    ) {
        Text(
            stringResource(R.string.helprequest_card_kind, kindLabel(request.kind), whenLabel(request, today)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        ClayCardTitle(request.title, style = MaterialTheme.typography.titleMedium)
        Text(
            whoLabel(row),
            style = MaterialTheme.typography.bodyMedium,
            color = FamiliaClay.colors.onTintVariant(tint),
        )
        Text(
            statusLabel(row),
            style = MaterialTheme.typography.bodyMedium,
            color = if (request.status == HelpStatus.Open && !row.isMine) MaterialTheme.colorScheme.primary else FamiliaClay.colors.onTintVariant(tint),
        )
    }
}

/** "Demandé par moi", "Demandé par Camille", "Pour Noé, demandé par Camille". */
@Composable
private fun whoLabel(row: HelpRequestRow): String {
    val author = if (row.isMine) stringResource(R.string.helprequest_by_me) else row.request.authorName
    return row.helpedName?.let { stringResource(R.string.helprequest_for_by, it, author) }
        ?: stringResource(R.string.helprequest_by, author)
}

@Composable
private fun statusLabel(row: HelpRequestRow): String {
    val request = row.request
    return when {
        row.iAmVolunteer -> stringResource(R.string.helprequest_status_mine_taken)
        request.status == HelpStatus.Taken ->
            stringResource(R.string.helprequest_status_taken_by, request.volunteerName.orEmpty())
        row.iDeclined -> stringResource(R.string.helprequest_status_i_declined)
        row.isMine && request.declinedBy != null -> stringResource(R.string.helprequest_status_declined)
        row.isMine -> stringResource(R.string.helprequest_status_waiting)
        else -> stringResource(R.string.helprequest_status_open)
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewHelpRequests(): HelpRequestsUiState.Success {
    val today = LocalDate.of(2026, 11, 20)
    fun request(id: String, kind: HelpKind, title: String, date: LocalDate, author: String, status: HelpStatus = HelpStatus.Open, volunteer: String? = null) =
        HelpRequest(
            id, kind, title, date, LocalTime.of(17, 0), LocalTime.of(19, 0), null, null,
            authorId = author.lowercase(), authorName = author, forMemberId = author.lowercase(),
            notifyIds = listOf("me", "b"), status = status, volunteerId = volunteer?.lowercase(), volunteerName = volunteer,
        )
    return HelpRequestsUiState.Success(
        open = listOf(
            HelpRequestRow(request("r1", HelpKind.Childcare, "Garder Noé mercredi après-midi", today.plusDays(1), "Camille"), "Noé", isMine = false, iAmVolunteer = false, iDeclined = false),
            HelpRequestRow(request("r2", HelpKind.Ride, "M'emmener à la gare", today.plusDays(3), "Moi"), null, isMine = true, iAmVolunteer = false, iDeclined = false),
        ),
        taken = listOf(
            HelpRequestRow(request("r3", HelpKind.Other, "Arroser les plantes", today.plusDays(6), "Jules", HelpStatus.Taken, "Inès"), null, isMine = false, iAmVolunteer = false, iDeclined = false),
        ),
        today = today,
    )
}

@FamiliaPreviews
@Composable
private fun HelpRequestsScreenPreview() {
    FamiliaTheme { Surface { HelpRequestsScreen(previewHelpRequests(), {}, {}, {}) } }
}

@FamiliaPreviews
@Composable
private fun HelpRequestsScreenR1bisPreview() {
    val state = previewHelpRequests()
    val forMe = state.open.filter { !it.isMine }.map { HelpRequestTodo(it.request, null, it.helpedName, state.today) }
    FamiliaTheme { Surface { HelpRequestsScreen(state.copy(forMe = forMe), {}, {}, {}, r1bis = true) } }
}
