package fr.familia.app.feature.capsules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Serializable
data class CapsuleDetailDestination(val capsuleId: String)

@Composable
fun CapsulesRoute(container: AppContainer?, currentMember: Member, onOpen: (String) -> Unit, onBack: () -> Unit, onAdd: (() -> Unit)? = null) {
    val viewModel: CapsulesViewModel = viewModel(
        factory = viewModelFactory { initializer { CapsulesViewModel(container?.capsuleRepository, container?.memberRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CapsulesScreen(state, onBack, onOpen = onOpen, onAdd = onAdd)
}

/** A removed capsule closes its page. */
@Composable
fun CapsuleDetailRoute(container: AppContainer?, currentMember: Member, capsuleId: String, onBack: () -> Unit) {
    val viewModel: CapsuleDetailViewModel = viewModel(
        key = "capsule-$capsuleId",
        factory = viewModelFactory {
            initializer { CapsuleDetailViewModel(container?.capsuleRepository, container?.memberRepository, currentMember.id, capsuleId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == CapsuleDetailUiState.Removed) onBack() }
    CapsuleDetailScreen(state, viewModel::remove, onBack)
}
