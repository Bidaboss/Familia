package fr.familia.app.feature.joinme

import fr.familia.app.core.model.Member
import fr.familia.app.core.place.Place

/** Where the place comes from: the position of the moment, or a place the member chose. */
enum class PlaceSource { Here, Elsewhere }

/** Finding the place, then what was found. */
sealed interface PlaceLookup {
    data object Idle : PlaceLookup
    data object Searching : PlaceLookup
    data class Found(val place: Place) : PlaceLookup

    /** Location off, or no fix within the delay. */
    data object NoPosition : PlaceLookup

    /** The member said no to the location permission. */
    data object NoPermission : PlaceLookup

    /** Nothing matches the address or the link typed. */
    data object NotFound : PlaceLookup
}

/** A shortcut that ticks a whole group: « Mon foyer », « Toute la famille ». */
data class RecipientShortcut(val kind: Kind, val memberIds: Set<String>) {
    enum class Kind { MyHousehold, Family }
}

/** The members of one household who can receive it (with the app), adults first. */
data class RecipientGroup(val name: String, val members: List<Member>)

enum class JoinMeError { NameTooLong, NoteTooLong }

sealed interface JoinMeUiState {
    data object Loading : JoinMeUiState
    data object Unavailable : JoinMeUiState

    data class Editing(
        val source: PlaceSource = PlaceSource.Here,
        val lookup: PlaceLookup = PlaceLookup.Idle,
        val query: String = "",
        val name: String = "",
        val note: String = "",
        /** Nobody at first (carte AVIS-13): the member ticks the recipients each time. */
        val recipientIds: Set<String> = emptySet(),
        val groups: List<RecipientGroup> = emptyList(),
        val shortcuts: List<RecipientShortcut> = emptyList(),
        val errors: Set<JoinMeError> = emptySet(),
        val sending: Boolean = false,
        val sendFailed: Boolean = false,
    ) : JoinMeUiState {
        val place: Place? get() = (lookup as? PlaceLookup.Found)?.place

        /** The send button stays off until a place is found and someone is ticked. */
        val canSend: Boolean get() = place != null && recipientIds.isNotEmpty() && errors.isEmpty() && !sending
    }

    /** Sent: the screen closes. */
    data object Sent : JoinMeUiState
}
