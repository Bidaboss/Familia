package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.ClayCard
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.feature.members.RenameHouseholdDialog
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.FeatureTier
import fr.familia.app.core.model.tier
import fr.familia.app.feature.discover.DiscoverModule
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.MembersViewModel.Companion.MAX_HOUSEHOLD_NAME
import fr.familia.app.feature.tree.MonthPick
import java.time.YearMonth
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.LoadingState

/** The Administration menu of the admin principal (PRD, récit 1 ter). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministrationScreen(
    state: AdministrationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSetChef: (Member, Boolean) -> Unit,
    onAddHousehold: (String) -> Unit,
    onDeleteHousehold: (Household) -> Unit,
    onOpenExport: () -> Unit,
    /** « Utilisation » (PRD, récit 1 nonies). */
    onOpenUsage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actionFailed: Boolean = false,
    onFailureShown: () -> Unit = {},
    /** Modules switched on for the family (config/app.features); the others stay in test. */
    features: Map<String, Boolean> = emptyMap(),
    onSetFeature: ((Feature, Boolean) -> Unit)? = null,
    /** Renames a household (moved here from the Famille tab, DESIGN-5); hidden when null. */
    onRenameHousehold: ((Household, String) -> Unit)? = null,
    /** « Consommation » (PRD, récit 1 sexies); hidden when null. */
    consumption: ConsumptionCard? = null,
    /** « Défi du mois » (récit 35): this month and the next; hidden when [onPickChallenge] is null. */
    challenges: List<MonthPick> = emptyList(),
    onPickChallenge: ((YearMonth, String) -> Unit)? = null,
    /** Sealed capsules of members who left (PRD rév. 184); hidden when empty. */
    capsuleLeftovers: List<CapsuleLeftover> = emptyList(),
    onDeleteCapsuleLeftovers: (String) -> Unit = {},
    /** « Ordre des foyers » (récit 35 bis); hidden when [onSaveRotation] is null. */
    rotation: ChallengeRotation.Schedule? = null,
    onSaveRotation: ((YearMonth, List<String>) -> Unit)? = null,
    /** « Voir comme » (PRD, rôles): the members to look through the eyes of; hidden when [onViewAs] is null. */
    viewAsMembers: List<Member> = emptyList(),
    onViewAs: ((Member) -> Unit)? = null,
    /** « Partager Familia » (récit 1 bis bis): the install link for the whole family. */
    onShareApp: (() -> Unit)? = null,
    /** « Retours » (récit 6 sexies): the ideas and problems of the family; [feedbackUnseen] are new. */
    onOpenFeedback: (() -> Unit)? = null,
    feedbackUnseen: Int = 0,
    /** « Plantages » (récit 6 sexies, rév. 220): the kinds of crash still to look at. */
    onOpenCrashes: (() -> Unit)? = null,
    crashCount: Int = 0,
    /** « Envoyer une notification » (récit 69): opens the form; hidden when null (module off). */
    onOpenAnnouncements: (() -> Unit)? = null,
    /** « Surprises offertes récemment » (brief cadeaux § 9), admin principal only. */
    onOpenOfferedSurprises: (() -> Unit)? = null,
    /** « Ordre d'À découvrir » (AVIS-19 partie 2): the order in force; hidden when [onSaveDiscoverOrder] is null. */
    discoverOrder: List<DiscoverModule> = DiscoverModule.entries,
    discoverInTutorial: Set<DiscoverModule> = emptySet(),
    onSaveDiscoverOrder: ((List<DiscoverModule>) -> Unit)? = null,
    /** « Liens d'agenda » (audit du 17/11): cut an adult's calendar link; hidden when null. */
    calendarLinks: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.themeBackground(),
        containerColor = Color.Transparent,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.administration_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                // One help sentence on the screen at most (DESIGN-11): the others are in the « ? ».
                actions = { HelpButton(ScreenHelp.Administration) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                AdministrationUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                AdministrationUiState.Error -> EmptyState(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = stringResource(R.string.members_error_title),
                    body = stringResource(R.string.administration_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                AdministrationUiState.NotAllowed -> EmptyState(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = stringResource(R.string.administration_not_allowed_title),
                    body = stringResource(R.string.administration_not_allowed_body),
                )
                is AdministrationUiState.Ready ->
                    ReadyContent(
                        state, onSetChef, onAddHousehold, onDeleteHousehold, onOpenExport, features, onSetFeature, onRenameHousehold,
                        onOpenUsage, consumption, challenges, onPickChallenge, capsuleLeftovers, onDeleteCapsuleLeftovers,
                        rotation, onSaveRotation, viewAsMembers, onViewAs, onShareApp, onOpenFeedback, feedbackUnseen,
                        onOpenCrashes, crashCount, onOpenAnnouncements, onOpenOfferedSurprises,
                        discoverOrder, discoverInTutorial, onSaveDiscoverOrder, calendarLinks,
                    )
            }
        }
    }
    if (actionFailed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            title = { Text(stringResource(R.string.administration_failed_title)) },
            text = { Text(stringResource(R.string.administration_failed_body)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun ReadyContent(
    state: AdministrationUiState.Ready,
    onSetChef: (Member, Boolean) -> Unit,
    onAddHousehold: (String) -> Unit,
    onDeleteHousehold: (Household) -> Unit,
    onOpenExport: () -> Unit,
    features: Map<String, Boolean>,
    onSetFeature: ((Feature, Boolean) -> Unit)?,
    onRenameHousehold: ((Household, String) -> Unit)?,
    onOpenUsage: (() -> Unit)?,
    consumption: ConsumptionCard?,
    challenges: List<MonthPick>,
    onPickChallenge: ((YearMonth, String) -> Unit)?,
    capsuleLeftovers: List<CapsuleLeftover>,
    onDeleteCapsuleLeftovers: (String) -> Unit,
    rotation: ChallengeRotation.Schedule?,
    onSaveRotation: ((YearMonth, List<String>) -> Unit)?,
    viewAsMembers: List<Member>,
    onViewAs: ((Member) -> Unit)?,
    onShareApp: (() -> Unit)?,
    onOpenFeedback: (() -> Unit)?,
    feedbackUnseen: Int,
    onOpenCrashes: (() -> Unit)?,
    crashCount: Int,
    onOpenAnnouncements: (() -> Unit)?,
    onOpenOfferedSurprises: (() -> Unit)?,
    discoverOrder: List<DiscoverModule>,
    discoverInTutorial: Set<DiscoverModule>,
    onSaveDiscoverOrder: ((List<DiscoverModule>) -> Unit)?,
    calendarLinks: (@Composable () -> Unit)?,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var choosingViewAs by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // Retour de Kévin, 17/11 : « l'onglet administration est trop grand ». Everything is grouped
    // under a few headers, all closed at first: one touch opens a group.
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    fun toggle(key: String) {
        open = if (key in open) open - key else open + key
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onViewAs != null && viewAsMembers.isNotEmpty()) {
            adminGroup("demo", R.string.administration_group_demo, R.string.administration_group_demo_help, open, ::toggle) {
                if (onViewAs != null && viewAsMembers.isNotEmpty()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { choosingViewAs = true })
                                .heightIn(min = 56.dp)
                                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Visibility, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.view_as_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.view_as_help),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onOpenOfferedSurprises != null) {
            item(key = "offeredSurprises") {
                ClayCard(onClick = onOpenOfferedSurprises, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.offered_surprises_title), style = dayHeaderTextStyle())
                            Text(
                                stringResource(R.string.offered_surprises_entry_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.CardGiftcard, contentDescription = null)
                    }
                }
            }
        }
        if (onOpenAnnouncements != null) {
            item(key = "announcements") {
                ClayCard(onClick = onOpenAnnouncements, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.announcements_title), style = dayHeaderTextStyle())
                            Text(
                                stringResource(R.string.announcements_entry_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.Campaign, contentDescription = null)
                    }
                }
            }
        }
        if (onOpenFeedback != null || onOpenCrashes != null) {
            adminGroup(
                "feedback", R.string.administration_group_feedback, R.string.administration_group_feedback_help, open, ::toggle,
                badge = (if (onOpenFeedback != null) feedbackUnseen else 0) + (if (onOpenCrashes != null) crashCount else 0),
            ) {
                if (onOpenFeedback != null) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenFeedback)
                                .heightIn(min = 56.dp)
                                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.feedback_list_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.feedback_list_help),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (feedbackUnseen > 0) {
                                Spacer(Modifier.width(8.dp))
                                Badge { Text(stringResource(R.string.feedback_list_count, feedbackUnseen)) }
                            }
                        }
                    }
                }
                if (onOpenCrashes != null) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenCrashes)
                                .heightIn(min = 56.dp)
                                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.crashes_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.crashes_help),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (crashCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Badge { Text(stringResource(R.string.crashes_count, crashCount)) }
                            }
                        }
                    }
                }
            }
        }
        adminGroup("family", R.string.administration_group_family, R.string.administration_group_family_help, open, ::toggle) {
            Column {
                SectionTitle(R.string.administration_chefs_title)
            }
            state.adults.forEach { row -> AdultSwitchRow(row, onSetChef) }
            Column {
                SectionTitle(R.string.administration_households_title)
            }
            state.households.forEach { row ->
                HouseholdLine(
                    row,
                    onRename = onRenameHousehold?.let { { renaming = row.household.id } },
                    onDelete = { deleting = row.household.id },
                )
            }
            Column {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.administration_household_add)) }
            }
            if (onShareApp != null) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShareApp)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.share_app_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.share_app_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (onSetFeature != null) {
            adminGroup("features", R.string.administration_features_title, R.string.administration_features_help, open, ::toggle) {
                // BUG-2 (Kévin le 18/11): the modules au choix, then the new versions, the base folded at the bottom.
                val byTier = Feature.entries.groupBy { it.tier }
                Column { SectionTitle(R.string.administration_features_choice) }
                byTier[FeatureTier.Choice].orEmpty().forEach { feature ->
                    FeatureSwitchRow(feature, on = features[feature.key] ?: feature.onByDefault, onSetFeature = onSetFeature)
                }
                Column {
                    SectionTitle(R.string.administration_features_new, R.string.administration_features_new_help)
                }
                byTier[FeatureTier.NewVersion].orEmpty().forEach { feature ->
                    FeatureSwitchRow(feature, on = features[feature.key] ?: feature.onByDefault, onSetFeature = onSetFeature)
                }
                // « La base » has no switch any more (Kévin le 18/11, 20 h 39): always on, only named here.
                Column {
                    SectionTitle(R.string.administration_features_base)
                    Text(
                        stringResource(
                            R.string.administration_features_base_help,
                            byTier[FeatureTier.Base].orEmpty().map { stringResource(it.labelRes) }.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
                    )
                }
            }
        }
        if ((onPickChallenge != null && challenges.isNotEmpty()) || capsuleLeftovers.isNotEmpty() ||
            (onSaveRotation != null && rotation != null && rotation.households.isNotEmpty()) || onSaveDiscoverOrder != null
        ) {
            adminGroup("life", R.string.administration_group_life, R.string.administration_group_life_help, open, ::toggle) {
                if (onPickChallenge != null && challenges.isNotEmpty()) {
                    Column {
                        SectionTitle(R.string.administration_challenge_title)
                    }
                    Column { ChallengePickSection(challenges, onPickChallenge) }
                }
                if (capsuleLeftovers.isNotEmpty()) {
                    Column { CapsuleLeftoversSection(capsuleLeftovers, onDeleteCapsuleLeftovers) }
                }
                if (onSaveRotation != null && rotation != null && rotation.households.isNotEmpty()) {
                    Column {
                        SectionTitle(R.string.administration_rotation_title)
                    }
                    Column { ChallengeRotationSection(rotation, onSaveRotation) }
                }
                if (onSaveDiscoverOrder != null) {
                    Column {
                        SectionTitle(R.string.administration_discover_title)
                    }
                    Column { DiscoverOrderSection(discoverOrder, discoverInTutorial, onSaveDiscoverOrder) }
                }
            }
        }
        if (onOpenUsage != null || consumption != null) {
            adminGroup("follow", R.string.administration_group_follow, R.string.administration_group_follow_help, open, ::toggle) {
                if (onOpenUsage != null) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenUsage)
                                .heightIn(min = 56.dp)
                                .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.QueryStats, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (consumption != null) {
                    Column { ConsumptionSection(consumption) }
                }
                // Kévin le 18/11 : le budget Firebase en un geste, depuis Administration.
                Column {
                    val uri = LocalUriHandler.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uri.openUri(FIREBASE_BUDGET_URL) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Savings, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.administration_firebase_budget), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.administration_firebase_budget_help),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        adminGroup("data", R.string.administration_group_data, R.string.administration_group_data_help, open, ::toggle) {
            if (calendarLinks != null) Column { calendarLinks() }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenExport)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = ClaySpacing.screen, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    val toRename = state.households.firstOrNull { it.household.id == renaming }?.household
    if (toRename != null && onRenameHousehold != null) {
        RenameHouseholdDialog(
            household = toRename,
            onConfirm = { name -> onRenameHousehold(toRename, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    if (choosingViewAs && onViewAs != null) {
        ViewAsDialog(
            members = viewAsMembers,
            onPick = { member -> choosingViewAs = false; onViewAs(member) },
            onDismiss = { choosingViewAs = false },
        )
    }
    if (adding) {
        NewHouseholdDialog(onConfirm = { name -> onAddHousehold(name); adding = false }, onDismiss = { adding = false })
    }
    val toDelete = state.households.firstOrNull { it.household.id == deleting && it.memberCount == 0 }?.household
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.administration_household_delete_title, toDelete.name)) },
            text = { Text(stringResource(R.string.administration_household_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteHousehold(toDelete); deleting = null },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.administration_household_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_keep))
                }
            },
        )
    }
}

/**
 * A group of Administration, in its own card (refonte, lot 3): a header of 56 dp that opens or
 * closes it, then, open, its lines in the card. [badge] shows on the header what waits inside (new
 * retours, plantages), even closed.
 */
private fun LazyListScope.adminGroup(
    key: String,
    titleRes: Int,
    helpRes: Int,
    open: List<String>,
    onToggle: (String) -> Unit,
    badge: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = key in open
    item(key = "group-$key") {
        val state = stringResource(if (expanded) R.string.administration_group_open else R.string.administration_group_closed)
        ClayCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = state) { onToggle(key) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = ClaySpacing.screen, vertical = 12.dp)
                    .semantics {
                        heading()
                        stateDescription = state
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(titleRes), style = dayHeaderTextStyle())
                    Text(
                        stringResource(helpRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (badge > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(badge.toString()) }
                }
                Spacer(Modifier.width(8.dp))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun SectionTitle(titleRes: Int, helpRes: Int? = null) {
    Column(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        if (helpRes != null) {
            Text(
                stringResource(helpRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One adult: the switch makes them chef de famille. The admin principal has every right anyway. */
@Composable
private fun AdultSwitchRow(row: AdultRow, onSetChef: (Member, Boolean) -> Unit) {
    val member = row.member
    val toggle = if (row.isOwner) {
        Modifier
    } else {
        Modifier.toggleable(member.isAdmin, role = Role.Switch) { on -> onSetChef(member, on) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(toggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.firstName, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    row.isOwner -> stringResource(R.string.administration_owner_row)
                    else -> row.householdName ?: stringResource(R.string.members_other_household)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!row.isOwner) Switch(checked = member.isAdmin, onCheckedChange = null)
    }
}

/**
 * One module: on, the whole family sees it; off, only the admin principal does, to try it. Under it,
 * what it is for and where it shows, « Publier Firebase » first when it has server parts, and
 * « Voir où » when a screen shows it (PRODUIT, 17/11).
 */
@Composable
private fun FeatureSwitchRow(feature: Feature, on: Boolean, onSetFeature: (Feature, Boolean) -> Unit) {
    val name = stringResource(feature.labelRes)
    val seeWhere = LocalFeatureSeeWhere.current(feature)
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(on, role = Role.Switch) { onSetFeature(feature, it) }
                .heightIn(min = 56.dp)
                .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (on) R.string.administration_feature_on else R.string.administration_feature_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
        Column(Modifier.padding(horizontal = ClaySpacing.screen), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            featureHelpRes(feature)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            if (feature in NEEDS_FIREBASE_PUBLISH) {
                Text(
                    stringResource(R.string.administration_feature_publish_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (seeWhere != null) {
                val label = stringResource(R.string.administration_feature_see_where_label, name)
                TextButton(
                    onClick = seeWhere,
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = label },
                ) { Text(stringResource(R.string.administration_feature_see_where)) }
            }
        }
    }
}

@Composable
private fun HouseholdLine(row: HouseholdRow, onRename: (() -> Unit)?, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.household.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.administration_household_members, row.memberCount, row.memberCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRename != null) {
            IconButton(onClick = onRename, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.household_rename_action, row.household.name))
            }
        }
        // Only an empty household can go: its members would otherwise point to nothing.
        if (row.memberCount == 0) {
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.administration_household_delete_action, row.household.name),
                )
            }
        }
    }
}

@Composable
private fun NewHouseholdDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.administration_household_add)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_HOUSEHOLD_NAME) },
                label = { Text(stringResource(R.string.member_household_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Fictional family for previews and screenshots (never real names). */
internal fun previewAdministrationState(): AdministrationUiState.Ready {
    fun m(id: String, name: String, household: String, chef: Boolean = false) =
        Member(id, name, null, null, household, MemberProfile.Adult, chef, emptyList(), null)
    return AdministrationUiState.Ready(
        adults = listOf(
            AdultRow(m("a", "Camille", "h1", chef = true), "Foyer Martin", isOwner = true),
            AdultRow(m("d", "Inès", "h2", chef = true), "Foyer Bernard", isOwner = false),
            AdultRow(m("b", "Sacha", "h1"), "Foyer Martin", isOwner = false),
        ),
        households = listOf(
            HouseholdRow(Household("h2", "Foyer Bernard"), 2),
            HouseholdRow(Household("h1", "Foyer Martin"), 3),
            HouseholdRow(Household("h3", "Foyer Petit"), 0),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun AdministrationScreenPreview() {
    FamiliaTheme {
        Surface {
            AdministrationScreen(
                previewAdministrationState(),
                onBack = {},
                onRetry = {},
                onSetChef = { _, _ -> },
                onAddHousehold = {},
                onDeleteHousehold = {},
                onOpenExport = {},
                features = mapOf("gifts" to true),
                onSetFeature = { _, _ -> },
            )
        }
    }
}

/** « Voir comme… »: one line per member, the app opens as that member sees it. */
@Composable
private fun ViewAsDialog(members: List<Member>, onPick: (Member) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.view_as_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(members, key = { "view-as-${it.id}" }) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { onPick(member) })
                            .heightIn(min = 56.dp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MemberAvatar(member)
                        Spacer(Modifier.width(16.dp))
                        Text(member.firstName, style = MaterialTheme.typography.titleMedium)
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

/** Utilisation et facturation du projet Firebase : la consommation, le forfait et l'alerte de budget. */
private const val FIREBASE_BUDGET_URL = "https://console.firebase.google.com/project/familia-93286/usage/details"
