package fr.familia.app.feature.trips.detail

import fr.familia.app.feature.trips.list.TripPart

import fr.familia.app.core.share.tripShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.Feature
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.familia.app.core.photo.keepPhotoAccess
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripPlaceKind
import java.time.ZoneId
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import fr.familia.app.R
import fr.familia.app.feature.trips.voice.VoiceNoteDialog
import fr.familia.app.feature.trips.voice.rememberVoicePlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import fr.familia.app.feature.trips.packing.packingCount
import fr.familia.app.feature.trips.packing.rememberPackingViewModel
import fr.familia.app.feature.trips.photos.rememberTripAlbum

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
    /** « Documents » (récit 105, règle 41): for those who go, never the followers. */
    onOpenDocuments: (() -> Unit)? = null,
    /** « Enrichir avec une IA » (récit 106): for those who go. */
    onOpenEnrich: (() -> Unit)? = null,
    /** The page of a place programmed under a destination (récit 103, règle 19). */
    onOpenPlace: (placeId: String) -> Unit = {},
    /** A new accommodation, restaurant or spot under the destination [stepId] (règle 20). */
    onAddPlace: (stepId: String, kind: TripPlaceKind) -> Unit = { _, _ -> },
    /** A new destination or transport from a « + » of « Le trajet », starting on [day] (DESIGN-99 A). */
    onInsertStep: (kind: fr.familia.app.feature.trips.step.StepAddKind, day: java.time.LocalDate) -> Unit = { _, _ -> onAddStep() },
    /** The album of the trip opened on one photo (récit 104). */
    onOpenPhoto: (photoId: String) -> Unit = { onSeePhotos() },
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
    val context = LocalContext.current
    val photos by album.uiState.collectAsStateWithLifecycle()
    val photoActions = rememberPhotoActions(album, onSeePhotos)
    // « Ajouter des photos » under a destination (récit 104, partie 2): the photos chosen are filed under it.
    var photoStep by rememberSaveable { mutableStateOf<String?>(null) }
    val stepPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, photos.placesLeft))) { uris ->
        keepPhotoAccess(context, uris)
        album.addPhotos(uris.map { it.toString() }, photoStep)
        photoStep = null
    }
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val shown = state as? TripDetailUiState.Success
    // The photos by destination, from the day they were taken (récit 104, partie 1): nothing is written.
    val byStep = remember(shown?.trip, shown?.steps, photos.photos) {
        shown?.let { stepPhotos(tripTimeline(it.trip, it.steps), photos.photos, ZoneId.systemDefault()) } ?: StepPhotos(emptyMap(), emptyList())
    }
    // The places programmed under the destinations (règle 18); none when they cannot be read.
    val places by remember(container, tripId) {
        container?.tripPlaceRepository?.observePlaces(tripId)
            ?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }
            ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val addPhoto: ((String) -> Unit)? = if (photos.canAdd && photos.placesLeft > 0) {
        { stepId ->
            photoStep = stepId
            stepPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    } else {
        null
    }
    // « Ajout rapide » (récit 104, règle 35): during the trip, under the destination of the day.
    val stay = remember(shown?.trip, shown?.steps, shown?.today) {
        shown?.takeIf { it.isParticipant }?.let { currentStay(tripTimeline(it.trip, it.steps), it.today) }
    }
    // Notes vocales (récit 104, partie 3b): under each destination; recorded from « Ajout rapide ».
    val voiceRepo = container?.tripVoiceNoteRepository
    val voiceNotes by remember(voiceRepo, tripId) {
        voiceRepo?.observe(tripId)
            ?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }
            ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val voicePlayer = rememberVoicePlayer()
    val scope = rememberCoroutineScope()
    var voiceStep by rememberSaveable { mutableStateOf<String?>(null) }
    val quickAdd = stay?.let { today ->
        val id = today.step.id
        QuickAdd(
            destination = placeName(today.step),
            onPhoto = addPhoto?.let { add -> { add(id) } },
            onWord = onOpenWord?.let { word -> { word(id) } },
            onVoice = voiceRepo?.let { { voiceStep = id } },
        ).takeIf { it.onPhoto != null || it.onWord != null || it.onVoice != null }
    }
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
        onOpenDocuments = onOpenDocuments?.takeIf { shown?.isParticipant == true && container?.tripDocumentRepository != null },
        onOpenEnrich = onOpenEnrich?.takeIf { shown?.isParticipant == true && container?.tripPlaceRepository != null },
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
        // « Le trajet »: those who go change the nights (règle 16), the others read them.
        timeline = TimelineActions(
            onOpen = onEditStep,
            onNights = if (shown?.isParticipant == true) viewModel::changeNights else null,
            onUndo = viewModel::restoreSteps,
            places = remember(places) { placesByStep(places) },
            onOpenPlace = onOpenPlace,
            onAddPlace = if (shown?.isParticipant == true) onAddPlace else null,
            onInsertStep = if (shown?.isParticipant == true) onInsertStep else null,
            onEnrich = onOpenEnrich?.takeIf { shown?.isParticipant == true && container?.tripPlaceRepository != null },
            photos = byStep.byStay,
            onOpenPhoto = onOpenPhoto,
            withoutStep = byStep.withoutStep.size,
            onSeePhotos = onSeePhotos,
            onAddPhoto = addPhoto,
            voiceNotes = remember(voiceNotes) { voiceNotes.groupBy { it.stepId } },
            playingVoiceId = voicePlayer.playingId,
            onPlayVoice = { note ->
                if (voicePlayer.playingId == note.id) {
                    voicePlayer.stop()
                } else {
                    scope.launch { voiceRepo?.playUrl(tripId, note)?.let { url -> voicePlayer.play(note, url) } }
                }
            },
            canDeleteVoice = { note -> note.authorId == currentMember.id || shown?.canManage == true },
            onDeleteVoice = { note ->
                if (voicePlayer.playingId == note.id) voicePlayer.stop()
                scope.launch { runCatching { voiceRepo?.delete(tripId, note) } }
            },
        ),
        quickAdd = quickAdd,
        cover = { success ->
            TripCoverCarousel(success.trip, success.steps, container?.placePhotos)
        },
        onShare = shown?.takeIf { shareOn && it.canShare }?.let { success -> { share(context, tripShare(context, success.trip, success.today)) } },
    )
    val recordAt = voiceStep
    val recordStay = stay?.takeIf { it.step.id == recordAt }
    if (recordAt != null && voiceRepo != null) {
        VoiceNoteDialog(
            destination = recordStay?.let { placeName(it.step) } ?: "",
            onSend = { file, seconds ->
                voiceStep = null
                scope.launch {
                    try {
                        voiceRepo.add(tripId, currentMember, recordAt, file, seconds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.voice_send_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        file.delete()
                    }
                }
            },
            onDismiss = { voiceStep = null },
        )
    }
}

/** The actions of « À prévoir »: « Je m'en charge » and « Je ne peux plus » only while packingList is on. */
fun TripDetailViewModel.checklistActions(myId: String, packingOn: Boolean): ChecklistActions =
    if (packingOn) {
        ChecklistActions(this::addItem, this::editItem, this::setItemDone, this::deleteItem, this::takeItem, this::releaseItem, myId)
    } else {
        ChecklistActions(this::addItem, this::editItem, this::setItemDone, this::deleteItem)
    }
