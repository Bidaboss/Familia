package fr.familia.app.core.model

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
    val declinedBy: String? = null,
    val thankedAt: Instant? = null,
    val createdAt: Instant? = null,
)
