package fr.familia.app.feature.members.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.concerns
import fr.familia.app.core.model.nextBirthday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * « Une fiche de membre organisée » (PRD rév. 290, récit 66): what the new page reads besides the
 * old one, and the pure rules of its order. Behind « Nouvelle fiche de membre » (memberProfileV2).
 */

/** One person of « Sa famille »: a member opens their page, a relative without an account does not. */
data class KinPerson(val id: String, val name: String, val member: Member?)

/** Parents, partners and children of a person, from the tree and the parents of the members. */
data class Kinship(val parents: List<KinPerson>, val partners: List<KinPerson>, val children: List<KinPerson>) {
    val isEmpty: Boolean get() = parents.isEmpty() && partners.isEmpty() && children.isEmpty()
}

/** Everything the new page shows besides the sections it shares with the old one. */
data class ProfileV2State(
    val today: LocalDate,
    val household: Household?,
    val kinship: Kinship,
    val moments: List<Event>,
    val photos: List<GoodNews>,
    /** I may change the address of this household (the admin principal or its chef). */
    val managesHousehold: Boolean,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** How many events and photos « Ses moments » shows. */
internal const val MOMENTS_MAX = 3
internal const val PHOTOS_MAX = 6

/** How far « Ses moments » looks for an event: half a year, never the whole planning. */
private const val MOMENTS_DAYS = 180L

/**
 * Cadeaux comes before Goûts et passions in the 30 days before the birthday and from the 1st to the
 * 24th of December (PRODUIT-97); Goûts et passions comes first the rest of the year.
 */
internal fun giftsFirst(birthDate: LocalDate?, today: LocalDate): Boolean {
    if (today.monthValue == 12 && today.dayOfMonth <= 24) return true
    val birth = birthDate ?: return false
    val days = ChronoUnit.DAYS.between(today, nextBirthday(birth, today))
    return days in 0..30
}

/** The age on [today]. */
internal fun ageOn(birthDate: LocalDate, today: LocalDate): Int {
    val years = today.year - birthDate.year
    val hadIt = today.monthValue > birthDate.monthValue ||
        (today.monthValue == birthDate.monthValue && today.dayOfMonth >= birthDate.dayOfMonth)
    return if (hadIt) years else years - 1
}

/** Days until the next birthday, 0 on the day. */
internal fun daysUntilBirthday(birthDate: LocalDate, today: LocalDate): Int =
    ChronoUnit.DAYS.between(today, nextBirthday(birthDate, today)).toInt()

/**
 * The parents, partners and children of [memberId]: the links of the tree, and the parents written
 * on the members (always there, even without the tree). Each one named with the word the reader
 * chose for them in the tree, else their first name.
 */
internal fun kinshipOf(memberId: String, family: Family, kin: List<Kin>, links: List<KinLink>, words: Map<String, String>): Kinship {
    val fromMembers = family.members.flatMap { child -> child.parentIds.map { KinLink.parent(it, child.id) } }
    val tree = GenealogyTree(family.members, kin, links + fromMembers)
    val byId = family.members.associateBy { it.id }
    fun person(id: String): KinPerson? {
        val who = tree.people[id] ?: return null
        return KinPerson(id, words[id]?.takeIf { it.isNotBlank() } ?: who.firstName, byId[id])
    }
    fun list(ids: List<String>) = ids.distinct().mapNotNull(::person).sortedBy { it.name.lowercase() }
    return Kinship(list(tree.parentsOf(memberId)), list(tree.partnersOf(memberId)), list(tree.childrenOf(memberId)))
}

/** The next events [member] comes to (« Je viens »), the nearest first, three at most. */
internal fun nextMomentsOf(events: List<Event>, answers: Map<String, List<Rsvp>>, member: Member, family: Family): List<Event> =
    events.filter { event ->
        concerns(event.target, member, family) &&
            answers[event.id].orEmpty().any { it.memberId == member.id && it.status == RsvpStatus.Yes }
    }.sortedWith(compareBy({ it.date }, { it.time })).take(MOMENTS_MAX)

/** The last good news with photos about [memberId], that [readerId] has not hidden. */
internal fun photosOf(news: List<GoodNews>, memberId: String, readerId: String): List<GoodNews> =
    news.filter { it.photoIds.isNotEmpty() && readerId !in it.hiddenByIds }
        .filter { it.forMemberId == memberId || memberId in it.mentionIds }
        .take(PHOTOS_MAX)

/** A phone as the rules take it: digits, spaces, dots, dashes, brackets and a leading +. */
internal fun isPhone(text: String): Boolean = Regex("^[+]?[0-9 .()-]{6,24}$").matches(text)

/** Who may see a phone or an address on a page: never for a child (PRODUIT-98). */
internal fun hasCoordinates(member: Member): Boolean = member.profile == MemberProfile.Adult

@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileV2ViewModel(
    private val members: MemberRepository?,
    genealogy: GenealogyRepository?,
    events: EventRepository?,
    rsvps: RsvpRepository?,
    goodNews: GoodNewsRepository?,
    private val readerId: String,
    private val memberId: String,
    private val today: LocalDate,
) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e ->
        if (e is CancellationException) throw e
        emit(value)
    }

    private val family = (members?.observeFamily() ?: emptyFlow())
    private val kin = (genealogy?.observeKin() ?: flowOf(emptyList())).orElse(emptyList())
    private val links = (genealogy?.observeLinks() ?: flowOf(emptyList())).orElse(emptyList())
    private val words = (genealogy?.observeWords(readerId) ?: flowOf(emptyMap())).orElse(emptyMap())
    private val upcoming = (events?.observeBetween(today, today.plusDays(MOMENTS_DAYS)) ?: flowOf(emptyList())).orElse(emptyList())
    private val answers = upcoming.flatMapLatest { list ->
        if (list.isEmpty() || rsvps == null) flowOf(emptyMap()) else rsvps.observeAnswersOf(memberId, list.map { it.id }).orElse(emptyMap())
    }
    private val news = (goodNews?.observeLatest(40) ?: flowOf(emptyList())).orElse(emptyList())

    private val tree = combine(kin, links, words) { k, l, w -> Triple(k, l, w) }
    private val moments = combine(upcoming, answers) { e, a -> e to a }

    val state: StateFlow<ProfileV2State?> = combine(family, tree, moments, news, combine(saving, failed) { s, f -> s to f }) { fam, t, m, n, sf ->
        val member = fam.members.firstOrNull { it.id == memberId } ?: return@combine null
        val reader = fam.members.firstOrNull { it.id == readerId }
        ProfileV2State(
            today = today,
            household = fam.households.firstOrNull { it.id == member.householdId },
            kinship = kinshipOf(memberId, fam, t.first, t.second, t.third),
            moments = nextMomentsOf(m.first, m.second, member, fam),
            photos = photosOf(n, memberId, readerId),
            managesHousehold = reader != null && (fam.ownerId == reader.id || (reader.isAdmin && reader.householdId == member.householdId)),
            saving = sf.first,
            saveFailed = sf.second,
        )
    }.orElse(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** My own phone; blank removes it. */
    fun savePhone(phone: String) {
        val repo = members ?: return
        val value = phone.trim().ifEmpty { null }
        if (value != null && !isPhone(value)) return run { failed.value = true }
        write { repo.savePhone(memberId, value) }
    }

    /** The address of the household of this page; blank removes it. */
    fun saveAddress(address: String) {
        val repo = members ?: return
        val household = state.value?.household ?: return
        val value = address.trim().take(200).ifEmpty { null }
        write { repo.saveHousehold(household.copy(address = value)) }
    }

    private fun write(block: suspend () -> Unit) {
        saving.value = true
        failed.value = false
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            } finally {
                saving.value = false
            }
        }
    }
}
