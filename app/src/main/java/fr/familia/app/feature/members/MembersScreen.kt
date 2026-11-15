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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.photo.MemberPhotoEditor

/** Famille tab: members grouped by household, then the installed version. Stateless. */
@Composable
fun MembersScreen(
    state: MembersUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the form; each row and the add button follow the rights in [MembersUiState.Success]. */
    onEditMember: ((memberId: String?) -> Unit)? = null,
    versionName: String = BuildConfig.VERSION_NAME,
    onOpenNotifications: (() -> Unit)? = null,
    /** Shown to the admin principal only (PRD, récit 1 ter). */
    onOpenAdministration: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    /** The pencil shows on the households of [MembersUiState.Success.renamableHouseholdIds]. */
    onRenameHousehold: ((Household, String) -> Unit)? = null,
    renameFailed: Boolean = false,
    onRenameFailureShown: () -> Unit = {},
    /** The avatar of [MembersUiState.Success.photoEditableMemberIds] opens the photo choice (PRD, récit 3 bis). */
    onSavePhoto: ((memberId: String, jpeg: ByteArray) -> Unit)? = null,
    onRemovePhoto: (memberId: String) -> Unit = {},
    photoFailed: Boolean = false,
    onPhotoFailureShown: () -> Unit = {},
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var photoOf by rememberSaveable { mutableStateOf<String?>(null) }
    val photos = LocalMemberPhotos.current
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
            val canAdd = onEditMember != null && state.canAddMembers
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room at the bottom so the add button never hides the last row.
                contentPadding = PaddingValues(top = 8.dp, bottom = if (canAdd) 96.dp else 8.dp),
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
                        val household = group.household
                        HouseholdHeader(
                            name = household?.name ?: stringResource(R.string.members_other_household),
                            onRename = if (household != null && onRenameHousehold != null &&
                                household.id in state.renamableHouseholdIds
                            ) {
                                { renaming = household.id }
                            } else {
                                null
                            },
                        )
                    }
                    items(group.members, key = { "member-${it.id}" }) { member ->
                        MemberRow(
                            member = member,
                            isMe = member.id == state.currentMemberId,
                            isOwner = member.id == state.ownerId,
                            onClick = onEditMember?.takeIf { member.id in state.editableMemberIds }?.let { edit -> { edit(member.id) } },
                            hasPhoto = member.id in photos,
                            onPhotoClick = if (onSavePhoto != null && member.id in state.photoEditableMemberIds) {
                                { photoOf = member.id }
                            } else {
                                null
                            },
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
                if (onOpenAdministration != null && state.isOwner) {
                    item(key = "administration") {
                        SettingsRow(Icons.Outlined.AdminPanelSettings, R.string.administration_title, onOpenAdministration)
                    }
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
            if (canAdd && onEditMember != null) {
                ExtendedFloatingActionButton(
                    onClick = { onEditMember(null) },
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.members_add)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
    val renamed = (state as? MembersUiState.Success)?.groups?.firstNotNullOfOrNull { group ->
        group.household?.takeIf { it.id == renaming }
    }
    if (renamed != null && onRenameHousehold != null) {
        RenameHouseholdDialog(
            household = renamed,
            onConfirm = { name -> onRenameHousehold(renamed, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    val photoMember = (state as? MembersUiState.Success)?.groups?.firstNotNullOfOrNull { group ->
        group.members.firstOrNull { it.id == photoOf }
    }
    if (photoMember != null && onSavePhoto != null) {
        MemberPhotoEditor(
            member = photoMember,
            hasPhoto = photoMember.id in photos,
            onSave = { jpeg -> onSavePhoto(photoMember.id, jpeg) },
            onRemove = { onRemovePhoto(photoMember.id) },
            onDismiss = { photoOf = null },
        )
    }
    if (photoFailed) {
        AlertDialog(
            onDismissRequest = onPhotoFailureShown,
            title = { Text(stringResource(R.string.photo_failed_title)) },
            text = { Text(stringResource(R.string.photo_failed_body)) },
            confirmButton = {
                TextButton(onClick = onPhotoFailureShown, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
    if (renameFailed) {
        AlertDialog(
            onDismissRequest = onRenameFailureShown,
            title = { Text(stringResource(R.string.household_rename_failed_title)) },
            text = { Text(stringResource(R.string.household_rename_failed_body)) },
            confirmButton = {
                TextButton(onClick = onRenameFailureShown, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
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
private fun HouseholdHeader(name: String, onRename: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        if (onRename != null) {
            IconButton(onClick = onRename, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.household_rename_action, name))
            }
        }
    }
}

/** Asks for the new name of [household]; "Enregistrer" stays off while the name is empty or unchanged. */
@Composable
private fun RenameHouseholdDialog(household: Household, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(household.id) { mutableStateOf(household.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.household_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MembersViewModel.MAX_HOUSEHOLD_NAME) },
                label = { Text(stringResource(R.string.member_household_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && name.trim() != household.name,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.member_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun MemberRow(
    member: Member,
    isMe: Boolean,
    isOwner: Boolean,
    onClick: (() -> Unit)?,
    hasPhoto: Boolean = false,
    onPhotoClick: (() -> Unit)? = null,
) {
    val editLabel = stringResource(R.string.members_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = editLabel, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPhotoClick != null) {
            PhotoButton(member, hasPhoto, onPhotoClick)
            Spacer(Modifier.width(8.dp))
        } else {
            MemberAvatar(member)
            Spacer(Modifier.width(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isMe) stringResource(R.string.members_me, member.firstName) else member.firstName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = profileLabel(member, isOwner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The avatar as a 56 dp button that opens the photo choice. Without a photo yet, a small camera
 * badge says that one can be added.
 */
@Composable
private fun PhotoButton(member: Member, hasPhoto: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.photo_of, member.firstName)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.photo_change_action), onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MemberAvatar(member)
        if (!hasPhoto) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(3.dp),
                )
            }
        }
    }
}

@Composable
private fun profileLabel(member: Member, isOwner: Boolean): String {
    val profile = stringResource(
        when (member.profile) {
            MemberProfile.Adult -> R.string.profile_adult
            MemberProfile.ChildWithApp -> R.string.profile_child_with_app
            MemberProfile.ChildWithoutApp -> R.string.profile_child_without_app
        },
    )
    return when {
        isOwner -> stringResource(R.string.profile_with_owner, profile)
        member.isAdmin -> stringResource(R.string.profile_with_admin, profile)
        else -> profile
    }
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
            MembersScreen(
                MembersUiState.Success(
                    previewGroups(),
                    "a",
                    ownerId = "a",
                    isOwner = true,
                    canAddMembers = true,
                    editableMemberIds = setOf("a", "b", "c", "d", "e", "f"),
                    renamableHouseholdIds = setOf("h1", "h2"),
                    photoEditableMemberIds = setOf("a", "c"),
                ),
                onRetry = {},
                versionName = "0.1.0",
                onEditMember = {},
                onSavePhoto = { _, _ -> },
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun MembersScreenErrorPreview() {
    FamiliaTheme { Surface { MembersScreen(MembersUiState.Error, onRetry = {}, versionName = "0.1.0") } }
}
