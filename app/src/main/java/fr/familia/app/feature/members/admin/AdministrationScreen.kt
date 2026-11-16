package fr.familia.app.feature.members.admin

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.feature.members.RenameHouseholdDialog
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.members.MembersViewModel.Companion.MAX_HOUSEHOLD_NAME
import fr.familia.app.feature.tree.MonthPick
import java.time.YearMonth

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
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.administration_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                AdministrationUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "chefs-title") {
            SectionTitle(R.string.administration_chefs_title, R.string.administration_chefs_help)
        }
        items(state.adults, key = { "adult-${it.member.id}" }) { row -> AdultSwitchRow(row, onSetChef) }
        item(key = "households-title") {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle(R.string.administration_households_title, R.string.administration_households_help)
        }
        items(state.households, key = { "household-${it.household.id}" }) { row ->
            HouseholdLine(
                row,
                onRename = onRenameHousehold?.let { { renaming = row.household.id } },
                onDelete = { deleting = row.household.id },
            )
        }
        item(key = "household-add") {
            OutlinedButton(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.administration_household_add)) }
        }
        if (onSetFeature != null) {
            item(key = "features-title") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionTitle(R.string.administration_features_title, R.string.administration_features_help)
            }
            items(Feature.entries, key = { "feature-${it.key}" }) { feature ->
                FeatureSwitchRow(feature, on = features[feature.key] == true, onSetFeature = onSetFeature)
            }
        }
        if (onOpenUsage != null) {
            item(key = "usage") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenUsage)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.QueryStats, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (onPickChallenge != null && challenges.isNotEmpty()) {
            item(key = "challenge-title") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionTitle(R.string.administration_challenge_title, R.string.administration_challenge_help)
            }
            item(key = "challenge") { ChallengePickSection(challenges, onPickChallenge) }
        }
        if (capsuleLeftovers.isNotEmpty()) {
            item(key = "capsule-leftovers") { CapsuleLeftoversSection(capsuleLeftovers, onDeleteCapsuleLeftovers) }
        }
        if (consumption != null) {
            item(key = "consumption") { ConsumptionSection(consumption) }
        }
        item(key = "backup") {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExport)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.export_title), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun SectionTitle(titleRes: Int, helpRes: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(helpRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

/** One module: on, the whole family sees it; off, only the admin principal does, to try it. */
@Composable
private fun FeatureSwitchRow(feature: Feature, on: Boolean, onSetFeature: (Feature, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(on, role = Role.Switch) { onSetFeature(feature, it) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(feature.labelRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (on) R.string.administration_feature_on else R.string.administration_feature_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
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
