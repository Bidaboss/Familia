package fr.familia.app.feature.firststeps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.members.photo.MemberPhotoEditor

/**
 * « Mes premiers pas » on the Accueil (récit 61), behind Feature.FirstSteps switched on for the family:
 * the card to place, or null when there is nothing to show, in « Voir comme », or during the Mode présentation. [onOpenTastes] opens
 * my profile, where « Goûts et passions » is filled; [onOpenGoodNews] the feed of the good news.
 */
@Composable
fun rememberFirstSteps(
    container: AppContainer?,
    currentMember: Member,
    onOpenTastes: () -> Unit,
    onOpenGoodNews: () -> Unit,
): (@Composable () -> Unit)? {
    val flags = LocalFeatureFlags.current
    // Switched on for the family, the admin principal included: an exception to the rule where the
    // admin sees every module first, so nothing new shows on their Accueil while it is off (PRD rév. 261).
    if (!flags.isOnForFamily(Feature.FirstSteps) || LocalViewAs.current.viewed != null) return null
    val context = LocalContext.current.applicationContext
    val tastesOn = flags.isOn(Feature.MemberCard)
    val viewModel: FirstStepsViewModel = viewModel(
        key = "firstSteps-${currentMember.id}-$tastesOn",
        factory = viewModelFactory {
            initializer {
                FirstStepsViewModel(
                    container?.memberPhotoRepository, container?.memberCardRepository, container?.goodNewsRepository,
                    container?.appConfigRepository, FirstStepsPreferences(context), currentMember.id, tastesOn,
                )
            }
        },
    )
    val card by viewModel.card.collectAsStateWithLifecycle()
    val photoFailed by viewModel.photoFailed.collectAsStateWithLifecycle()
    var framing by rememberSaveable { mutableStateOf(false) }
    if (framing) {
        MemberPhotoEditor(
            member = currentMember,
            hasPhoto = false,
            onSave = { jpeg -> framing = false; viewModel.savePhoto(jpeg) },
            onRemove = { framing = false },
            onDismiss = { framing = false },
        )
    }
    if (photoFailed) {
        AlertDialog(
            onDismissRequest = viewModel::photoFailureShown,
            text = { Text(stringResource(R.string.first_steps_photo_failed)) },
            confirmButton = { TextButton(onClick = viewModel::photoFailureShown) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    // Null when there is nothing to show, so the Accueil keeps no empty block.
    val shown = card ?: return null
    return {
        FirstStepsCardContent(
            card = shown,
            onPhoto = { framing = true },
            onTastes = onOpenTastes,
            onReaction = onOpenGoodNews,
            onLater = viewModel::later,
            onDismiss = viewModel::dismiss,
        )
    }
}

/** The card itself, stateless: the gestures with their tick, or « Bravo, c'est parti ! ». No counter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstStepsCardContent(
    card: FirstStepsCard,
    onPhoto: () -> Unit,
    onTastes: () -> Unit,
    onReaction: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = ClayTint.Family
    val muted = FamiliaClay.colors.onTintVariant(tint)
    ClayCard(modifier.fillMaxWidth(), tint = tint) {
        when (card) {
            FirstStepsCard.Bravo -> {
                ClayCardTitle(stringResource(R.string.first_steps_bravo_title))
                Text(stringResource(R.string.first_steps_bravo_body), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.first_steps_close)) }
            }
            is FirstStepsCard.Steps -> {
                ClayCardTitle(stringResource(R.string.first_steps_title))
                Text(stringResource(R.string.first_steps_body), style = MaterialTheme.typography.bodyMedium, color = muted)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.lines.forEach { line ->
                        StepLine(
                            line,
                            when (line.step) {
                                FirstStep.Photo -> onPhoto
                                FirstStep.Tastes -> onTastes
                                FirstStep.Reaction -> onReaction
                            },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.first_steps_later)) }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.first_steps_never)) }
                }
            }
        }
    }
}

@Composable
private fun StepLine(line: FirstStepLine, onOpen: () -> Unit) {
    val label = stringResource(
        when (line.step) {
            FirstStep.Photo -> R.string.first_steps_photo
            FirstStep.Tastes -> R.string.first_steps_tastes
            FirstStep.Reaction -> R.string.first_steps_reaction
        },
    )
    val state = stringResource(if (line.done) R.string.first_steps_done else R.string.first_steps_to_do)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (line.done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null, // The state description says it.
            tint = if (line.done) MaterialTheme.colorScheme.primary else FamiliaClay.colors.onTintVariant(ClayTint.Family),
            modifier = Modifier.size(24.dp),
        )
        if (line.done) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        } else {
            ClayLink(label, onOpen, Modifier.weight(1f))
        }
    }
}

@FamiliaPreviews
@Composable
private fun FirstStepsCardPreview() {
    FamiliaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FirstStepsCardContent(
                FirstStepsCard.Steps(
                    listOf(
                        FirstStepLine(FirstStep.Photo, done = true),
                        FirstStepLine(FirstStep.Tastes, done = false),
                        FirstStepLine(FirstStep.Reaction, done = false),
                    ),
                ),
                {}, {}, {}, {}, {},
            )
            FirstStepsCardContent(FirstStepsCard.Bravo, {}, {}, {}, {}, {})
        }
    }
}
