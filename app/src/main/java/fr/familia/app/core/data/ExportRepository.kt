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
)

/** PRD, récit 6 ter: everything the family wrote, for a backup or to leave Firebase. */
interface ExportRepository {
    suspend fun loadFamilyData(): FamilyData
}
