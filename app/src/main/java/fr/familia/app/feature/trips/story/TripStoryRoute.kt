package fr.familia.app.feature.trips.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.Member
import java.time.LocalDate

/** « Le voyage jour par jour » of [tripId], opened on [day] (an ISO date) when it has a word. */
@Composable
fun TripStoryRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    day: String?,
    onOpenWord: (stepId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TripStoryViewModel = viewModel(
        key = "trip-story-$tripId",
        factory = viewModelFactory {
            initializer {
                TripStoryViewModel(
                    container?.tripRepository,
                    container?.tripWordRepository,
                    container?.albumPhotoRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    day?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    LocalDate.now(FAMILY_ZONE),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenWord, onBack) {
        TripStoryActions(
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            onNextDay = viewModel::nextDay,
            onReplay = viewModel::replay,
            onHeart = viewModel::toggleHeart,
            onOpenWord = onOpenWord,
            onRetry = viewModel::retry,
            onClose = onBack,
        )
    }
    TripStoryScreen(state, actions)
}
