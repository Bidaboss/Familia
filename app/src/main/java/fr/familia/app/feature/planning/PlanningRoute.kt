package fr.familia.app.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the Planning tab to its ViewModel. */
@Composable
fun PlanningRoute(container: AppContainer?, currentMember: Member, onAddEvent: () -> Unit, onOpenEvent: (eventId: String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { PlanningScopePreferences(context) }
    val viewModel: PlanningViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlanningViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    currentMember.id,
                    rsvps = container?.rsvpRepository,
                    initialScope = prefs.scope,
                    saveScope = { prefs.scope = it },
                ) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlanningScreen(
        state = state,
        onRetry = viewModel::retry,
        onAddEvent = onAddEvent,
        onOpenEvent = onOpenEvent,
        onScopeChange = viewModel::setScope,
    )
}
