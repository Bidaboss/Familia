package fr.familia.app.feature.members.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects the Administration menu to its ViewModel. */
@Composable
fun AdministrationRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenExport: () -> Unit,
    onBack: () -> Unit,
    onOpenUsage: (() -> Unit)? = null,
) {
    val viewModel: AdministrationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AdministrationViewModel(
                    container?.memberRepository,
                    currentMember.id,
                    container?.appConfigRepository,
                    container?.consumptionRepository,
                    container?.treeRepository,
                    container?.capsuleRepository,
                ) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed by viewModel.actionFailed.collectAsStateWithLifecycle()
    val features by viewModel.features.collectAsStateWithLifecycle()
    val consumption by viewModel.consumption.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val leftovers by viewModel.capsuleLeftovers.collectAsStateWithLifecycle()
    // The card only shows when the tree is on for the admin principal (always, while in test).
    val treeOn = LocalFeatureFlags.current.isOn(Feature.Tree)
    AdministrationScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSetChef = viewModel::setChef,
        onAddHousehold = viewModel::addHousehold,
        onDeleteHousehold = viewModel::deleteHousehold,
        onOpenExport = onOpenExport,
        onOpenUsage = onOpenUsage,
        actionFailed = failed,
        onFailureShown = viewModel::failureShown,
        features = features,
        onSetFeature = viewModel::setFeature,
        onRenameHousehold = viewModel::renameHousehold,
        consumption = consumption,
        challenges = if (treeOn) challenges else emptyList(),
        onPickChallenge = if (treeOn) viewModel::pickChallenge else null,
        capsuleLeftovers = leftovers,
        onDeleteCapsuleLeftovers = viewModel::deleteCapsuleLeftovers,
    )
}
