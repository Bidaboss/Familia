package fr.familia.app.feature.events.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.R
import fr.familia.app.core.calendar.CalendarEntry
import fr.familia.app.core.calendar.addToCalendar
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.eventShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.photo.CoverBanner
import fr.familia.app.feature.events.album.EventAlbumSection
import fr.familia.app.feature.events.bring.BringSection
import fr.familia.app.feature.events.carpool.CarpoolSection
import fr.familia.app.feature.events.reminders.EventRemindersSection
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.comments.EventCommentsSection
import fr.familia.app.feature.events.album.isAlbumOpen
import fr.familia.app.feature.polls.event.EventPollsSection

/** Connects the event view to its ViewModel, and closes it when the event is deleted meanwhile. */
@Composable
fun EventDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: () -> Unit = {},
    /** « Comptes de l'événement » (PRODUIT-57), wired once the expenses screens exist. */
    onOpenExpenses: (() -> Unit)? = null,
    /** Opens a recipe joined to a line of « Qui apporte quoi », while the Recettes module is on. */
    onOpenRecipe: ((recipeId: String) -> Unit)? = null,
    /** « Lancer un sondage » and its polls (récit 2 quater). */
    onNewPoll: () -> Unit = {},
    onOpenPoll: (pollId: String) -> Unit = {},
) {
    val viewModel: EventDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventDetailViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    container?.rsvpRepository,
                    currentMember.id,
                    eventId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Deleted from the form opened here, or by someone else: back to the planning.
        if (state is EventDetailUiState.Success) wasShown = true
        if (state == EventDetailUiState.Missing && wasShown) onBack()
    }
    val context = LocalContext.current
    val calendarOn = LocalFeatureFlags.current.isOn(Feature.Calendar)
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val expensesOn = LocalFeatureFlags.current.isOn(Feature.Expenses)
    val shown = state as? EventDetailUiState.Success
    EventDetailScreen(
        state = state,
        onAddToCalendar = shown?.takeIf { calendarOn }?.let { success ->
            { addToCalendar(context, CalendarEntry.forEvent(success.event, context.getString(R.string.calendar_created_from))) }
        },
        onShare = shown?.takeIf { shareOn }?.let { success ->
            { share(context, eventShare(context, success.event, success.today)) }
        },
        onOpenExpenses = onOpenExpenses?.takeIf { expensesOn && shown != null && canOpenExpenses(shown, currentMember) },
        onAnswer = viewModel::answer,
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onBack = onBack,
        header = {
            val cover = (state as? EventDetailUiState.Success)?.event?.coverPhoto
            if (cover != null && LocalFeatureFlags.current.isOn(Feature.MorePhotos)) {
                CoverBanner(cover, Modifier.padding(bottom = 8.dp))
            }
        },
        album = {
            val success = state as? EventDetailUiState.Success
            // « Me le rappeler » (récit 56), private, right under « Je viens ».
            if (success != null && LocalFeatureFlags.current.isOn(Feature.MyReminders) && container?.eventReminderRepository != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventRemindersSection(
                    container, currentMember, success.event,
                    coming = success.answerers.any { it.isMe && it.status == RsvpStatus.Yes },
                )
            }
            // « On y va ensemble » (récit 52), right under « Je viens »: me and the children I answer for.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Carpool)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                CarpoolSection(
                    container, currentMember, eventId,
                    riderIds = success.answerers.map { it.member.id },
                    iAmComing = success.answerers.any { it.isMe && it.status == RsvpStatus.Yes },
                )
            }
            // « Qui apporte quoi » (récit 4 bis), before the album.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Bring)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                BringSection(container, currentMember, eventId, success.canManage, onOpenRecipe = onOpenRecipe)
            }
            if (success != null && LocalFeatureFlags.current.isOn(Feature.EventAlbums)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventAlbumSection(container, currentMember, eventId, isAlbumOpen(success.event, success.today), onOpenAlbum)
            }
            // Its polls (récit 2 quater): a date, a menu, a place, asked to its guests.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Polls)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventPollsSection(container, currentMember, eventId, success.today, onNewPoll, onOpenPoll)
            }
            // The comments (récit 28), last: they can grow long.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Comments)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventCommentsSection(container, currentMember, eventId)
            }
        },
    )
}
