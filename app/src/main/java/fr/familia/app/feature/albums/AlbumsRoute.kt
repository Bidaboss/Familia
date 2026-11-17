package fr.familia.app.feature.albums

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
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** « Albums », from Tout Familia (R1 bis). */
@Serializable
data object AlbumsDestination

/** Connects « Albums » to the trips, the events and their photos. */
@Composable
fun AlbumsRoute(
    container: AppContainer?,
    currentMember: Member,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumEntry) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenTrips: (() -> Unit)? = null,
) {
    val flags = LocalFeatureFlags.current
    val tripsOn = flags.isOn(Feature.Trips)
    val eventsOn = flags.isOn(Feature.EventAlbums)
    val today = LocalDate.now()
    val viewModel: AlbumsViewModel = viewModel(
        key = "albums-$today-$tripsOn-$eventsOn",
        factory = viewModelFactory {
            initializer {
                AlbumsViewModel(
                    container?.tripRepository,
                    container?.eventRepository,
                    container?.albumPhotoRepository,
                    container?.memberRepository,
                    currentMember.id,
                    today,
                    tripsOn,
                    eventsOn,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumsScreen(state, onBack, onOpenAlbum, onOpenMonth, onOpenTrips)
}
