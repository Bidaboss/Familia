package fr.familia.app.feature.events

import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.eventOrder
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Fictional family shared by the event tests: admin1 (admin) and adult1 in h1, adult2 in h2,
 * kid1 (child with the app, parent adult1), kid2 (child without the app, parent adult2).
 */
object TestFamily {
    private fun m(id: String, household: String, profile: MemberProfile = MemberProfile.Adult, admin: Boolean = false, parents: List<String> = emptyList()) =
        Member(id, id.replaceFirstChar { it.uppercase() }, "$id@example.com", null, household, profile, admin, parents, "uid-$id")

    val admin = m("admin1", "h1", admin = true)
    val adult = m("adult1", "h1")
    val otherAdult = m("adult2", "h2")
    val kid = m("kid1", "h1", MemberProfile.ChildWithApp, parents = listOf("adult1"))
    val otherKid = m("kid2", "h2", MemberProfile.ChildWithoutApp, parents = listOf("adult2"))
    val family = Family(
        households = listOf(Household("h1", "Foyer Un"), Household("h2", "Foyer Deux")),
        members = listOf(admin, adult, otherAdult, kid, otherKid),
    )

    fun event(id: String, author: Member, target: EventTarget = EventTarget.ForFamily, date: LocalDate = LocalDate.of(2026, 12, 24)) =
        Event(id, "Repas $id", date, null, null, null, target, author.id, author.firstName)
}

class FakeMembers(family: Family = TestFamily.family) : MemberRepository {
    val family = MutableStateFlow(family)
    override suspend fun findByEmail(email: String): Member? = null
    override suspend fun linkAccount(memberId: String, uid: String) = Unit
    override fun observeFamily(): Flow<Family> = family
    override fun newMemberId(): String = "new"
    override suspend fun saveMember(member: Member, previousEmail: String?) = Unit
    override suspend fun removeMember(member: Member, updatedChildren: List<Member>) = Unit
    override fun newHouseholdId(): String = "h-new"
    override suspend fun saveHousehold(household: Household) = Unit
}

class FakeEvents(initial: List<Event> = emptyList(), private val failWith: Exception? = null, private val hang: Boolean = false) : EventRepository {
    val events = MutableStateFlow(initial)
    val saved = mutableListOf<Pair<Event, Boolean>>()
    val deleted = mutableListOf<String>()
    override fun observeUpcoming(from: LocalDate): Flow<List<Event>> =
        events.map { list -> list.filter { it.date >= from }.sortedWith(eventOrder) }
    override suspend fun getEvent(id: String): Event? = events.value.firstOrNull { it.id == id }
    override fun newEventId(): String = "e-new"
    override suspend fun saveEvent(event: Event, isNew: Boolean) {
        failWith?.let { throw it }
        if (hang) awaitCancellation()
        saved += event to isNew
    }
    override suspend fun deleteEvent(id: String) {
        failWith?.let { throw it }
        deleted += id
    }
}
