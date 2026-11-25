package fr.familia.app.feature.members

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.HouseholdGroup
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.aboveSnackbar
import java.time.LocalDate

/** The two tabs of Famille (brief Famille-recettes, règle 1). */
enum class FamilyTab { Households, Tree }

/**
 * Famille (brief Famille-recettes F1, Kévin le 25/11): « 13 personnes · 4 foyers », the tabs
 * Foyers | Arbre, then one block per household, all unfolded, « Mon foyer » first. Each adult but
 * oneself gets Appeler and Message; a touch on a row opens the person's page. Stateless but for the
 * tab and the open dialog.
 */
@Composable
fun MembersScreen(
    state: MembersUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the form; the add button follows the rights in [MembersUiState.Success]. */
    onEditMember: ((memberId: String?) -> Unit)? = null,
    /** Set: every row opens the member's page, whose Modifier keeps the rights of [onEditMember]. */
    onOpenProfile: ((memberId: String) -> Unit)? = null,
    /** The pencil shows on the households of [MembersUiState.Success.renamableHouseholdIds]. */
    onRenameHousehold: ((Household, String) -> Unit)? = null,
    renameFailed: Boolean = false,
    onRenameFailureShown: () -> Unit = {},
    /** The « Arbre » tab (module Arbre on); null leaves the households alone, without tabs. */
    tree: (@Composable () -> Unit)? = null,
    today: LocalDate = familyToday(),
    initialTab: FamilyTab = FamilyTab.Households,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
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
        is MembersUiState.Success -> Column(modifier.fillMaxSize()) {
            FamilyCount(state.groups)
            val showTree = tree != null && tab == FamilyTab.Tree
            if (tree != null) {
                FamilyTabs(tab, onChange = { tab = it })
            }
            if (showTree) {
                Box(Modifier.weight(1f).fillMaxWidth()) { tree?.invoke() }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val canAdd = onEditMember != null && state.canAddMembers
                    HouseholdList(
                        state = state,
                        today = today,
                        onOpen = { member ->
                            onOpenProfile?.let { open -> { open(member.id) } }
                                ?: onEditMember?.takeIf { member.id in state.editableMemberIds }?.let { edit -> { edit(member.id) } }
                        },
                        onRename = { household ->
                            if (onRenameHousehold != null && !state.isOwner && household.id in state.renamableHouseholdIds) {
                                { renaming = household.id }
                            } else {
                                null
                            }
                        },
                        // Room at the bottom so the add button never hides the last row.
                        bottom = if (canAdd) 96.dp else 16.dp,
                    )
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

/** « 13 personnes · 4 foyers », white with a shadow on the photo. */
@Composable
private fun FamilyCount(groups: List<HouseholdGroup>) {
    if (groups.isEmpty()) return
    val (people, homes) = familyCounts(groups)
    val onPhoto = LocalPhotoTheme.current != null
    Text(
        stringResource(
            R.string.members_f1_count,
            pluralStringResource(R.plurals.members_f1_people, people, people),
            pluralStringResource(R.plurals.members_f1_homes, homes, homes),
        ),
        style = if (onPhoto) MaterialTheme.typography.titleMedium.copy(shadow = PhotoSectionShadow) else MaterialTheme.typography.titleMedium,
        color = if (onPhoto) PhotoSectionText else Color.Unspecified,
        modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
    )
}

/**
 * Foyers | Arbre (F1): two halves of 56 dp in a rounded band, the chosen one dark brown with white
 * text; on a photo, the band is cream glass.
 */
@Composable
private fun FamilyTabs(tab: FamilyTab, onChange: (FamilyTab) -> Unit) {
    val labels = listOf(FamilyTab.Households to R.string.members_tab_households, FamilyTab.Tree to R.string.members_tab_tree)
    val onPhoto = LocalPhotoTheme.current != null
    val band = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp)
            .then(if (onPhoto) Modifier.photoCard(28.dp) else Modifier.clip(band).background(MaterialTheme.colorScheme.surfaceVariant))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        labels.forEach { (value, label) ->
            val selected = tab == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) TabActive else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Color.White else if (onPhoto) GlassInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HouseholdList(
    state: MembersUiState.Success,
    today: LocalDate,
    onOpen: (Member) -> (() -> Unit)?,
    onRename: (Household) -> (() -> Unit)?,
    bottom: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottom),
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
        householdSections(state.groups, state.currentMemberId).forEach { section ->
            val household = section.group.household
            item(key = "household-${household?.id ?: "other"}") {
                Column(Modifier.padding(horizontal = ClaySpacing.screen).padding(top = 8.dp, bottom = 6.dp)) {
                    HouseholdTitle(
                        name = when {
                            section.mine -> stringResource(R.string.members_my_household)
                            household != null -> household.name
                            else -> stringResource(R.string.members_other_household)
                        },
                        renameLabel = household?.name,
                        onRename = household?.let(onRename),
                    )
                    HouseholdCard {
                        section.group.members.forEachIndexed { index, member ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalContentColor.current.copy(alpha = 0.12f))
                            MemberRow(
                                member = member,
                                isMe = member.id == state.currentMemberId,
                                isOwner = member.id == state.ownerId,
                                reachable = reachable(member, state.currentMemberId),
                                today = today,
                                onClick = onOpen(member),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseholdTitle(name: String, renameLabel: String?, onRename: (() -> Unit)?) {
    val onPhoto = LocalPhotoTheme.current != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = if (onPhoto) MaterialTheme.typography.titleLarge.copy(shadow = PhotoSectionShadow) else MaterialTheme.typography.titleLarge,
            color = if (onPhoto) PhotoSectionText else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        if (onRename != null && renameLabel != null) {
            IconButton(onClick = onRename, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.household_rename_action, renameLabel),
                    tint = if (onPhoto) PhotoSectionText else LocalContentColor.current,
                )
            }
        }
    }
}

/** A household's card: a ClayCard, or frosted glass on a photo theme (as Cadeaux). */
@Composable
private fun HouseholdCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalPhotoTheme.current == null) {
        ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), content = content)
        return
    }
    CompositionLocalProvider(LocalContentColor provides GlassInk) {
        Column(Modifier.fillMaxWidth().photoCard(24.dp).padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun MemberRow(
    member: Member,
    isMe: Boolean,
    isOwner: Boolean,
    reachable: Boolean,
    today: LocalDate,
    onClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stringResource(R.string.members_open_profile), role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) stringResource(R.string.members_me, member.firstName) else member.firstName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rowLine(member, isOwner, today),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f),
            )
        }
        val phone = member.phone
        if (reachable && phone != null) {
            IconButton(onClick = { dial(context, phone) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.profile_v2_call_name, member.firstName))
            }
            IconButton(onClick = { text(context, phone) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = stringResource(R.string.members_message_name, member.firstName))
            }
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

/** Under the name: « 🎂 Dans 5 jours » within two weeks, else the profile (« Enfant · 11 ans »). */
@Composable
private fun rowLine(member: Member, isOwner: Boolean, today: LocalDate): String {
    birthdaySoonIn(member, today)?.let { days ->
        return if (days == 0) stringResource(R.string.members_f1_birthday_today) else pluralStringResource(R.plurals.members_f1_birthday, days, days)
    }
    if (isOwner) return stringResource(R.string.members_f1_owner)
    val profile = stringResource(
        when (member.profile) {
            MemberProfile.Adult -> R.string.profile_adult
            MemberProfile.ChildWithApp -> R.string.members_f1_child
            MemberProfile.ChildWithoutApp -> R.string.members_f1_child_without_app
        },
    )
    val age = if (member.profile == MemberProfile.Adult) null else ageOf(member, today)
    return when {
        member.isAdmin -> stringResource(R.string.members_f1_with, profile, stringResource(R.string.members_f1_chef))
        age != null -> stringResource(R.string.members_f1_with, profile, pluralStringResource(R.plurals.birthday_age, age, age))
        else -> profile
    }
}

private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits(number)))) }
}

private fun text(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + digits(number)))) }
}

private fun digits(number: String) = number.filter { c -> c.isDigit() || c == '+' }

/** Fictional family used by previews and screenshots (never real names). */
internal fun previewGroups(): List<HouseholdGroup> {
    fun m(id: String, name: String, profile: MemberProfile = MemberProfile.Adult, admin: Boolean = false, born: LocalDate? = null, phone: String? = null) =
        Member(id, name, null, born, "h1", profile, admin, emptyList(), null, phone = phone)
    return listOf(
        HouseholdGroup(
            Household("h1", "Foyer Martin"),
            listOf(
                m("a", "Camille", admin = true, phone = "0600000001"),
                m("b", "Sacha", phone = "0600000002"),
                m("c", "Noé", MemberProfile.ChildWithoutApp, born = LocalDate.of(2021, 4, 2)),
            ),
        ),
        HouseholdGroup(
            Household("h2", "Foyer Bernard"),
            listOf(
                m("d", "Inès", admin = true, born = LocalDate.of(1980, 11, 30), phone = "0600000003"),
                m("e", "Lou", MemberProfile.ChildWithApp, born = LocalDate.of(2014, 2, 3)),
            ),
        ),
        HouseholdGroup(null, listOf(m("f", "Jules"))),
    )
}

@FamiliaPreviews
@androidx.compose.runtime.Composable
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
                ),
                onRetry = {},
                onEditMember = {},
                tree = {},
                today = LocalDate.of(2026, 11, 25),
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun MembersScreenErrorPreview() {
    FamiliaTheme { Surface { MembersScreen(MembersUiState.Error, onRetry = {}) } }
}

/** The ink of the glass cards, as Cadeaux. */
private val GlassInk = Color(0xFF1E1B18)

/** The chosen tab (F1): dark brown. */
private val TabActive = Color(0xFF3A2A1A)
