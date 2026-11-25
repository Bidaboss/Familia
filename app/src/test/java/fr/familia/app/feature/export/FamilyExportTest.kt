package fr.familia.app.feature.export

import fr.familia.app.core.data.ExportAnswer
import fr.familia.app.core.data.ExportDocument
import fr.familia.app.core.data.ExportTripChild
import fr.familia.app.core.data.ExportWeeklyAnswer
import fr.familia.app.core.data.FamilyData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Fictional family only. */
class FamilyExportTest {

    private val data = FamilyData(
        households = listOf(ExportDocument("h1", mapOf("name" to "Foyer Martin"))),
        members = listOf(
            ExportDocument("m2", mapOf("firstName" to "Noé", "profile" to "childWithoutApp", "parentIds" to listOf("m1"), "email" to null)),
            ExportDocument("m1", mapOf("firstName" to "Camille", "isAdmin" to true, "colorIndex" to 3L, "birthDate" to "1990-05-04")),
        ),
        events = listOf(
            ExportDocument("e1", mapOf("title" to "Repas", "date" to "2026-12-24", "updatedAt" to Instant.parse("2026-11-14T08:30:00Z"))),
        ),
        answers = listOf(
            ExportAnswer("e1", "m2", mapOf("status" to "yes", "answeredBy" to "m1")),
            ExportAnswer("e1", "m1", mapOf("status" to "maybe", "answeredBy" to "m1")),
        ),
        helpRequests = listOf(ExportDocument("r1", mapOf("title" to "Garder Noé", "notifyIds" to listOf("m1")))),
        weeklyQuestions = listOf(ExportDocument("2026-11-30", mapOf("text" to "Ton petit-déjeuner idéal ?", "answerCount" to 2L))),
        weeklyAnswers = listOf(
            ExportWeeklyAnswer("2026-11-30", "m2", mapOf("text" to null, "skipped" to true)),
            ExportWeeklyAnswer("2026-11-30", "m1", mapOf("text" to "Des crêpes", "skipped" to false)),
        ),
        capsules = listOf(ExportDocument("c1", mapOf("text" to "Pour tes 18 ans", "authorId" to "m1", "openedFor" to emptyList<String>()))),
        trips = listOf(ExportDocument("t1", mapOf("title" to "Bretagne", "readerIds" to listOf("m1")))),
        tripSteps = listOf(
            ExportTripChild("t1", "s2", mapOf("place" to "Quimper")),
            ExportTripChild("t1", "s1", mapOf("place" to "Rennes")),
        ),
        tripItems = listOf(ExportTripChild("t1", "i1", mapOf("label" to "Crème solaire"))),
    )

    private val json = Json.parseToJsonElement(familyExportJson(data, Instant.parse("2026-11-14T10:00:00Z"), "1.2.0")).jsonObject

    @Test
    fun `the file says what it is and when it was made`() {
        assertEquals("Familia", json.getValue("app").jsonPrimitive.content)
        assertEquals(EXPORT_FORMAT_VERSION, json.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals("1.2.0", json.getValue("appVersion").jsonPrimitive.content)
        assertEquals("2026-11-14T10:00:00Z", json.getValue("exportedAt").jsonPrimitive.content)
    }

    @Test
    fun `every collection is there with its ids, sorted`() {
        assertEquals(listOf("m1", "m2"), json.getValue("members").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals("Foyer Martin", json.getValue("households").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        val answers = json.getValue("rsvps").jsonArray.map { it.jsonObject }
        assertEquals(listOf("m1", "m2"), answers.map { it.getValue("memberId").jsonPrimitive.content })
        assertTrue(answers.all { it.getValue("eventId").jsonPrimitive.content == "e1" })
        assertEquals("Garder Noé", json.getValue("helpRequests").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals("2026-11-30", json.getValue("weeklyQuestions").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        val weekly = json.getValue("weeklyAnswers").jsonArray.map { it.jsonObject }
        assertEquals(listOf("m1", "m2"), weekly.map { it.getValue("memberId").jsonPrimitive.content })
        assertEquals("Des crêpes", weekly.first().getValue("text").jsonPrimitive.content)
        assertEquals("Pour tes 18 ans", json.getValue("capsules").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals("Bretagne", json.getValue("trips").jsonArray.single().jsonObject.getValue("title").jsonPrimitive.content)
        val steps = json.getValue("tripSteps").jsonArray.map { it.jsonObject }
        assertEquals(listOf("s1", "s2"), steps.map { it.getValue("id").jsonPrimitive.content })
        assertTrue(steps.all { it.getValue("tripId").jsonPrimitive.content == "t1" })
        assertEquals("Crème solaire", json.getValue("tripItems").jsonArray.single().jsonObject.getValue("label").jsonPrimitive.content)
    }

    @Test
    fun `values keep their type, and times are written in ISO 8601`() {
        val camille = json.getValue("members").jsonArray.first().jsonObject
        assertEquals("true", camille.getValue("isAdmin").jsonPrimitive.content)
        assertEquals(false, camille.getValue("isAdmin").jsonPrimitive.isString)
        assertEquals("3", camille.getValue("colorIndex").jsonPrimitive.content)
        val noe = json.getValue("members").jsonArray[1].jsonObject
        assertEquals(listOf("m1"), noe.getValue("parentIds").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("null", noe.getValue("email").toString())
        val event = json.getValue("events").jsonArray.single().jsonObject
        assertEquals("2026-11-14T08:30:00Z", event.getValue("updatedAt").jsonPrimitive.content)
    }

    @Test
    fun `two exports of the same data give the same file`() {
        val at = Instant.parse("2026-11-14T10:00:00Z")
        val shuffled = data.copy(members = data.members.reversed(), answers = data.answers.reversed())
        assertEquals(familyExportJson(data, at, "1.2.0"), familyExportJson(shuffled, at, "1.2.0"))
    }

    @Test
    fun `the file is named after the day`() {
        assertEquals("familia-sauvegarde-2026-11-14.json", exportFileName(LocalDate.of(2026, 11, 14)))
    }
}
