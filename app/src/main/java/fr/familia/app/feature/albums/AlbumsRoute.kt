package fr.familia.app.feature.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import kotlinx.serialization.Serializable
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
    /** « Photos du fil » of a month (récit 71). */
    onOpenFeedPhotos: (YearMonth) -> Unit = {},
) {
    val flags = LocalFeatureFlags.current
    val tripsOn = flags.isOn(Feature.Trips)
    val eventsOn = flags.isOn(Feature.EventAlbums)
    // Se souvenir en un seul lieu (récit 71): only with the fil on; off, Albums stays as it was.
    val memoriesInFeed = flags.isOnForFamily(Feature.NewsFeed)
    val feedPhotosOn = memoriesInFeed && flags.isOn(Feature.GoodNews)
    val today = familyToday()
    val viewModel: AlbumsViewModel = viewModel(
        key = "albums-$today-$tripsOn-$eventsOn-$memoriesInFeed-$feedPhotosOn",
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
                    goodNews = container?.goodNewsRepository?.takeIf { feedPhotosOn },
                    memoriesInFeed = memoriesInFeed,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (memoriesInFeed) {
        AlbumsByMonthScreen(state, today, onBack, onOpenAlbum, onOpenMonth, onOpenFeedPhotos, onOpenTrips)
    } else {
        AlbumsScreen(state, onBack, onOpenAlbum, onOpenMonth, onOpenTrips)
    }
}
