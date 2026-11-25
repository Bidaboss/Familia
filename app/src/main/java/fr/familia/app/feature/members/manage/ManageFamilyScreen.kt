package fr.familia.app.feature.members.manage

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.MembersUiState
import fr.familia.app.feature.members.previewGroups

/** Who is still to come into the app: an adult or a child with the app, not signed in yet. */
internal fun pendingInvitations(groups: List<HouseholdGroup>): List<Member> =
    groups.flatMap { it.members }.filter { it.profile != MemberProfile.ChildWithoutApp && it.uid == null }

/** What the admin may move or remove: everyone, but the admin principal is never removed. */
internal fun removable(groups: List<HouseholdGroup>, ownerId: String?): List<HouseholdGroup> =
    groups.map { group -> group.copy(members = group.members.filter { it.id != ownerId }) }.filter { it.members.isNotEmpty() }

/** The two choices that start with picking a member. */
internal enum class ManagePick { Move, Remove }

/**
 * Gérer la famille (brief Famille-recettes F5): reserved to the admin principal. The invitations
 * still waiting, with Relancer; then Ajouter un membre, Changer un membre de foyer and Retirer un
 * membre, the last two through the member form, which keeps its checks (parent unique, etc.).
 */
@Composable
fun ManageFamilyScreen(
    state: MembersUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (memberId: String) -> Unit,
    onRemind: (Member) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf<ManagePick?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = { FamiliaTopBar(title = stringResource(R.string.manage_title), onBack = onBack) },
    ) { padding ->
        when (state) {
            MembersUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            MembersUiState.Error -> EmptyState(
                icon = Icons.Outlined.Groups,
                title = stringResource(R.string.members_error_title),
                body = stringResource(R.string.members_error_body),
                modifier = Modifier.padding(padding),
            )
            is MembersUiState.Success -> if (!state.isOwner) {
                // Reached by a link while not (or no longer) the admin principal: nothing to do here.
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = stringResource(R.string.manage_owner_only),
                    body = stringResource(R.string.manage_owner_only_body),
                    modifier = Modifier.padding(padding),
                )
            } else {
                ManageContent(state, padding, onAdd, onEdit, onRemind, onPick = { picking = it })
            }
        }
    }
    val success = state as? MembersUiState.Success
    val pick = picking
    if (success != null && success.isOwner && pick != null) {
        PickMemberDialog(
            title = stringResource(if (pick == ManagePick.Move) R.string.manage_move_pick else R.string.manage_remove_pick),
            groups = if (pick == ManagePick.Remove) removable(success.groups, success.ownerId) else success.groups,
            onPick = { id ->
                picking = null
                onEdit(id)
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ManageContent(
    state: MembersUiState.Success,
    padding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onRemind: (Member) -> Unit,
    onPick: (ManagePick) -> Unit,
) {
    val invitations = pendingInvitations(state.groups)
    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "owner-only") {
            Text(
                stringResource(R.string.manage_owner_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (invitations.isNotEmpty()) {
            item(key = "invitations-title") { SectionTitle(stringResource(R.string.manage_invitations)) }
            item(key = "invitations") {
                ClayCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    invitations.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalContentColor.current.copy(alpha = 0.12f))
                        InvitationRow(member, onRemind = { onRemind(member) }, onAddEmail = { onEdit(member.id) })
                    }
                }
            }
        }
        item(key = "actions-title") { SectionTitle(stringResource(R.string.manage_actions)) }
        item(key = "actions") {
            ClayCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                ActionRow(Icons.Outlined.PersonAdd, stringResource(R.string.manage_add), stringResource(R.string.manage_add_body), onClick = onAdd)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalContentColor.current.copy(alpha = 0.12f))
                ActionRow(Icons.Outlined.Home, stringResource(R.string.manage_move), stringResource(R.string.manage_move_body), onClick = { onPick(ManagePick.Move) })
            }
        }
        item(key = "remove") {
            ClayCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                ActionRow(
                    Icons.Outlined.PersonOff,
                    stringResource(R.string.manage_remove),
                    stringResource(R.string.manage_remove_body),
                    danger = true,
                    onClick = { onPick(ManagePick.Remove) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun InvitationRow(member: Member, onRemind: () -> Unit, onAddEmail: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.firstName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (member.email == null) R.string.manage_invitation_no_email else R.string.manage_invitation_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f),
            )
        }
        if (member.email == null) {
            OutlinedButton(onClick = onAddEmail, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.manage_invitation_add_email))
            }
        } else {
            val label = stringResource(R.string.manage_invitation_remind_name, member.firstName)
            Button(onClick = onRemind, modifier = Modifier.heightIn(min = 56.dp).semantics { this.contentDescription = label }) {
                Text(stringResource(R.string.manage_invitation_remind))
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, body: String, danger: Boolean = false, onClick: () -> Unit) {
    val ink = if (danger) MaterialTheme.colorScheme.error else LocalContentColor.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = ink) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

/** One member to pick, ranked by household (règle des listes de membres, 17/11). */
@Composable
private fun PickMemberDialog(title: String, groups: List<HouseholdGroup>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                groups.forEach { group ->
                    item(key = "h-${group.household?.id ?: "other"}") {
                        Text(
                            group.household?.name ?: stringResource(R.string.members_other_household),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    group.members.forEach { member ->
                        item(key = "m-${member.id}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClick = { onPick(member.id) })
                                    .heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MemberAvatar(member, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(member.firstName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@FamiliaPreviews
@Composable
private fun ManageFamilyPreview() {
    FamiliaTheme {
        Surface {
            ManageFamilyScreen(
                MembersUiState.Success(previewGroups(), "a", ownerId = "a", isOwner = true),
                onBack = {},
                onAdd = {},
                onEdit = {},
                onRemind = {},
            )
        }
    }
}
