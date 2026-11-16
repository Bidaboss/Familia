package fr.familia.app.core.data

import java.time.Instant

/**
 * A raw copy of one Firestore document, for the export. Values are kept as they are stored:
 * String, Long, Double, Boolean, null, lists and maps; server times become [Instant].
 */
data class ExportDocument(val id: String, val fields: Map<String, Any?>)

/** An answer also needs its event, since the same member answers many events. */
data class ExportAnswer(val eventId: String, val memberId: String, val fields: Map<String, Any?>)

data class FamilyData(
    val households: List<ExportDocument>,
    val members: List<ExportDocument>,
    val events: List<ExportDocument>,
    val answers: List<ExportAnswer>,
    /** Coups de main (spec, « Données et notifications »): the admin principal reads them all. */
    val helpRequests: List<ExportDocument> = emptyList(),
    /** Question de la semaine (récit 37): the questions, and the answers once they show to everyone. */
    val weeklyQuestions: List<ExportDocument> = emptyList(),
    val weeklyAnswers: List<ExportWeeklyAnswer> = emptyList(),
    /**
     * Capsules temporelles (PRD rév. 184): only those the admin principal can read, the ones they
     * wrote and the ones opened for them. The others stay sealed, even in a backup.
     */
    val capsules: List<ExportDocument> = emptyList(),
)

/** An answer to the question of the week [questionId]: its id is the member's. */
data class ExportWeeklyAnswer(val questionId: String, val memberId: String, val fields: Map<String, Any?>)

/** The server could not be reached. A backup is complete or not made at all, never read from the cache. */
class ExportOfflineException(cause: Throwable) : Exception(cause)

/** PRD, récit 6 ter: everything the family wrote, for a backup or to leave Firebase. */
interface ExportRepository {
    /** [memberId] is the admin principal's: it decides which capsules they can read. */
    suspend fun loadFamilyData(memberId: String): FamilyData
}
