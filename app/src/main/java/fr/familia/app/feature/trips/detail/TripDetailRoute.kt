package fr.familia.app.feature.trips.detail

import fr.familia.app.feature.trips.list.TripPart

import fr.familia.app.core.share.tripShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.Feature
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.trips.packing.packingCount
import fr.familia.app.feature.trips.packing.rememberPackingViewModel
import fr.familia.app.feature.trips.photos.rememberTripAlbum
import fr.familia.app.feature.trips.phrases.PhraseBook
import fr.familia.app.feature.trips.phrases.rememberAbroadCountries
import fr.familia.app.feature.trips.safety.EmergencyNumbers

/** Connects the trip view to its ViewModel. */
@Composable
fun TripDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onEditTrip: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onSeePhotos: () -> Unit,
    onBack: () -> Unit,
    /** « Comptes du voyage » (récit 16 bis): null while the switch is off or for a child. */
    onOpenExpenses: (() -> Unit)? = null,
    /** The part to show first, from the list (récit 80); null: the top. */
    openAt: TripPart? = null,
    /** « La carte du voyage » (récit 87). */
    onOpenMap: (() -> Unit)? = null,
    /** « Le mot de l'étape » (récit 88). */
    onOpenWord: ((stepId: String) -> Unit)? = null,
    /** « Valise » (récit 98): the screen of the bags, opened from the round button. */
    onOpenPacking: (() -> Unit)? = null,
    /** « Phrases utiles » (récit 105): opened from the round button when a step is abroad. */
    onOpenPhrases: (() -> Unit)? = null,
    /** « Sécurité » (récit 105): opened from the round button when a step is in a country of the table, never France. */
    onOpenSafety: (() -> Unit)? = null,
    /** « Monnaie » (récit 105): for those who go, once a step is abroad or the trip has a currency. */
    onOpenMoney: (() -> Unit)? = null,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // « À emporter » (récit 62): « Je m'en charge » and « Ma valise » only while packingList is on.
    val packingOn = LocalFeatureFlags.current.isOn(Feature.PackingList)
    val shownChecklist = remember(viewModel, packingOn) { viewModel.checklistActions(currentMember.id, packingOn) }
    // The number on the round « Valise »: what is left in my bag, and the lines nobody has taken.
    val bags by rememberPackingViewModel(container, currentMember, tripId).uiState.collectAsStateWithLifecycle()
    val packingCount = (state as? TripDetailUiState.Success)?.let { success -> packingCount(success.items, bags, packingOn) } ?: 0
    val album = rememberTripAlbum(container, currentMember, tripId)
    val photos by album.uiState.collectAsStateWithLifecycle()
    val photoActions = rememberPhotoActions(album, onSeePhotos)
    val context = LocalContext.current
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val shown = state as? TripDetailUiState.Success
    val abroad = rememberAbroadCountries(container, shown?.steps.orEmpty())
    TripDetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onEditTrip = onEditTrip,
        onAddStep = onAddStep,
        onEditStep = onEditStep,
        onArrived = viewModel::markArrived,
        onClearArrival = viewModel::clearArrival,
        onBack = onBack,
        checklist = shownChecklist,
        onOpenPacking = onOpenPacking,
        onOpenPhrases = onOpenPhrases?.takeIf { PhraseBook.abroad(abroad).isNotEmpty() },
        onOpenSafety = onOpenSafety?.takeIf { EmergencyNumbers.abroad(abroad).isNotEmpty() },
        onOpenMoney = onOpenMoney?.takeIf { shown?.isParticipant == true && (abroad.isNotEmpty() || shown.trip.currency != null) },
        packingCount = packingCount,
        photos = photos,
        photoActions = photoActions,
        onOpenExpenses = onOpenExpenses,
        illustrated = true,
        satelliteToken = fr.familia.app.BuildConfig.MAPBOX_PUBLIC_TOKEN,
        openAt = openAt,
        onOpenMap = onOpenMap,
        onOpenWord = onOpenWord,
        onSeePhotos = onSeePhotos,
        onShare = shown?.takeIf { shareOn && it.canShare }?.let { success -> { share(context, tripShare(context, success.trip, success.today)) } },
    )
}

/** The actions of « À prévoir »: « Je m'en charge » and « Je ne peux plus » only while packingList is on. */
fun TripDetailViewModel.checklistActions(myId: String, packingOn: Boolean): ChecklistActions =
    if (packingOn) {
        ChecklistActions(this::addItem, this::editItem, this::setItemDone, this::deleteItem, this::takeItem, this::releaseItem, myId)
    } else {
        ChecklistActions(this::addItem, this::editItem, this::setItemDone, this::deleteItem)
    }
