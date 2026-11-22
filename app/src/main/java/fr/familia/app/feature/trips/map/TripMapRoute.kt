package fr.familia.app.feature.trips.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.BuildConfig
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.trips.detail.TripDetailViewModel

/** « La carte du voyage »: the same trip and steps as its page, read with the same rules. */
@Composable
fun TripMapRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onOpenStep: (stepId: String) -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onLaunch: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-map-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The words read with the rules of the trip (récit 88): « Le trajet » says which step has one.
    val words by remember(container, tripId) { container?.tripWordRepository?.observeWords(tripId) ?: flowOf(emptyMap()) }
        .collectAsStateWithLifecycle(emptyMap())
    // The streets when this APK holds the token; else the drawn map (the image falls back by itself without network).
    val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
    val ground = mapGround(streetsOn = true, token, hasNetwork = true)
    TripMapScreen(state, onOpenStep, onEditStep, onBack, streetsToken = token.takeIf { ground == MapGround.Streets }, words = words, onLaunch = onLaunch.takeIf { token.isNotBlank() })
}
