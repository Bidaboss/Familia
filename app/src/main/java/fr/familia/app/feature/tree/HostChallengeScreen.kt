package fr.familia.app.feature.tree

import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.core.ui.ClaySpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Connects the guide of the challenge of a foyer to its ViewModel. */
@Composable
fun HostChallengeRoute(container: AppContainer?, currentMember: Member, month: YearMonth, onClose: (HostedStatus?) -> Unit) {
    val viewModel: HostChallengeViewModel = viewModel(
        key = "hostChallenge-$month",
        factory = viewModelFactory {
            initializer { HostChallengeViewModel(container?.treeRepository, container?.memberRepository, currentMember, month) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { state.done?.let(onClose) }
    HostChallengeScreen(
        state = state,
        onBack = { if (state.step > 1) viewModel.back() else onClose(null) },
        actions = HostGuideActions(
            chooseKind = viewModel::chooseKind,
            setTarget = viewModel::setTarget,
            setTitle = viewModel::setTitle,
            setEmoji = viewModel::setEmoji,
            setIllustration = viewModel::setIllustration,
            next = viewModel::next,
            saveDraft = viewModel::saveDraft,
            publish = viewModel::publish,
            skip = viewModel::skip,
            failureShown = viewModel::failureShown,
        ),
    )
}

/** What the guide can do, so the screen stays a function of its state. */
data class HostGuideActions(
    val chooseKind: (HostedKind) -> Unit = {},
    val setTarget: (Int) -> Unit = {},
    val setTitle: (String) -> Unit = {},
    val setEmoji: (String) -> Unit = {},
    val setIllustration: (HostedIllustration) -> Unit = {},
    val next: () -> Unit = {},
    val saveDraft: () -> Unit = {},
    val publish: () -> Unit = {},
    val skip: () -> Unit = {},
    val failureShown: () -> Unit = {},
)

private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)

/** « de décembre », « d'avril », like the notice of the server. */
fun ofMonth(month: YearMonth): String {
    val name = month.format(monthName)
    return if (name.first() in "aeiouéèh") "d'$name" else "de $name"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostChallengeScreen(state: HostGuideState, onBack: () -> Unit, actions: HostGuideActions, modifier: Modifier = Modifier) {
    var skipping by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.host_title, ofMonth(state.month)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (state.canPublish) {
                        TextButton(onClick = { skipping = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.host_skip))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
        ) {
            Text(
                stringResource(R.string.host_step, state.step),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.step) {
                1 -> KindStep(state.draft, actions)
                2 -> TargetStep(state.draft, actions)
                3 -> LookStep(state.draft, actions)
                else -> PreviewStep(state, actions)
            }
            if (state.step < 4) {
                Button(
                    onClick = actions.next,
                    enabled = state.step != 3 || state.draft.title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.host_next)) }
            }
        }
    }
    if (skipping) {
        AlertDialog(
            onDismissRequest = { skipping = false },
            title = { Text(stringResource(R.string.host_skip_title)) },
            text = { Text(stringResource(R.string.host_skip_body)) },
            confirmButton = {
                TextButton(onClick = { skipping = false; actions.skip() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.host_skip_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { skipping = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (state.failed) {
        AlertDialog(
            onDismissRequest = actions.failureShown,
            title = { Text(stringResource(R.string.host_failed_title)) },
            text = { Text(stringResource(R.string.host_failed_body)) },
            confirmButton = {
                TextButton(onClick = actions.failureShown, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun StepTitle(text: String, help: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val kindLabels = mapOf(
    HostedKind.Photo to R.string.host_kind_photo,
    HostedKind.Outing to R.string.host_kind_outing,
    HostedKind.Cooking to R.string.host_kind_cooking,
    HostedKind.Kindness to R.string.host_kind_kindness,
    HostedKind.Creative to R.string.host_kind_creative,
)

@Composable
private fun KindStep(draft: HostDraft, actions: HostGuideActions) {
    StepTitle(stringResource(R.string.host_kind_title), stringResource(R.string.host_kind_help))
    Column(Modifier.selectableGroup()) {
        HostedKind.entries.forEach { kind ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = draft.kind == kind, onClick = { actions.chooseKind(kind) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = draft.kind == kind, onClick = null)
                Text(
                    "${HostGuide.start(kind, draft.target).emoji}  ${stringResource(kindLabels.getValue(kind))}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TargetStep(draft: HostDraft, actions: HostGuideActions) {
    StepTitle(stringResource(R.string.host_target_title), stringResource(R.string.host_target_help))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { actions.setTarget(draft.target - 1) },
            enabled = draft.target > HostedChallenge.TARGET_MIN,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.host_target_less)) }
        Text(
            draft.target.toString(),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp),
        )
        IconButton(
            onClick = { actions.setTarget(draft.target + 1) },
            enabled = draft.target < HostedChallenge.TARGET_MAX,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.host_target_more)) }
    }
}

private val illustrationIcons: Map<HostedIllustration, Pair<ImageVector, Int>> = mapOf(
    HostedIllustration.Sun to (Icons.Outlined.WbSunny to R.string.host_illustration_sun),
    HostedIllustration.Heart to (Icons.Outlined.FavoriteBorder to R.string.host_illustration_heart),
    HostedIllustration.Leaf to (Icons.Outlined.Eco to R.string.host_illustration_leaf),
    HostedIllustration.Star to (Icons.Outlined.StarBorder to R.string.host_illustration_star),
    HostedIllustration.Camera to (Icons.Outlined.PhotoCamera to R.string.host_illustration_camera),
    HostedIllustration.Pot to (Icons.Outlined.Restaurant to R.string.host_illustration_pot),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookStep(draft: HostDraft, actions: HostGuideActions) {
    StepTitle(stringResource(R.string.host_look_title), stringResource(R.string.host_look_help))
    OutlinedTextField(
        value = draft.title,
        onValueChange = actions.setTitle,
        label = { Text(stringResource(R.string.host_look_name)) },
        supportingText = { Text("${draft.title.length} / ${HostedChallenge.TITLE_MAX}") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.host_look_emoji), style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HostGuide.emojis.forEach { emoji ->
            Choice(selected = draft.emoji == emoji, label = emoji, onClick = { actions.setEmoji(emoji) }) {
                Text(emoji, fontSize = 24.sp)
            }
        }
    }
    Text(stringResource(R.string.host_look_illustration), style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        HostedIllustration.entries.forEach { illustration ->
            val (icon, label) = illustrationIcons.getValue(illustration)
            val name = stringResource(label)
            Choice(selected = draft.illustration == illustration, label = name, onClick = { actions.setIllustration(illustration) }) {
                Icon(icon, contentDescription = null)
            }
        }
    }
}

/** A round choice of 56 dp, ringed when selected. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(56.dp)
            .background(if (selected) colors.secondaryContainer else colors.surfaceVariant, CircleShape)
            .then(if (selected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PreviewStep(state: HostGuideState, actions: HostGuideActions) {
    val draft = state.draft
    StepTitle(stringResource(R.string.host_preview_title), stringResource(R.string.host_preview_help))
    // The challenge as the family will see it: the point of this step (DESIGN-11).
    ClayCard(Modifier.fillMaxWidth(), emphasis = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(illustrationIcons.getValue(draft.illustration).first, contentDescription = null)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tree_challenge, draft.emoji, draft.title.trim()), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.tree_challenge_progress, 0, draft.target),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(stringResource(R.string.host_preview_how), style = MaterialTheme.typography.bodyMedium)
    }
    if (state.canPublish) {
        Button(onClick = actions.publish, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.host_publish))
        }
        OutlinedButton(onClick = actions.saveDraft, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.host_save_draft))
        }
    } else {
        Button(onClick = actions.saveDraft, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.host_save_for_adults))
        }
        Text(stringResource(R.string.host_child_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
