package fr.familia.app.feature.firststeps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.data.MemberPhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * « Mes premiers pas » (récit 61) for [memberId]: each gesture is ticked from what the family already
 * sees (a profile photo, « Goûts et passions » filled, a reaction to a good news). [tastesOn] is
 * false while « Goûts et passions » is off: that gesture is not offered. The screen never builds it in « Voir comme ».
 */
class FirstStepsViewModel(
    private val photos: MemberPhotoRepository?,
    cards: MemberCardRepository?,
    goodNews: GoodNewsRepository?,
    private val store: FirstStepsStore,
    private val memberId: String,
    tastesOn: Boolean,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.of("Europe/Paris"),
) : ViewModel() {

    private val marks = MutableStateFlow(store.load(memberId))
    private val _photoFailed = MutableStateFlow(false)

    /** A photo chosen from the card that could not be saved: the card says so once. */
    val photoFailed: StateFlow<Boolean> = _photoFailed.asStateFlow()

    private val hasPhoto: Flow<Boolean> = photos?.observePhotos()?.map { memberId in it }?.catch { emit(false) } ?: flowOf(false)

    private val hasTastes: Flow<Boolean?> = if (!tastesOn) {
        flowOf(null)
    } else {
        cards?.observeCard(memberId)?.map { card -> card != null && (card.tastes.isNotBlank() || card.hobbies.isNotBlank()) }
            ?.catch { emit(false) } ?: flowOf(false)
    }

    private val hasReacted: Flow<Boolean> =
        goodNews?.observeLatest(RECENT_NEWS)?.map { list -> list.any { memberId in it.reactions } }?.catch { emit(false) } ?: flowOf(false)

    val card: StateFlow<FirstStepsCard?> = combine(hasPhoto, hasTastes, hasReacted, marks) { photo, tastes, reacted, kept ->
        val done = buildMap {
            put(FirstStep.Photo, photo)
            tastes?.let { put(FirstStep.Tastes, it) }
            put(FirstStep.Reaction, reacted)
        }
        val (shown, next) = firstSteps(done, kept, today())
        if (next != kept) keep(next)
        shown
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** « Plus tard »: hidden for the rest of the day. */
    fun later() = keep(marks.value.copy(snoozedDay = today().toEpochDay()))

    /** « Ne plus afficher », and « Fermer » under « Bravo »: hidden for good on this phone. */
    fun dismiss() = keep(marks.value.copy(dismissed = true))

    /** The profile photo framed from the card (same rights and size as in Famille, the rules check them). */
    fun savePhoto(jpeg: ByteArray) {
        val repo = photos ?: return
        viewModelScope.launch {
            try {
                repo.savePhoto(memberId, jpeg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _photoFailed.value = true
            }
        }
    }

    fun photoFailureShown() {
        _photoFailed.value = false
    }

    private fun keep(next: FirstStepsMarks) {
        store.save(memberId, next)
        marks.value = next
    }

    private fun today(): LocalDate = clock().atZone(zone).toLocalDate()

    private companion object {
        /** The good news looked at for a first reaction: a new member reacts to recent ones. */
        const val RECENT_NEWS = 50
    }
}
