package fr.familia.app.feature.members

import androidx.compose.ui.res.pluralStringResource
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.core.theme.ClayTint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.HourglassTop
import fr.familia.app.feature.souvenirs.SouvenirFamily
import fr.familia.app.core.ui.ClayCard
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.ui.aboveSnackbar
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fr.familia.app.core.ui.ClaySpacing
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import fr.familia.app.R
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import java.time.LocalDate
import java.time.ZoneId
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.photo.MemberPhotoEditor
import fr.familia.app.feature.members.photo.MemberPhotoViewer

/**
 * Famille tab: the members grouped by household. The settings moved to the
 * sheet of the avatar (DESIGN-12). Stateless.
 */
@Composable
fun MembersScreen(
    state: MembersUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the form; each row and the add button follow the rights in [MembersUiState.Success]. */
    onEditMember: ((memberId: String?) -> Unit)? = null,
    /** « Carnet de recettes », while its module is on (récit 38). */
    onOpenRecipes: (() -> Unit)? = null,
    /** « Capsules temporelles », while their module is on (récit 39). */
    onOpenCapsules: (() -> Unit)? = null,
    onOpenGenealogy: (() -> Unit)? = null,
    /** « Qui est-ce ? » (récit 43), among the treasures. */
    onOpenWhoIsIt: (() -> Unit)? = null,
    /** The pencil shows on the households of [MembersUiState.Success.renamableHouseholdIds]. */
    onRenameHousehold: ((Household, String) -> Unit)? = null,
    renameFailed: Boolean = false,
    onRenameFailureShown: () -> Unit = {},
    /** The avatar of [MembersUiState.Success.photoEditableMemberIds] opens the photo choice (PRD, récit 3 bis). */
    onSavePhoto: ((memberId: String, jpeg: ByteArray) -> Unit)? = null,
    onRemovePhoto: (memberId: String) -> Unit = {},
    photoFailed: Boolean = false,
    onPhotoFailureShown: () -> Unit = {},
    /** The gift button of each row opens the member's « Mes envies » (PRD, récit 7). */
    onOpenWishes: ((memberId: String) -> Unit)? = null,
    /** Set: every row opens the member's page, whose Modifier keeps the rights of [onEditMember]. */
    onOpenProfile: ((memberId: String) -> Unit)? = null,
    /**
     * R1 bis (§4, R5): « Nous 13, en 4 foyers », one card of color per household with all its
     * members.
     */
    r1: Boolean = false,
    /**
     * « Famille par foyer » (récit 64, Kévin le 18/11): me first, my household, then the other
     * households folded. Behind its switch, off for everyone until Kévin turns it on.
     */
    byHousehold: Boolean = false,
    today: LocalDate = LocalDate.now(ZoneId.of("Europe/Paris")),
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var unfolded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var photoOf by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
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
                // « Nos trésors » left the Famille tab (Kévin, 18/11): recettes, arbre, capsule and
                // « Qui est-ce ? » are all in « Tout Familia ». The tab is the family only.
                // One member's row, the same in every presentation: same clicks, same rights.
                val memberRow: @Composable (Member) -> Unit = { member ->
                    MemberRow(
                        member = member,
                        isMe = member.id == state.currentMemberId,
                        isOwner = member.id == state.ownerId,
                        onClick = onOpenProfile?.let { open -> { open(member.id) } }
                            ?: onEditMember?.takeIf { member.id in state.editableMemberIds }?.let { edit -> { edit(member.id) } },
                        hasPhoto = member.id in photos,
                        onPhotoClick = when {
                            member.id in photos -> { { viewing = member.id } }
                            onSavePhoto != null && member.id in state.photoEditableMemberIds -> { { photoOf = member.id } }
                            else -> null
                        },
                        onWishesClick = onOpenWishes?.let { open -> { open(member.id) } },
                    )
                }
                if (byHousehold && state.groups.isNotEmpty()) {
                    familyByHousehold(
                        layout = familyTabLayout(state.groups, state.currentMemberId),
                        today = today,
                        onOpenMine = onOpenProfile ?: onEditMember?.takeIf { state.currentMemberId in state.editableMemberIds }?.let { edit -> { id: String -> edit(id) } },
                        householdName = { group -> group.household?.name ?: stringResource(R.string.members_other_household) },
                        header = { group ->
                            val household = group.household
                            HouseholdHeader(
                                name = household?.name ?: stringResource(R.string.members_other_household),
                                onRename = if (household != null && onRenameHousehold != null && !state.isOwner &&
                                    household.id in state.renamableHouseholdIds
                                ) {
                                    { renaming = household.id }
                                } else {
                                    null
                                },
                            )
                        },
                        row = memberRow,
                        open = unfolded,
                        onToggle = { key -> unfolded = if (key in unfolded) unfolded - key else unfolded + key },
                    )
                }
                if (!byHousehold && r1 && state.groups.isNotEmpty()) {
                    item(key = "count") {
                        val people = state.groups.sumOf { it.members.size }
                        val homes = state.groups.count { it.household != null }
                        Text(
                            pluralStringResource(R.plurals.members_r1_count, people, people, pluralStringResource(R.plurals.members_r1_homes, homes, homes)),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp).semantics { heading() },
                        )
                    }
                }
                if (state.groups.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Groups,
                            title = stringResource(R.string.members_empty_title),
                            body = stringResource(R.string.members_empty_body),
                        )
                    }
                }
                if (!byHousehold && r1) {
                    state.groups.forEachIndexed { index, group ->
                        item(key = "card-${group.household?.id ?: "other"}") {
                            val household = group.household
                            ClayCard(
                                tint = R1_HOUSEHOLD_TINTS[index % R1_HOUSEHOLD_TINTS.size],
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 6.dp),
                            ) {
                                HouseholdHeader(
                                    name = household?.name ?: stringResource(R.string.members_other_household),
                                    onRename = if (household != null && onRenameHousehold != null && !state.isOwner &&
                                        household.id in state.renamableHouseholdIds
                                    ) {
                                        { renaming = household.id }
                                    } else {
                                        null
                                    },
                                )
                                group.members.forEach { member ->
                                    MemberRow(
                                        member = member,
                                        isMe = member.id == state.currentMemberId,
                                        isOwner = member.id == state.ownerId,
                                        onClick = onOpenProfile?.let { open -> { open(member.id) } }
                                            ?: onEditMember?.takeIf { member.id in state.editableMemberIds }?.let { edit -> { edit(member.id) } },
                                        hasPhoto = member.id in photos,
                                        onPhotoClick = when {
                                            member.id in photos -> { { viewing = member.id } }
                                            onSavePhoto != null && member.id in state.photoEditableMemberIds -> { { photoOf = member.id } }
                                            else -> null
                                        },
                                        onWishesClick = onOpenWishes?.let { open -> { open(member.id) } },
                                    )
                                }
                            }
                        }
                    }
                }
                if (!byHousehold && !r1) state.groups.forEach { group ->
                    item(key = "household-${group.household?.id ?: "other"}") {
                        val household = group.household
                        HouseholdHeader(
                            name = household?.name ?: stringResource(R.string.members_other_household),
                            // The admin principal renames every household in Administration (DESIGN-5);
                            // a chef de famille keeps the pencil on their own household here.
                            onRename = if (household != null && onRenameHousehold != null && !state.isOwner &&
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
                            onClick = onOpenProfile?.let { open -> { open(member.id) } }
                                ?: onEditMember?.takeIf { member.id in state.editableMemberIds }?.let { edit -> { edit(member.id) } },
                            hasPhoto = member.id in photos,
                            // A photo opens in full screen; without one, those who may add it choose one.
                            onPhotoClick = when {
                                member.id in photos -> { { viewing = member.id } }
                                onSavePhoto != null && member.id in state.photoEditableMemberIds -> { { photoOf = member.id } }
                                else -> null
                            },
                            onWishesClick = onOpenWishes?.let { open -> { open(member.id) } },
                        )
                    }
                }
            }
            if (canAdd && onEditMember != null) {
                ExtendedFloatingActionButton(
                    onClick = { onEditMember(null) },
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.members_add)) },
                    text = { Text(stringResource(R.string.members_add)) },
                    modifier = Modifier.align(Alignment.BottomEnd).aboveSnackbar().padding(16.dp),
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
    val viewed = (state as? MembersUiState.Success)?.groups?.firstNotNullOfOrNull { group ->
        group.members.firstOrNull { it.id == viewing }
    }
    val viewedPhoto = viewed?.let { photos[it.id] }
    if (viewed != null && viewedPhoto != null) {
        val canChange = onSavePhoto != null && viewed.id in (state as MembersUiState.Success).photoEditableMemberIds
        MemberPhotoViewer(
            member = viewed,
            photo = viewedPhoto,
            onDismiss = { viewing = null },
            onChange = if (canChange) {
                {
                    viewing = null
                    photoOf = viewed.id
                }
            } else {
                null
            },
        )
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


@Composable
private fun HouseholdHeader(name: String, onRename: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ClaySpacing.screen, end = 4.dp, top = 8.dp),
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

@Composable
private fun MemberRow(
    member: Member,
    isMe: Boolean,
    isOwner: Boolean,
    onClick: (() -> Unit)?,
    hasPhoto: Boolean = false,
    onPhotoClick: (() -> Unit)? = null,
    onWishesClick: (() -> Unit)? = null,
) {
    val editLabel = stringResource(R.string.members_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = editLabel, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPhotoClick != null) {
            PhotoButton(member, hasPhoto, onPhotoClick)
            Spacer(Modifier.width(8.dp))
        } else {
            MemberAvatar(member)
            Spacer(Modifier.width(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
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
        if (onWishesClick != null) {
            val wishesLabel = stringResource(R.string.gift_wishes_of, member.firstName)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onWishesClick,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = wishesLabel },
            ) {
                Icon(Icons.Outlined.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.members_wishes_button))
            }
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
            .clickable(
                onClickLabel = stringResource(if (hasPhoto) R.string.photo_view_action else R.string.photo_change_action),
                onClick = onClick,
            )
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
    val label = when {
        isOwner -> stringResource(R.string.profile_with_owner, profile)
        member.isAdmin -> stringResource(R.string.profile_with_admin, profile)
        else -> profile
    }
    // A member prepared ahead: whoever manages the household sees that the invitation is still to come.
    return if (member.email == null && member.profile != MemberProfile.ChildWithoutApp) {
        stringResource(R.string.profile_without_email, label)
    } else {
        label
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
                onEditMember = {},
                onSavePhoto = { _, _ -> },
                onOpenWishes = {},
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun MembersScreenErrorPreview() {
    FamiliaTheme { Surface { MembersScreen(MembersUiState.Error, onRetry = {}) } }
}

/** One color per household in the R1 bis Famille, in the order of the households. */
private val R1_HOUSEHOLD_TINTS = listOf(ClayTint.Household, ClayTint.Family, ClayTint.Birthday, ClayTint.Plain)
