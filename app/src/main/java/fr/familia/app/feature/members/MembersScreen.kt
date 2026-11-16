package fr.familia.app.feature.members

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
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.ui.aboveSnackbar
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import fr.familia.app.core.share.canShareApp
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.photo.MemberPhotoEditor
import fr.familia.app.feature.members.photo.MemberPhotoViewer

/** Famille tab: members grouped by household, then the installed version. Stateless. */
@Composable
fun MembersScreen(
    state: MembersUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the form; each row and the add button follow the rights in [MembersUiState.Success]. */
    onEditMember: ((memberId: String?) -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    /** Shown to the admin principal only (PRD, récit 1 ter). */
    onOpenAdministration: (() -> Unit)? = null,
    onOpenUpdates: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    /** Famille › Réglages › Apparence, while the Thèmes module is on (DESIGN-7). */
    onOpenAppearance: (() -> Unit)? = null,
    /**
     * « Temps de mes enfants » (carte EXPERT-5): shown to an adult responsible for a child with the
     * app. The admin principal finds everyone's time in Administration instead.
     */
    onOpenChildrenUsage: (() -> Unit)? = null,
    /** « Carnet de recettes », while its module is on (récit 38). */
    onOpenRecipes: (() -> Unit)? = null,
    /** « Capsules temporelles », while their module is on (récit 39). */
    onOpenCapsules: (() -> Unit)? = null,
    onOpenGenealogy: (() -> Unit)? = null,
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
    /** « Partager Familia » (récit 1 bis bis), shown to adults, given the admin principal's first name. */
    onShareApp: ((ownerFirstName: String?) -> Unit)? = null,
    /** « Une idée ? Un souci ? » (récit 6 sexies), for every member. */
    onOpenFeedback: (() -> Unit)? = null,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
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
                // « Nos trésors » at the top (DESIGN-12, avancé au 21 par PRODUIT): recettes, arbre and
                // capsules as tiles. Each one stays behind its module; no module on, no row.
                val treasures = listOfNotNull(
                    onOpenRecipes?.let {
                        Treasure("recipes", Icons.Rounded.RestaurantMenu, SouvenirFamily.Party, R.string.members_treasure_recipes, R.string.members_treasure_recipes_open, it)
                    },
                    onOpenGenealogy?.let {
                        Treasure("genealogy", Icons.Rounded.AccountTree, SouvenirFamily.Help, R.string.members_treasure_tree, R.string.members_treasure_tree_open, it)
                    },
                    onOpenCapsules?.let {
                        Treasure("capsules", Icons.Rounded.HourglassTop, SouvenirFamily.Trips, R.string.members_treasure_capsules, R.string.members_treasure_capsules_open, it)
                    },
                )
                if (treasures.isNotEmpty()) {
                    item(key = "treasures") { TreasureTiles(treasures) }
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
                state.groups.forEach { group ->
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
                val everyone = state.groups.flatMap { it.members }
                if (onShareApp != null && canShareApp(everyone.firstOrNull { it.id == state.currentMemberId })) {
                    val ownerName = everyone.firstOrNull { it.id == state.ownerId }?.firstName
                    item(key = "share-app") { SettingsRow(Icons.Outlined.Share, R.string.share_app_title, onClick = { onShareApp(ownerName) }) }
                }
                item(key = "settings-title") {
                    Column {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(R.string.members_settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 16.dp, bottom = 4.dp)
                                .semantics { heading() },
                        )
                    }
                }
                if (onOpenNotifications != null) {
                    item(key = "notifications") {
                        SettingsRow(Icons.Outlined.Notifications, R.string.notifications_title, onOpenNotifications)
                    }
                }
                if (onOpenAppearance != null) {
                    item(key = "appearance") { SettingsRow(Icons.Outlined.Palette, R.string.appearance_title, onOpenAppearance) }
                }
                val hasChildWithApp = state.groups.any { group ->
                    group.members.any { it.profile == MemberProfile.ChildWithApp && state.currentMemberId in it.parentIds }
                }
                if (onOpenChildrenUsage != null && !state.isOwner && hasChildWithApp) {
                    item(key = "children-usage") {
                        SettingsRow(Icons.Outlined.QueryStats, R.string.usage_children_title, onOpenChildrenUsage)
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
                if (onOpenFeedback != null) {
                    item(key = "feedback") { SettingsRow(Icons.Outlined.Lightbulb, R.string.feedback_title, onOpenFeedback) }
                }
                if (onOpenHelp != null) {
                    item(key = "help") { SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, R.string.help_title, onOpenHelp) }
                }
            }
            if (canAdd && onEditMember != null) {
                ExtendedFloatingActionButton(
                    onClick = { onEditMember(null) },
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
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

private class Treasure(
    val key: String,
    val icon: ImageVector,
    val family: SouvenirFamily,
    @StringRes val labelRes: Int,
    @StringRes val openLabelRes: Int,
    val onClick: () -> Unit,
) {
    /**
     * The short form used before the tiles (« Qui est-ce ? », #173): a warm color, and its name read
     * as is. Lets either PR land first without breaking main.
     */
    constructor(key: String, icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) :
        this(key, icon, SouvenirFamily.Share, labelRes, labelRes, onClick)
}

/**
 * « Nos trésors » (DESIGN-12): a small title, then one tile per module on, of equal width. A tile
 * alone takes the whole width, its picture on the left.
 */
@Composable
private fun TreasureTiles(treasures: List<Treasure>) {
    Column(Modifier.padding(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.members_treasures),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.08.em),
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            treasures.forEach { treasure ->
                ClayCard(
                    modifier = Modifier.weight(1f).heightIn(min = 112.dp),
                    onClick = treasure.onClick,
                    onClickLabel = stringResource(treasure.openLabelRes),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    if (treasures.size == 1) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TreasureBadge(treasure)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(treasure.labelRes), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            TreasureBadge(treasure)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(treasure.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The 48 dp disc in the light tint of its family, with the filled picture in the dark one. */
@Composable
private fun TreasureBadge(treasure: Treasure) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(treasure.family.light),
        contentAlignment = Alignment.Center,
    ) {
        Icon(treasure.icon, contentDescription = null, tint = treasure.family.dark, modifier = Modifier.size(28.dp))
    }
}

/** A settings entry below the members (design: settings live in the Famille tab). */
@Composable
private fun SettingsRow(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit, indent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = if (indent) ClaySpacing.screen + 40.dp else ClaySpacing.screen, end = ClaySpacing.screen, top = 12.dp, bottom = 12.dp),
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
