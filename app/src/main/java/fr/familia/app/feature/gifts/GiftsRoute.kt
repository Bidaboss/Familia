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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Connects the Cadeaux tab to its ViewModel. */
@Composable
fun GiftsRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenList: (listId: String) -> Unit,
    /** « Mes envies » live on the member's page (retour d'essai, 17/11); null keeps them in the tab. */
    onOpenMyPage: (() -> Unit)? = null,
    /** « Préparer une surprise » (récit 63): the choice of the person. */
    onOpenSurprisePicker: () -> Unit = {},
    /** « Ajouter une envie » on the new tab (PRODUIT-119). */
    onAddWish: (listId: String) -> Unit = onOpenList,
    /** A surprise I prepare (brief Cadeaux et Surprise, §2). */
    onOpenSurprise: (String) -> Unit = {},
) {
    // For the whole family only (récit 63): off, the admin principal keeps the tab as it was.
    val surpriseButton = LocalFeatureFlags.current.isOnForFamily(Feature.SurpriseButton)
    val viewedAs = LocalViewAs.current.viewed
    val context = LocalContext.current
    val visits = remember { GiftSeenPreferences(context.applicationContext) }
    val viewModel: GiftsViewModel = viewModel(
        // A new ViewModel when a switch moves: the lists shown change with it.
        key = "gifts-$surpriseButton",
        factory = viewModelFactory {
            initializer {
                GiftsViewModel(
                    container?.giftRepository, container?.memberRepository, currentMember.id,
                    seen = visits.seen,
                    surpriseButton = surpriseButton,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // « Voir comme » never moves the « nouvelles » of the admin's own phone.
    val countsVisits = viewedAs == null
    val shown = state as? GiftsUiState.Success
    val wishesIds = shown?.sections?.filter { !it.isMe }?.mapNotNull { it.wishes?.id }.orEmpty()
    LaunchedEffect(wishesIds, countsVisits) {
        if (countsVisits && wishesIds.isNotEmpty()) visits.markFirstSeen(wishesIds)
    }
    // The surprises I prepare, one tile each; nothing while they cannot be read.
    val surpriseRepo = container?.surpriseRepository
    val surprises by remember(surpriseRepo, currentMember.id) {
        surpriseRepo?.observeMine(currentMember.id)?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val family = (state as? GiftsUiState.Success)?.sections?.map { it.member }.orEmpty()
    val tiles = surprises.mapNotNull { s ->
        family.firstOrNull { it.id == s.beneficiaryId }?.let { SurpriseTile(s.id, it.firstName, s.date) }
    }
    GiftsScreen(
        state = state,
        onOpenList = { listId ->
            if (countsVisits) visits.markSeen(listId)
            onOpenList(listId)
        },
        onRetry = viewModel::retry,
        // « Voir comme »: some lists may be kept from the admin.
        someListsHidden = viewedAs != null,
        onOpenMyPage = onOpenMyPage,
        onOpenSurprisePicker = onOpenSurprisePicker,
        onAddWish = onAddWish,
        surprises = tiles,
        onOpenSurprise = onOpenSurprise,
    )
}
