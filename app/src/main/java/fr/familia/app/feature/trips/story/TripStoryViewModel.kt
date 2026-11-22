package fr.familia.app.feature.trips.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.TripWordRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

sealed interface TripStoryUiState {
    data object Loading : TripStoryUiState

    /** The trip is gone, this member no longer follows it, or no day has a word. */
    data object Missing : TripStoryUiState
    data object Error : TripStoryUiState

    data class Ready(
        val tripTitle: String,
        /** The days with a word, first to last. */
        val days: List<StoryDay>,
        val dayIndex: Int,
        val frames: List<StoryFrame>,
        val frameIndex: Int,
        val currentMemberId: String,
        val family: Family,
        val heartFailed: Boolean = false,
    ) : TripStoryUiState {
        val day: StoryDay get() = days[dayIndex]
        val frame: StoryFrame get() = frames[frameIndex]
        val hasNextDay: Boolean get() = dayIndex < days.lastIndex

        /** Whether this member put a heart on the word shown. */
        val hearted: Boolean get() = frame.story()?.word?.hearted(currentMemberId) == true
        val hearts: Int get() = frame.story()?.word?.heartIds?.size ?: 0
        fun memberOf(id: String): Member? = family.members.firstOrNull { it.id == id }
    }
}

/**
 * « Le voyage jour par jour » (récit 90): the words and photos of the steps, day after day, on
 * screens one touch apart. Nothing moves by itself. [startDay] is the day asked, else today, else
 * the last day with a word.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripStoryViewModel(
    private val trips: TripRepository?,
    private val words: TripWordRepository?,
    private val albums: AlbumPhotoRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String,
    private val startDay: LocalDate?,
    private val today: LocalDate,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** Where the member is: the date of the day (stays right when the words change) and the screen of it. */
    private val position = MutableStateFlow<Pair<LocalDate?, Int>>(startDay to 0)
    private val heartFailed = MutableStateFlow(false)
    private var current: TripStoryUiState.Ready? = null

    val uiState: StateFlow<TripStoryUiState> = attempt
        .flatMapLatest { n ->
            if (trips == null || words == null || members == null) {
                flowOf(TripStoryUiState.Missing)
            } else {
                val album: Flow<List<AlbumPhoto>> = (albums?.observePhotos(tripAlbumFolder(tripId)) ?: flowOf(emptyList()))
                    .catch { emit(emptyList()) } // An album that cannot be read leaves the words without photos.
                val data = combine(trips.observeTrip(tripId), trips.observeSteps(tripId), words.observeWords(tripId), album, members.observeFamily()) {
                        trip, steps, all, photos, family ->
                    Source(trip, steps, all, photos, family)
                }
                combine(data, position, heartFailed) { source, at, failed -> state(source, at, failed) }
                    .onStart { if (n > 0) emit(TripStoryUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(TripStoryUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripStoryUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** The next screen; from the last screen of a day, nothing (the end screen has its own buttons). */
    fun next() {
        val ready = current ?: return
        if (ready.frameIndex < ready.frames.lastIndex) move(ready.day.date, ready.frameIndex + 1)
    }

    fun previous() {
        val ready = current ?: return
        if (ready.frameIndex > 0) move(ready.day.date, ready.frameIndex - 1)
    }

    /** « Jour 3 »: the first screen of the next day with a word. */
    fun nextDay() {
        val ready = current ?: return
        if (ready.hasNextDay) move(ready.days[ready.dayIndex + 1].date, 0)
    }

    /** « Revoir ce jour »: back to its first screen. */
    fun replay() {
        val ready = current ?: return
        move(ready.day.date, 0)
    }

    /** A day of the strip of the trip page. */
    fun goTo(date: LocalDate) {
        move(date, 0)
    }

    private fun move(date: LocalDate, frame: Int) {
        heartFailed.value = false
        position.value = date to frame
    }

    /** The heart of this member on the word shown: put, or taken back. */
    fun toggleHeart() {
        val ready = current ?: return
        val story = ready.frame.story() ?: return
        val stepId = story.step.id
        val hearted = !story.word.hearted(currentMemberId)
        heartFailed.value = false
        viewModelScope.launch {
            val failed = try {
                // Still pending after the timeout: queued offline, Firestore sends it later.
                withTimeoutOrNull(writeTimeoutMillis) { words!!.setHeart(tripId, stepId, currentMemberId, hearted) }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            heartFailed.value = failed
        }
    }

    private class Source(val trip: Trip?, val steps: List<TripStep>, val words: Map<String, TripWord>, val album: List<AlbumPhoto>, val family: Family)

    private fun state(source: Source, at: Pair<LocalDate?, Int>, failed: Boolean): TripStoryUiState {
        val trip = source.trip
        if (trip == null || source.family.members.none { it.id == currentMemberId }) return TripStoryUiState.Missing
        val days = storyDays(trip, source.steps, source.words, source.album)
        val dayIndex = firstStoryDay(days, at.first, today) ?: return TripStoryUiState.Missing
        val frames = storyFrames(days[dayIndex])
        val ready = TripStoryUiState.Ready(
            tripTitle = trip.title,
            days = days,
            dayIndex = dayIndex,
            frames = frames,
            // A word taken back while it is read can leave fewer screens: the last one stays.
            frameIndex = if (days[dayIndex].date == at.first) at.second.coerceIn(0, frames.lastIndex) else 0,
            currentMemberId = currentMemberId,
            family = source.family,
            heartFailed = failed,
        )
        current = ready
        return ready
    }
}
