package fr.familia.app.core.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    private fun card(id: String, feature: String? = null, adultsOnly: Boolean = false, childBody: String? = null) =
        WhatsNewCard(id, "Titre $id", "Phrase $id", childBody = childBody, feature = feature, adultsOnly = adultsOnly)

    private fun release(code: Long, vararg cards: WhatsNewCard) = WhatsNewRelease("r$code", code, "1.$code", cards.toList())

    private val allOn: (String) -> Boolean = { true }

    private class FakeStore : WhatsNewStore {
        val seen = mutableMapOf<String, Set<String>>()
        val started = mutableSetOf<String>()
        val fresh = mutableSetOf<String>()
        val later = mutableMapOf<String, Set<String>>()
        override fun postponed(memberId: String) = later[memberId].orEmpty()
        override fun addPostponed(memberId: String, ids: Set<String>) { later[memberId] = postponed(memberId) + ids }
        override fun seen(memberId: String) = seen[memberId].orEmpty()
        override fun addSeen(memberId: String, ids: Set<String>) { seen[memberId] = seen(memberId) + ids }
        override fun started(memberId: String) = memberId in started
        override fun freshInstall(memberId: String) = memberId in fresh
        override fun setFreshInstall(memberId: String) { fresh += memberId }
        override fun setStarted(memberId: String) { started += memberId; fresh -= memberId }
    }

    @Test
    fun `the most recent cards first, five at most, never those of a newer version than mine`() {
        val releases = listOf(
            release(10, card("a"), card("b")),
            release(11, card("c"), card("d"), card("e")),
            release(12, card("f"), card("g")),
            release(13, card("later")),
        )
        val ids = WhatsNew.pending(releases, installedVersion = 12, seen = emptySet(), child = false, isOnForFamily = allOn).map { it.card.id }
        assertEquals(listOf("f", "g", "c", "d", "e"), ids)
    }

    @Test
    fun `a card of a module switched off waits, then comes once the module is on`() {
        val releases = listOf(release(10, card("recettes", feature = "recipes")))
        assertTrue(WhatsNew.pending(releases, 10, emptySet(), false) { false }.isEmpty())
        assertEquals(1, WhatsNew.pending(releases, 10, emptySet(), false) { it == "recipes" }.size)
    }

    @Test
    fun `a child never sees a card for adults, and reads the child sentence`() {
        val releases = listOf(release(10, card("comptes", adultsOnly = true), card("voyages", childBody = "Pour toi")))
        val shown = WhatsNew.pending(releases, 10, emptySet(), child = true, isOnForFamily = allOn)
        assertEquals(listOf("voyages"), shown.map { it.card.id })
        assertEquals("Pour toi", shown.single().card.bodyFor(child = true))
        assertEquals("Phrase voyages", shown.single().card.bodyFor(child = false))
    }

    @Test
    fun `a new install shows nothing, then only what comes after`() {
        val store = FakeStore()
        val decider = WhatsNewDecider(store)
        decider.noteFreshInstall("m1")
        assertTrue(decider.cardsToShow("m1", listOf(release(10, card("a"))), 10, false, allOn).isEmpty())
        val next = listOf(release(10, card("a")), release(11, card("b")))
        assertEquals(listOf("b"), decider.cardsToShow("m1", next, 11, false, allOn).map { it.card.id })
    }

    @Test
    fun `a new install keeps aside the card of a module still off, it comes when the admin switches it on`() {
        val store = FakeStore()
        val decider = WhatsNewDecider(store)
        val releases = listOf(release(10, card("a"), card("recettes", feature = "recipes")))
        decider.noteFreshInstall("m1")
        assertTrue(decider.cardsToShow("m1", releases, 10, false) { false }.isEmpty())
        assertEquals(listOf("recettes"), decider.cardsToShow("m1", releases, 10, false, allOn).map { it.card.id })
    }

    @Test
    fun `an update shows its cards until they are seen`() {
        val store = FakeStore()
        val decider = WhatsNewDecider(store)
        val releases = listOf(release(10, card("a")))
        val shown = decider.cardsToShow("m1", releases, 10, false, allOn)
        assertEquals(listOf("a"), shown.map { it.card.id })
        decider.markSeen("m1", shown)
        assertTrue(decider.cardsToShow("m1", releases, 10, false, allOn).isEmpty())
    }

    @Test
    fun `each update shows its own cards, one after the other`() {
        val decider = WhatsNewDecider(FakeStore())
        val first = listOf(release(10, card("a")))
        decider.markSeen("m1", decider.cardsToShow("m1", first, 10, false, allOn))
        // The next publication adds its file with its own number (retour du 19/11).
        val second = first + release(11, card("b"))
        assertTrue(decider.cardsToShow("m1", second, 10, false, allOn).isEmpty())
        val shown = decider.cardsToShow("m1", second, 11, false, allOn)
        assertEquals(listOf("b"), shown.map { it.card.id })
        assertEquals("1.11", shown.single().release.versionName)
        decider.markSeen("m1", shown)
        val third = second + release(12, card("c"))
        assertEquals(listOf("c"), decider.cardsToShow("m1", third, 12, false, allOn).map { it.card.id })
    }

    @Test
    fun `plus tard shows the cards once more, at the next opening only`() {
        val decider = WhatsNewDecider(FakeStore())
        val releases = listOf(release(10, card("a")))
        decider.postpone("m1", decider.cardsToShow("m1", releases, 10, false, allOn))
        val again = decider.cardsToShow("m1", releases, 10, false, allOn)
        assertEquals(listOf("a"), again.map { it.card.id })
        decider.postpone("m1", again)
        assertTrue(decider.cardsToShow("m1", releases, 10, false, allOn).isEmpty())
    }

    @Test
    fun `the history keeps every version, newest first, and leaves out those with nothing for me`() {
        val releases = listOf(release(10, card("a")), release(11, card("comptes", adultsOnly = true)), release(12, card("b")))
        val history = WhatsNew.history(releases, 12, child = true, isOnForFamily = allOn)
        assertEquals(listOf(12L, 10L), history.map { it.first.versionCode })
    }

    @Test
    fun `the corrections come after the new features, even those of an older version`() {
        val fixes12 = WhatsNewCard("corrections-12", "Corrigé", "• Une ligne", fixes = true)
        val releases = listOf(release(11, card("a")), release(12, card("b"), fixes12))
        val ids = WhatsNew.pending(releases, 12, emptySet(), child = false, isOnForFamily = allOn).map { it.card.id }
        assertEquals(listOf("b", "a", "corrections-12"), ids)
    }

    @Test
    fun `parse reads a card of corrections`() {
        val raw = listOf(
            mapOf(
                "id" to "corrections-300", "versionCode" to 300L, "versionName" to "1.300",
                "cards" to listOf(mapOf("id" to "corrections-300", "title" to "Corrigé", "body" to "• Une ligne", "kind" to "fixes")),
            ),
        )
        assertTrue(WhatsNew.parse(raw).single().cards.single().fixes)
    }

    @Test
    fun `parse leaves out an incomplete card or version`() {
        val raw = listOf(
            mapOf(
                "id" to "2026-11-28", "versionCode" to 210L, "versionName" to "1.210",
                "cards" to listOf(
                    mapOf("id" to "ok", "title" to "Titre", "body" to "Phrase", "open" to "recipes", "adultsOnly" to true),
                    mapOf("id" to "sans-phrase", "title" to "Titre"),
                ),
            ),
            mapOf("id" to "sans-numero", "cards" to emptyList<Any>()),
            "pas une version",
        )
        val releases = WhatsNew.parse(raw)
        assertEquals(1, releases.size)
        val card = releases.single().cards.single()
        assertEquals("ok", card.id)
        assertEquals(WhatsNewTarget.Recipes, card.target)
        assertTrue(card.adultsOnly)
        assertNull(WhatsNewCard("x", "t", "b", open = "inconnu").target)
        assertTrue(WhatsNew.parse(null).isEmpty())
    }
}
