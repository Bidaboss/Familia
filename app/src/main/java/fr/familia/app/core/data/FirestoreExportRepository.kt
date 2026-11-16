package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Reads the collections of docs/data-model.md from the server only: the offline cache may hold
 * part of the data, and a backup must be complete or not be made. Left out on purpose: the capsules the admin principal cannot read, the answers of the question of the week before they show to everyone, memberEmails (rebuilt from the members), and each member's
 * phones and settings, which only they can read.
 */
class FirestoreExportRepository(private val db: FirebaseFirestore) : ExportRepository {

    override suspend fun loadFamilyData(memberId: String): FamilyData = try {
        load(memberId)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw ExportOfflineException(e) else throw e
    }

    private suspend fun load(memberId: String): FamilyData = coroutineScope {
        val households = async { db.collection("households").get(Source.SERVER).await().documents.map(::toExport) }
        val members = async { db.collection("members").get(Source.SERVER).await().documents.map(::toExport) }
        val events = async { db.collection("events").get(Source.SERVER).await().documents.map(::toExport) }
        val answers = async {
            db.collectionGroup("rsvps").get(Source.SERVER).await().documents.mapNotNull { doc ->
                val eventId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                ExportAnswer(eventId, doc.id, plain(doc.data.orEmpty()))
            }
        }
        val helpRequests = async { db.collection("helpRequests").get(Source.SERVER).await().documents.map(::toExport) }
        val weekly = async { weeklyQuestions() }
        val capsules = async { capsules(memberId) }
        val (questions, weeklyAnswers) = weekly.await()
        FamilyData(
            households.await(), members.await(), events.await(), answers.await(), helpRequests.await(), questions, weeklyAnswers,
            capsules.await(),
        )
    }

    /**
     * The rules let anyone read the answers of a question only from its revealAt: the answers of
     * this week's question join the backup 48 h after it is published.
     */
    private suspend fun weeklyQuestions(): Pair<List<ExportDocument>, List<ExportWeeklyAnswer>> = coroutineScope {
        val questions = db.collection("weeklyQuestions").get(Source.SERVER).await().documents
        val now = Timestamp.now()
        val answers = questions
            .filter { q -> q.getTimestamp("revealAt")?.let { it <= now } == true }
            .map { q ->
                async {
                    q.reference.collection("answers").get(Source.SERVER).await().documents.map { a ->
                        ExportWeeklyAnswer(q.id, a.id, plain(a.data.orEmpty()))
                    }
                }
            }
        questions.map(::toExport) to answers.flatMap { it.await() }
    }

    /** The rules let one read only the capsules one wrote and those opened for one: two queries. */
    private suspend fun capsules(memberId: String): List<ExportDocument> = coroutineScope {
        val capsules = db.collection("capsules")
        val written = async { capsules.whereEqualTo("authorId", memberId).get(Source.SERVER).await().documents }
        val opened = async { capsules.whereArrayContains("openedFor", memberId).get(Source.SERVER).await().documents }
        (written.await() + opened.await()).distinctBy { it.id }.map(::toExport)
    }

    private fun toExport(doc: DocumentSnapshot) = ExportDocument(doc.id, plain(doc.data.orEmpty()))

    private fun plain(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { (_, v) -> plainValue(v) }

    private fun plainValue(value: Any?): Any? = when (value) {
        is Timestamp -> value.toInstant()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to plainValue(v) }
        is List<*> -> value.map(::plainValue)
        else -> value
    }
}
