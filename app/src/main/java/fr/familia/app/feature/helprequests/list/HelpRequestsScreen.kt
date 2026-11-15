package fr.familia.app.feature.helprequests.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import java.time.LocalDate
import java.time.LocalTime

/** The « Coups de main » tab (PRD, récit 15). Stateless. */
@Composable
fun HelpRequestsScreen(
    state: HelpRequestsUiState,
    onAsk: () -> Unit,
    onOpenRequest: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
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
}

@Composable
private fun RequestList(state: HelpRequestsUiState.Success, onOpenRequest: (String) -> Unit) {
    // The bottom padding keeps the last card above the « Demander » button.
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.open.isNotEmpty()) {
            item(key = "open") { SectionTitle(stringResource(R.string.helprequest_section_open)) }
            items(state.open, key = { it.request.id }) { row -> RequestCard(row, state.today, onOpenRequest) }
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
private fun RequestCard(row: HelpRequestRow, today: LocalDate, onOpenRequest: (String) -> Unit) {
    val request = row.request
    // Plain for what concerns me as a helper, the household tint for my own requests.
    val tint = if (row.isMine) ClayTint.Household else ClayTint.Plain
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        tint = tint,
        onClick = { onOpenRequest(request.id) },
        onClickLabel = stringResource(R.string.helprequest_open),
    ) {
        Text(
            stringResource(R.string.helprequest_card_kind, kindLabel(request.kind), whenLabel(request, today)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(request.title, style = MaterialTheme.typography.titleMedium)
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
