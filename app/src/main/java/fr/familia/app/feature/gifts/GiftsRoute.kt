package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import fr.familia.app.core.viewas.LocalViewAs
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Connects the Cadeaux tab to its ViewModel. */
@Composable
fun GiftsRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenList: (listId: String) -> Unit,
    onNewList: () -> Unit,
    onAllBirthdays: () -> Unit,
    onPrepareSurprise: (memberId: String) -> Unit,
    /** Null while the notebook module is off for this member. */
    onOpenNotebook: (() -> Unit)? = null,
) {
    val singleList = LocalFeatureFlags.current.isOn(Feature.SingleList)
    val presentation = LocalFeatureFlags.current.isOn(Feature.PresentationR1bis)
    val viewedAs = LocalViewAs.current.viewed
    val context = LocalContext.current
    val visits = remember { GiftSeenPreferences(context.applicationContext) }
    val viewModel: GiftsViewModel = viewModel(
        // A new ViewModel when a switch moves: the lists shown change with it.
        key = "gifts-$singleList-$presentation",
        factory = viewModelFactory {
            initializer {
                GiftsViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id,
                    singleList = singleList, presentation = presentation, seen = visits.seen,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // « Voir comme » never moves the « nouvelles » of the admin's own phone.
    val countsVisits = viewedAs == null && presentation
    val shown = state as? GiftsUiState.Success
    val wishesIds = shown?.sections?.filter { !it.isMe }?.mapNotNull { it.wishes?.id }.orEmpty()
    LaunchedEffect(wishesIds, countsVisits) {
        if (countsVisits && wishesIds.isNotEmpty()) visits.markFirstSeen(wishesIds)
    }
    GiftsScreen(
        state = state,
        onOpenList = { listId ->
            if (countsVisits) visits.markSeen(listId)
            onOpenList(listId)
        },
        onNewList = onNewList,
        onRetry = viewModel::retry,
        onAllBirthdays = onAllBirthdays,
        onPrepareSurprise = onPrepareSurprise,
        // « Voir comme »: the notebook is never shown (PRD), and some lists may be kept from the admin.
        onOpenNotebook = onOpenNotebook?.takeIf { viewedAs == null },
        someListsHidden = viewedAs != null,
        onSelectMoment = viewModel::selectMoment,
    )
}
