package fr.familia.app.feature.capsules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.serialization.Serializable

/*
 * La capsule temporelle (PRD, récit 39): a message sealed until a day, for chosen members. The
 * rules hide a sealed capsule from everyone but its author (firestore.rules, « Capsule
 * temporelle »), and the server opens it. Hidden behind Feature.Capsules.
 */

@Serializable
data object CapsulesDestination

@Composable
fun CapsulesRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit, onAdd: (() -> Unit)? = null) {
    val viewModel: CapsulesViewModel = viewModel(
        factory = viewModelFactory { initializer { CapsulesViewModel(container?.capsuleRepository, container?.memberRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CapsulesScreen(state, onBack, onAdd = onAdd)
}
