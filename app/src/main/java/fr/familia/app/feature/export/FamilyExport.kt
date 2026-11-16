package fr.familia.app.feature.export

import fr.familia.app.core.data.ExportDocument
import fr.familia.app.core.data.FamilyData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate

/** Bumped if the shape of the file changes, so a future import knows what it reads. */
const val EXPORT_FORMAT_VERSION = 1

private val prettyJson = Json { prettyPrint = true }

/**
 * The backup file (PRD, récit 6 ter): one JSON object with the members, households, events,
 * answers, help requests and questions of the week, as stored in Firestore. Ids are kept, so the file can be read back into a new
 * database. Times are written in ISO 8601 (UTC). Documents are sorted by id, so two exports of
 * the same data give the same file.
 */
fun familyExportJson(data: FamilyData, exportedAt: Instant, appVersion: String): String {
    val root = buildJsonObject {
        put("app", "Familia")
        put("formatVersion", EXPORT_FORMAT_VERSION)
        put("appVersion", appVersion)
        put("exportedAt", exportedAt.toString())
        put("households", documents(data.households))
        put("members", documents(data.members))
        put("events", documents(data.events))
        put("helpRequests", documents(data.helpRequests))
        put("weeklyQuestions", documents(data.weeklyQuestions))
        put(
            "weeklyAnswers",
            JsonArray(
                data.weeklyAnswers.sortedWith(compareBy({ it.questionId }, { it.memberId })).map { answer ->
                    JsonObject(
                        mapOf("questionId" to JsonPrimitive(answer.questionId), "memberId" to JsonPrimitive(answer.memberId)) +
                            fields(answer.fields),
                    )
                },
            ),
        )
        put(
            "rsvps",
            JsonArray(
                data.answers.sortedWith(compareBy({ it.eventId }, { it.memberId })).map { answer ->
                    JsonObject(
                        mapOf("eventId" to JsonPrimitive(answer.eventId), "memberId" to JsonPrimitive(answer.memberId)) +
                            fields(answer.fields),
                    )
                },
            ),
        )
    }
    return prettyJson.encodeToString(JsonObject.serializer(), root)
}

/** "familia-sauvegarde-2026-11-14.json". */
fun exportFileName(day: LocalDate): String = "familia-sauvegarde-$day.json"

private fun documents(docs: List<ExportDocument>): JsonArray =
    JsonArray(docs.sortedBy { it.id }.map { JsonObject(mapOf("id" to JsonPrimitive(it.id)) + fields(it.fields)) })

private fun fields(fields: Map<String, Any?>): Map<String, JsonElement> =
    fields.toSortedMap().mapValues { (_, v) -> toJson(v) }

private fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Instant -> JsonPrimitive(value.toString())
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) }.toSortedMap())
    is List<*> -> JsonArray(value.map(::toJson))
    else -> JsonPrimitive(value.toString()) // A type the app does not write: kept as text, never lost.
}
