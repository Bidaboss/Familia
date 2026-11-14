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

/** One member's answer to one event. [answeredBy] is the member or one of their responsible adults. */
data class Rsvp(val memberId: String, val status: RsvpStatus, val answeredBy: String)
