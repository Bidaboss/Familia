package fr.familia.app.feature.genealogy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.serialization.Serializable

/** L'arbre généalogique (PRD, récit 40), behind Feature.Genealogy. */
@Serializable
data object GenealogyDestination

@Composable
fun GenealogyRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit, onOpenProfile: ((String) -> Unit)? = null) {
    val viewModel: GenealogyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { GenealogyViewModel(container?.genealogyRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GenealogyScreen(state, onBack, onOpenProfile = onOpenProfile)
}
