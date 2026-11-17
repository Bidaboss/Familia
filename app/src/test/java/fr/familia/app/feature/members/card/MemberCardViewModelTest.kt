package fr.familia.app.feature.members.card

import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.cleaned
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Fictional test family: admin1 (admin principal) and adult1 in h1, adult2 in h2, kid1 (child with
 * the app, h1), kid2 (child without the app, h2).
 */
class MemberCardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeCards(initial: Map<String, MemberCard> = emptyMap()) : MemberCardRepository {
        val cards = MutableStateFlow(initial)
        val saved = mutableListOf<Pair<MemberCard, String>>()
        override fun observeCard(memberId: String): Flow<MemberCard?> = cards.map { it[memberId] }
        override suspend fun saveCard(card: MemberCard, byId: String) {
            saved += card to byId
            cards.value = if (card.isEmpty) cards.value - card.memberId else cards.value + (card.memberId to card)
        }
    }

    @Test
    fun `a card is trimmed, and a field too long is refused rather than cut`() {
        assertEquals(MemberCard("a", tastes = "Le thé", hobbies = "Le jardin"), MemberCard("a", tastes = "Le thé\n", hobbies = " Le jardin ").cleaned())
        assertNull(MemberCard("a", hobbies = "x".repeat(MemberCard.TEXT_MAX + 1)).cleaned())
        assertNull(MemberCard("a", tastes = "x".repeat(MemberCard.TEXT_MAX + 1)).cleaned())
        assertNull(MemberCard("a", shoes = "x".repeat(MemberCard.SIZE_MAX + 1)).cleaned())
        assertEquals("42", MemberCard("a", shoes = " 42 ").cleaned()!!.shoes)
        assertTrue(MemberCard("a", hobbies = "  ").cleaned()!!.isEmpty)
    }

    @Test
    fun `the member fills in their own card, signed with their id`() = runTest {
        val cards = FakeCards()
        val vm = MemberCardViewModel(cards, FakeMembers(), "adult2", "adult2")
        val empty = vm.uiState.first { it is MemberCardUiState.Ready } as MemberCardUiState.Ready
        assertTrue(empty.card.isEmpty && empty.isMe && empty.canEdit)
        assertTrue(vm.save(MemberCard("ignored", hobbies = " La pêche ")))
        val filled = vm.uiState.first { (it as? MemberCardUiState.Ready)?.card?.hobbies == "La pêche" }
        assertEquals("adult2", (filled as MemberCardUiState.Ready).card.memberId)
        assertEquals("adult2", cards.saved.single().second)
        assertFalse(vm.save(MemberCard("adult2", tastes = "x".repeat(MemberCard.TEXT_MAX + 1))))
        assertEquals(1, cards.saved.size)
    }

    @Test
    fun `an adult of the household fills in a child's card, another adult only reads`() = runTest {
        val cards = FakeCards(mapOf("kid1" to MemberCard("kid1", hobbies = "Le foot"), "adult1" to MemberCard("adult1", tastes = "Le thé")))
        val parent = MemberCardViewModel(cards, FakeMembers(), "adult1", "kid1")
        assertTrue((parent.uiState.first { it is MemberCardUiState.Ready } as MemberCardUiState.Ready).canEdit)
        val other = MemberCardViewModel(cards, FakeMembers(), "adult2", "adult1")
        val read = other.uiState.first { it is MemberCardUiState.Ready } as MemberCardUiState.Ready
        assertFalse(read.canEdit)
        assertEquals("Le thé", read.card.tastes)
        assertFalse(other.save(MemberCard("adult1", tastes = "Le café")))
        assertTrue(cards.saved.isEmpty())
    }

    @Test
    fun `an empty card is hidden from those who cannot fill it in`() = runTest {
        val vm = MemberCardViewModel(FakeCards(), FakeMembers(), "adult2", "adult1")
        assertEquals(MemberCardUiState.Hidden, vm.uiState.first { it !is MemberCardUiState.Loading })
        val admin = MemberCardViewModel(FakeCards(), FakeMembers(), "admin1", "adult2")
        assertTrue((admin.uiState.first { it is MemberCardUiState.Ready } as MemberCardUiState.Ready).canEdit)
    }
}
