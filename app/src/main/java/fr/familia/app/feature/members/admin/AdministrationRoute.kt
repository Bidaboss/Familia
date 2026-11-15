package fr.familia.app.feature.members.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the Administration menu to its ViewModel. */
@Composable
fun AdministrationRoute(container: AppContainer?, currentMember: Member, onOpenExport: () -> Unit, onBack: () -> Unit) {
    val viewModel: AdministrationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AdministrationViewModel(container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed by viewModel.actionFailed.collectAsStateWithLifecycle()
    AdministrationScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSetChef = viewModel::setChef,
        onAddHousehold = viewModel::addHousehold,
        onDeleteHousehold = viewModel::deleteHousehold,
        onOpenExport = onOpenExport,
        actionFailed = failed,
        onFailureShown = viewModel::failureShown,
    )
}
