package fr.familia.app.feature.announcements

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Announcement
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.announcementCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** « Envoyer une notification » (récit 69), opened from Administration by the admin principal. */
@Serializable
data object AnnouncementsDestination

/** One message, opened from its notification or from « Déjà envoyées ». */
@Serializable
data class AnnouncementDestination(val announcementId: String)

@Composable
fun AnnouncementsRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repository = container?.announcementRepository
    val families = remember(container) { container?.memberRepository?.observeFamily()?.catch { } ?: flowOf(null) }
    val family by families.collectAsStateWithLifecycle(initialValue = null)
    val sentFlow: Flow<List<Announcement>?> = remember(repository) {
        repository?.observeSent()?.map<List<Announcement>, List<Announcement>?> { it }?.catch { emit(emptyList()) } ?: flowOf(emptyList())
    }
    val sent by sentFlow.collectAsStateWithLifecycle(initialValue = null)
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var urgent by rememberSaveable { mutableStateOf(false) }
    // Who was unticked: everyone is ticked at first. Ids joined by a line break, to survive a rotation.
    var unticked by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sentToast = stringResource(R.string.announcements_sent_toast)
    val candidates = family?.let { announcementCandidates(it, currentMember) }.orEmpty()
    val out = unticked.split('\n').filter { it.isNotEmpty() }.toSet()
    val selected = candidates.map { it.id }.filter { it !in out }.toSet()
    AnnouncementsScreen(
        family = family ?: Family(emptyList(), emptyList()),
        me = currentMember,
        candidates = candidates,
        title = title,
        onTitle = { title = it.take(Announcement.MAX_TITLE) },
        text = text,
        onText = { text = it.take(Announcement.MAX_TEXT) },
        urgent = urgent,
        onUrgent = { urgent = it },
        selected = selected,
        onSelect = { now -> unticked = candidates.map { it.id }.filter { it !in now }.joinToString("\n") },
        sending = sending,
        sent = sent,
        onBack = onBack,
        onOpen = onOpen,
        onSend = {
            if (repository != null && !sending) {
                sending = true
                scope.launch {
                    try {
                        repository.send(title, text, urgent, candidates.map { it.id }.filter { it in selected }, currentMember)
                        title = ""
                        text = ""
                        urgent = false
                        unticked = ""
                        Toast.makeText(context, sentToast, Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        sending = false
                    }
                }
            }
        },
        failed = failed,
        onFailureShown = { failed = false },
    )
}

/** The whole message, signed « Camille (admin) ». */
@Composable
fun AnnouncementRoute(container: AppContainer?, announcementId: String, onBack: () -> Unit) {
    val repository = container?.announcementRepository
    val states = remember(repository, announcementId) {
        repository?.observe(announcementId)
            ?.map<Announcement?, AnnouncementState> { it?.let(AnnouncementState::Ready) ?: AnnouncementState.Gone }
            ?.catch { emit(AnnouncementState.Gone) }
            ?: flowOf(AnnouncementState.Gone)
    }
    val state by states.collectAsStateWithLifecycle(initialValue = AnnouncementState.Loading)
    AnnouncementScreen(state = state, onBack = onBack)
}

sealed interface AnnouncementState {
    data object Loading : AnnouncementState
    data object Gone : AnnouncementState
    data class Ready(val announcement: Announcement) : AnnouncementState
}
