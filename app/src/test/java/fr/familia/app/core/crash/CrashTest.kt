package fr.familia.app.core.crash

import fr.familia.app.core.data.CrashRepository
import fr.familia.app.core.model.CrashReport
import fr.familia.app.core.model.crashGroups
import fr.familia.app.feature.crashes.crashForClaude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** « Plantages » (récit 6 sexies, rév. 220). Fictional data only. */
class CrashTest {

    private fun boom(message: String, line: Int = 42): IllegalStateException =
        IllegalStateException(message).apply {
            stackTrace = arrayOf(
                StackTraceElement("fr.familia.app.feature.home.HomeViewModel", "load", "HomeViewModel.kt", line),
                StackTraceElement("kotlinx.coroutines.DispatchedTask", "run", "DispatchedTask.kt", 108),
            )
        }

    @Test
    fun `the trace never holds the message, where a typed text could be`() {
        val error = RuntimeException("wrap: Anniversaire de Camille", boom("Titre tapé : Repas chez Inès"))
        val trace = crashTrace(error)
        assertFalse(trace.contains("Camille"))
        assertFalse(trace.contains("Inès"))
        assertFalse(trace.contains("Titre"))
        assertTrue(trace.startsWith("java.lang.RuntimeException"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalStateException"))
        assertTrue(trace.contains("at fr.familia.app.feature.home.HomeViewModel.load(HomeViewModel.kt:42)"))
    }

    @Test
    fun `the trace stays within the size the rules accept`() {
        val error = IllegalStateException().apply {
            stackTrace = Array(500) { StackTraceElement("fr.familia.app.Very${"Long".repeat(20)}", "m$it", "F.kt", it) }
        }
        assertTrue(crashTrace(error).length <= CrashReport.TRACE_MAX)
    }

    @Test
    fun `the same error at the same place has the same signature, whatever its message`() {
        val a = crashSignature(boom("un"))
        assertEquals(a, crashSignature(boom("deux")))
        assertEquals(a, crashSignature(RuntimeException(boom("trois"))))
        assertNotEquals(a, crashSignature(boom("un", line = 43)))
        assertTrue(Regex("^[0-9a-f]{16}$").matches(a))
    }

    @Test
    fun `the screen is the name of its destination, never the id it was opened with`() {
        assertEquals("Home", crashScreenName("fr.familia.app.feature.home.HomeDestination"))
        assertEquals("EventDetail", crashScreenName("fr.familia.app.feature.events.detail.EventDetailDestination/{eventId}"))
        assertEquals("GiftList", crashScreenName("fr.familia.app.feature.gifts.GiftListDestination/abc123"))
        assertEquals("Recipes", crashScreenName("fr.familia.app.feature.recipes.RecipesDestination?search={search}"))
        assertEquals("", crashScreenName(null))
    }

    private val trace = "java.lang.IllegalStateException\n\tat fr.familia.app.feature.home.HomeViewModel.load(HomeViewModel.kt:42)"

    private fun report(id: String, member: String, signature: String, at: String, version: String = "1.4.0") =
        CrashReport(id, member, version, 140, "14", "Google Pixel 8", "Home", trace, signature, Instant.parse(at))

    @Test
    fun `the reports of one kind are grouped, with how many times and how many people`() {
        val groups = crashGroups(
            listOf(
                report("r1", "camille", "aaaaaaaaaaaaaaaa", "2026-11-21T10:00:00Z"),
                report("r2", "ines", "aaaaaaaaaaaaaaaa", "2026-11-21T12:00:00Z", version = "1.4.1"),
                report("r3", "camille", "aaaaaaaaaaaaaaaa", "2026-11-21T11:00:00Z"),
                report("r4", "noe", "bbbbbbbbbbbbbbbb", "2026-11-21T09:00:00Z"),
            ),
        )
        assertEquals(listOf("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb"), groups.map { it.signature })
        val first = groups.first()
        assertEquals(3, first.count)
        assertEquals(2, first.people)
        assertEquals("r2", first.latest.id)
        assertEquals(Instant.parse("2026-11-21T10:00:00Z"), first.firstAt)
        assertEquals(listOf("1.4.1", "1.4.0"), first.versions)
        assertEquals("IllegalStateException", first.errorName)
        assertEquals("HomeViewModel.kt:42", first.place)
    }

    @Test
    fun `no line of our code, no place`() {
        val r = report("r1", "camille", "aaaaaaaaaaaaaaaa", "2026-11-21T10:00:00Z").copy(trace = "java.lang.OutOfMemoryError\n\tat java.util.Arrays.copyOf(Arrays.java:3)")
        assertNull(crashGroups(listOf(r)).first().place)
    }

    @Test
    fun `the text for Claude says what, how often, where, never who`() {
        val group = crashGroups(
            listOf(
                report("r1", "camille", "aaaaaaaaaaaaaaaa", "2026-11-21T10:00:00Z"),
                report("r2", "ines", "aaaaaaaaaaaaaaaa", "2026-11-21T12:05:00Z"),
            ),
        ).first()
        val text = crashForClaude(group, ZoneId.of("Europe/Paris"))
        assertTrue(text.startsWith("Plantage reçu dans l'appli Familia (Administration > Plantages) : IllegalStateException."))
        assertTrue(text.contains("Dans notre code : HomeViewModel.kt:42."))
        assertTrue(text.contains("Vu 2 fois, chez 2 personnes, du 21 novembre 2026 à 11h00 au 21 novembre 2026 à 13h05."))
        assertTrue(text.contains("Versions : Familia 1.4.0."))
        assertTrue(text.contains("Téléphones : Android 14 · Google Pixel 8."))
        assertTrue(text.contains("Écran : Home."))
        assertTrue(text.contains(trace))
        assertTrue(text.endsWith("Peux-tu chercher la cause dans le code de Familia et proposer une correction ?"))
        assertFalse(text.contains("camille"))
        assertFalse(text.contains("ines"))
    }

    private class FakeCrashes(private val failOn: String? = null) : CrashRepository {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun send(memberId: String, crash: PendingCrash) {
            if (crash.id == failOn) error("refusé")
            sent += memberId to crash.id
        }
        override fun observeAll(): Flow<List<CrashReport>> = emptyFlow()
        override suspend fun settle(signature: String) = Unit
    }

    private fun pending(id: String) = PendingCrash(id, "1.4.0", 140, "14", "Google Pixel 8", "Home", trace, "aaaaaaaaaaaaaaaa", 0)

    @Test
    fun `each crash kept on the phone is sent in the member's name, then forgotten, even when refused`() = runTest {
        val repo = FakeCrashes(failOn = "b")
        val forgotten = mutableListOf<String>()
        sendPendingCrashes(listOf(pending("a"), pending("b"), pending("c")), "camille", repo) { forgotten += it }
        assertEquals(listOf("camille" to "a", "camille" to "c"), repo.sent)
        assertEquals(listOf("a", "b", "c"), forgotten)
    }
}
