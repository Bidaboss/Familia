package fr.familia.app.core.model

/** The three answers of the PRD, récit 4. */
enum class RsvpStatus(val firestoreValue: String) {
    Yes("yes"),
    Maybe("maybe"),
    No("no"),
    ;

    companion object {
        fun fromFirestore(value: String?): RsvpStatus? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/**
 * One member's answer to one event. [answeredBy] is the member or one of their responsible adults.
 * [pending]: given on this phone without network, it still waits to leave (it leaves by itself).
 */
data class Rsvp(val memberId: String, val status: RsvpStatus, val answeredBy: String, val pending: Boolean = false)
