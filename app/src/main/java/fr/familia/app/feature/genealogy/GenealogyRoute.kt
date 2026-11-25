package fr.familia.app.feature.genealogy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import kotlinx.serialization.Serializable

/** L'arbre généalogique (PRD, récit 40), behind Feature.Genealogy. */
@Serializable
data object GenealogyDestination

@Composable
fun GenealogyRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    onOpenProfile: ((String) -> Unit)? = null,
    onMakePoster: (() -> Unit)? = null,
) {
    val viewModel = genealogyViewModel(container, currentMember)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed by viewModel.actionFailed.collectAsStateWithLifecycle()
    val actions = rememberGenealogyActions(viewModel)
    GenealogyScreen(
        state,
        onBack,
        onOpenProfile = onOpenProfile,
        actions = actions,
        actionFailed = failed,
        onFailureShown = viewModel::failureShown,
        // Not in « Voir comme »: the poster would be made with someone else's rights (récit 53).
        onMakePoster = onMakePoster?.takeIf { LocalFeatureFlags.current.isOn(Feature.TreePoster) && LocalViewAs.current.viewed == null },
        treeOfLife = LocalFeatureFlags.current.isOn(Feature.ArbreDeVie),
    )
}

/**
 * The « Arbre » tab of Famille (brief Famille-recettes, règle 1): the same tree as the page, without
 * its title bar, its « Liste » nor its way back.
 */
@Composable
fun GenealogyTab(
    container: AppContainer?,
    currentMember: Member,
    onOpenProfile: ((String) -> Unit)? = null,
    onMakePoster: (() -> Unit)? = null,
) {
    val viewModel = genealogyViewModel(container, currentMember)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed by viewModel.actionFailed.collectAsStateWithLifecycle()
    GenealogyContent(
        state = state,
        onOpenProfile = onOpenProfile,
        actions = rememberGenealogyActions(viewModel),
        // The tree left Tout Familia: « Faire l'affiche » comes along (récit 53).
        onMakePoster = onMakePoster?.takeIf { LocalFeatureFlags.current.isOn(Feature.TreePoster) && LocalViewAs.current.viewed == null },
        onBack = null,
        treeOfLife = LocalFeatureFlags.current.isOn(Feature.ArbreDeVie),
        inFamilyTab = true,
    )
    GenealogyFailure(failed, viewModel::failureShown)
}

@Composable
private fun genealogyViewModel(container: AppContainer?, currentMember: Member): GenealogyViewModel = viewModel(
    factory = viewModelFactory {
        initializer { GenealogyViewModel(container?.genealogyRepository, container?.memberRepository, currentMember.id) }
    },
)

@Composable
private fun rememberGenealogyActions(viewModel: GenealogyViewModel): GenealogyActions = remember(viewModel) {
    GenealogyActions(
        onAddLink = viewModel::addLink,
        onRemoveLink = viewModel::removeLink,
        onSaveKin = viewModel::saveKin,
        onDeleteKin = viewModel::deleteKin,
        onSetWord = viewModel::setWord,
    )
}
