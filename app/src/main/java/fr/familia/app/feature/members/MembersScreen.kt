package fr.familia.app.feature.members

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar

/** Famille tab: members grouped by household, then the installed version. Stateless. */
@Composable
fun MembersScreen(
    state: MembersUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null for members who are not admin: rows are then read-only and there is no add button. */
    onEditMember: ((memberId: String?) -> Unit)? = null,
    versionName: String = BuildConfig.VERSION_NAME,
    onOpenNotifications: (() -> Unit)? = null,
    /** Null for members who are not admin. */
    onOpenExport: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
) {
    when (state) {
        MembersUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        MembersUiState.Error -> EmptyState(
            icon = Icons.Outlined.Groups,
            title = stringResource(R.string.members_error_title),
            body = stringResource(R.string.members_error_body),
            modifier = modifier,
            action = {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            },
        )
        is MembersUiState.Success -> Box(modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room at the bottom so the add button never hides the last row.
                contentPadding = PaddingValues(top = 8.dp, bottom = if (onEditMember != null) 96.dp else 8.dp),
            ) {
                if (state.groups.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Groups,
                            title = stringResource(R.string.members_empty_title),
                            body = stringResource(R.string.members_empty_body),
                        )
                    }
                }
                state.groups.forEach { group ->
                    item(key = "household-${group.household?.id ?: "other"}") {
                        HouseholdHeader(group.household?.name ?: stringResource(R.string.members_other_household))
                    }
                    items(group.members, key = { "member-${it.id}" }) { member ->
                        MemberRow(
                            member = member,
                            isMe = member.id == state.currentMemberId,
                            onClick = onEditMember?.let { edit -> { edit(member.id) } },
                        )
                    }
                }
                if (onOpenNotifications != null) {
                    item(key = "notifications") {
                        Column {
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                            SettingsRow(Icons.Outlined.Notifications, R.string.notifications_title, onOpenNotifications)
                        }
                    }
                }
                if (onOpenExport != null) {
                    item(key = "export") { SettingsRow(Icons.Outlined.SaveAlt, R.string.export_title, onOpenExport) }
                }
                if (onOpenUpdates != null) {
                    item(key = "updates") { SettingsRow(Icons.Outlined.SystemUpdate, R.string.update_check_title, onOpenUpdates) }
                }
                if (onOpenHelp != null) {
                    item(key = "help") { SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, R.string.help_title, onOpenHelp) }
                }
                item(key = "version") {
                    Text(
                        text = stringResource(R.string.app_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            if (onEditMember != null) {
                ExtendedFloatingActionButton(
                    onClick = { onEditMember(null) },
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.members_add)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

/** A settings entry below the members (design: settings live in the Famille tab). */
@Composable
private fun SettingsRow(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HouseholdHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun MemberRow(member: Member, isMe: Boolean, onClick: (() -> Unit)?) {
    val editLabel = stringResource(R.string.members_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = editLabel, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isMe) stringResource(R.string.members_me, member.firstName) else member.firstName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = profileLabel(member),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun profileLabel(member: Member): String {
    val profile = stringResource(
        when (member.profile) {
            MemberProfile.Adult -> R.string.profile_adult
            MemberProfile.ChildWithApp -> R.string.profile_child_with_app
            MemberProfile.ChildWithoutApp -> R.string.profile_child_without_app
        },
    )
    return if (member.isAdmin) stringResource(R.string.profile_with_admin, profile) else profile
}

/** Fictional family used by previews and screenshots (never real names). */
internal fun previewGroups(): List<HouseholdGroup> {
    fun m(id: String, name: String, profile: MemberProfile = MemberProfile.Adult, admin: Boolean = false) =
        Member(id, name, null, null, "h1", profile, admin, emptyList(), null)
    return listOf(
        HouseholdGroup(
            Household("h1", "Foyer Martin"),
            listOf(m("a", "Camille", admin = true), m("b", "Sacha"), m("c", "Noé", MemberProfile.ChildWithoutApp)),
        ),
        HouseholdGroup(Household("h2", "Foyer Bernard"), listOf(m("d", "Inès"), m("e", "Lou", MemberProfile.ChildWithApp))),
        HouseholdGroup(null, listOf(m("f", "Jules"))),
    )
}

@FamiliaPreviews
@Composable
private fun MembersScreenPreview() {
    FamiliaTheme {
        Surface {
            MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0", onEditMember = {})
        }
    }
}

@FamiliaPreviews
@Composable
private fun MembersScreenErrorPreview() {
    FamiliaTheme { Surface { MembersScreen(MembersUiState.Error, onRetry = {}, versionName = "0.1.0") } }
}
