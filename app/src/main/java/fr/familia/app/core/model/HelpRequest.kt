package fr.familia.app.core.model

import fr.familia.app.core.photo.SinglePhoto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The kind of help asked for (PRD, récit 13). */
enum class HelpKind(val firestoreValue: String) {
    Childcare("childcare"),
    Ride("ride"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): HelpKind? = entries.firstOrNull { it.firestoreValue == value }
    }
}

enum class HelpStatus(val firestoreValue: String) {
    Open("open"),
    Taken("taken"),
    Cancelled("cancelled"),
    ;

    companion object {
        fun fromFirestore(value: String?): HelpStatus? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/**
 * A « coup de main » (PRD, récits 13 to 18): someone asks the people in [notifyIds] for help on
 * [date]. Only the author and those people see it. [forMemberId] is the author, or one of their
 * children. [declinedBy] is the « Je ne peux pas » of the only person warned (carte PRODUIT-22).
 */
data class HelpRequest(
    val id: String,
    val kind: HelpKind,
    val title: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val place: String?,
    val note: String?,
    val authorId: String,
    val authorName: String,
    val forMemberId: String,
    val notifyIds: List<String>,
    val status: HelpStatus = HelpStatus.Open,
    val volunteerId: String? = null,
    val volunteerName: String? = null,
    /** The volunteer's short word, 200 characters at most (récit 16, spec rév. 8). */
    val volunteerNote: String? = null,
    val declinedBy: String? = null,
    val thankedAt: Instant? = null,
    val createdAt: Instant? = null,
    /**
     * A photo joined to the request (Kévin 16/11: the QR code of a parcel, a picture of what to
     * fetch): the base of its two files in Storage ([fr.familia.app.core.photo.SinglePhoto]),
     * read only by those who see the request.
     */
    val photo: String? = null,
)

/** helpRequests/{requestId}/{memberId}/{photoId}: sent by [memberId], removed by the server with the request. */
fun helpRequestPhotoBase(requestId: String, memberId: String, photoId: String = SinglePhoto.newId()) =
    "helpRequests/$requestId/$memberId/$photoId"

/** The longest word a volunteer can leave (rules: isVolunteerNote). */
const val VOLUNTEER_NOTE_MAX = 200
