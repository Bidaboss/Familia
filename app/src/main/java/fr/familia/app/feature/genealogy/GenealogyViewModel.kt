package fr.familia.app.feature.genealogy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.data.KinPhotoChange
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.KinLinkKind
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.model.isOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the other person is joined to the one whose card is open. */
enum class Relation {
    Parent, Child, Partner;

    /** The link that makes [otherId] the relation of [personId]. */
    fun link(personId: String, otherId: String): KinLink = when (this) {
        Parent -> KinLink.parent(otherId, personId)
        Child -> KinLink.parent(personId, otherId)
        Partner -> KinLink.partners(personId, otherId)
    }
}

sealed interface GenealogyUiState {
    data object Loading : GenealogyUiState
    data object Failed : GenealogyUiState

    /**
     * [words]: the words I chose for the people of the tree, by person id. [isOwner] edits the
     * whole tree; [chefOf] is the household I am chef of, whose kin and links I edit.
     */
    data class Ready(
        val tree: GenealogyTree,
        val words: Map<String, String>,
        val me: String,
        val isOwner: Boolean = false,
        val chefOf: String? = null,
        /** The name of each household, to tell two people of the same first name apart. */
        val householdNames: Map<String, String> = emptyMap(),
    ) : GenealogyUiState {
        /** Nobody joined to anyone, and no relative yet: the screen says how the tree fills up. */
        val isEmpty: Boolean get() = tree.links.isEmpty() && tree.people.values.none { it is TreePerson.OfKin }

        /** I may add relatives and links at all. */
        val canEdit: Boolean get() = isOwner || chefOf != null

        /** I may edit [personId]'s card (a relative) or join others to them. */
        fun canEditPerson(personId: String): Boolean =
            isOwner || (chefOf != null && tree.people[personId]?.householdId == chefOf)

        /**
         * The rules' managesLink (PRD rév. 195): partners, when one side is in a household I
         * manage; a parent, only when the child is.
         */
        fun canEditLink(link: KinLink): Boolean = when (link.kind) {
            KinLinkKind.Partner -> canEditPerson(link.aId) || canEditPerson(link.bId)
            KinLinkKind.Parent -> canEditPerson(link.bId)
        }

        /**
         * Whom « Relier deux membres » offers first: the people I may edit, by household (mine
         * first, then the others together), by first name inside each one.
         */
        val joinable: List<TreePerson>
            get() {
                val mine = tree.people[me]?.householdId
                return tree.people.values
                    .filter { canEditPerson(it.id) }
                    .sortedWith(compareBy<TreePerson>({ it.householdId != mine }, { it.householdId }, { it.firstName.lowercase() }))
            }

        /** I may make [otherId] the [relation] of [personId]: what the link dialog offers. */
        fun canJoin(personId: String, relation: Relation, otherId: String): Boolean =
            canEditLink(relation.link(personId, otherId))
    }
}

/**
 * L'arbre généalogique (PRD, récit 40): everyone reads it and chooses their own words; the admin
 * principal edits it all, and a chef de famille the relatives and links of their household. The
 * rules check the same (firestore.rules, « Arbre généalogique »).
 */
class GenealogyViewModel(
    private val genealogy: GenealogyRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
) : ViewModel() {

    private var family = Family(emptyList(), emptyList())

    val uiState: StateFlow<GenealogyUiState> =
        if (genealogy == null || members == null) {
            MutableStateFlow(GenealogyUiState.Ready(GenealogyTree(emptyList(), emptyList(), emptyList()), emptyMap(), currentMemberId))
        } else {
            combine(
                members.observeFamily(),
                genealogy.observeKin(),
                genealogy.observeLinks(),
                // My words are a nicety: without them, the tree still shows.
                genealogy.observeWords(currentMemberId).onStart { emit(emptyMap()) }.catch { e -> if (e is CancellationException) throw e else emit(emptyMap()) },
            ) { family, kin, links, words ->
                this.family = family
                val me = family.members.firstOrNull { it.id == currentMemberId }
                val ready: GenealogyUiState = GenealogyUiState.Ready(
                    tree = GenealogyTree(family.members, kin, links),
                    words = words,
                    me = currentMemberId,
                    isOwner = me != null && family.isOwner(me),
                    chefOf = me?.takeIf { it.isAdmin }?.householdId,
                    householdNames = family.households.associate { it.id to it.name },
                )
                ready
            }
                .catch { e -> if (e is CancellationException) throw e else emit(GenealogyUiState.Failed) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenealogyUiState.Loading)
        }

    private val _actionFailed = MutableStateFlow(false)

    /** True when the server refused a change: the screen says so once. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()

    fun failureShown() {
        _actionFailed.value = false
    }

    private val ready get() = uiState.value as? GenealogyUiState.Ready

    /** Joins [otherId] to [personId]; refused here when the rules would refuse it. */
    fun addLink(personId: String, relation: Relation, otherId: String) {
        val state = ready ?: return
        if (personId == otherId || otherId !in state.tree.people) return
        val link = relation.link(personId, otherId)
        if (!state.canEditLink(link) || state.tree.links.any { it.id == link.id }) return
        // A parent and a child the other way round would loop: refused.
        if (link.kind == KinLinkKind.Parent && state.tree.links.any { it.id == KinLink.parent(link.bId, link.aId).id }) return
        write { addLink(link, currentMemberId) }
    }

    fun removeLink(link: KinLink) {
        val state = ready ?: return
        if (!state.canEditLink(link)) return
        write { removeLink(link) }
    }

    /**
     * Adds a relative, or edits one ([kin] with an existing id). A new one goes to the household
     * of the person they are joined to when I manage it, else to mine; with [joinTo], the link
     * goes in the same batch, and so does [photo] (récit 79).
     */
    fun saveKin(kin: Kin, joinTo: Pair<String, Relation>? = null, photo: KinPhotoChange? = null) {
        val state = ready ?: return
        val firstName = kin.firstName.trim()
        if (firstName.isEmpty()) return
        val existing = state.tree.people[kin.id]
        val household = when {
            existing != null -> existing.householdId
            joinTo != null && state.canEditPerson(joinTo.first) -> state.tree.people[joinTo.first]?.householdId
            else -> state.chefOf ?: family.members.firstOrNull { it.id == currentMemberId }?.householdId
        } ?: return
        if (!state.isOwner && household != state.chefOf) return
        if (existing != null && !state.canEditPerson(kin.id)) return
        // A new parent for a child of a household I do not manage: the rules would refuse the batch.
        if (existing == null && joinTo?.second == Relation.Parent && !state.canEditPerson(joinTo.first)) return
        val id = if (existing != null) kin.id else genealogy?.newKinId() ?: return
        val saved = kin.copy(id = id, firstName = firstName, householdId = household, died = kin.died.takeUnless { kin.living })
        val link = joinTo?.takeIf { existing == null }?.let { (personId, relation) -> relation.link(personId, id) }
        // Nothing to take away from someone new.
        val change = photo.takeUnless { existing == null && it == KinPhotoChange.Remove }
        write { saveKin(saved, currentMemberId, link, change) }
    }

    /** Takes a relative out of the tree; the server removes their links. */
    fun deleteKin(id: String) {
        val state = ready ?: return
        if (state.tree.people[id] !is TreePerson.OfKin || !state.canEditPerson(id)) return
        write { deleteKin(id) }
    }

    /** My word for [personId]; blank takes it away. */
    fun setWord(personId: String, word: String) {
        val state = ready ?: return
        if (personId == currentMemberId || personId !in state.tree.people) return
        val trimmed = word.trim().take(MAX_WORD)
        val words = (state.words - personId).let { if (trimmed.isEmpty()) it else it + (personId to trimmed) }
            .filterKeys { it in state.tree.people }
        if (words == state.words) return
        write { saveWords(currentMemberId, words) }
    }

    private fun write(block: suspend GenealogyRepository.() -> Unit) {
        val repo = genealogy ?: return
        viewModelScope.launch {
            try {
                repo.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.value = true
            }
        }
    }

    companion object {
        const val MAX_WORD = 30
    }
}

/**
 * The line of [person] in a list to pick from (retour d'essai du 17/11, deux homonymes) : the first
 * name alone when nobody else in [among] has it, else with what tells them apart. A relative: their
 * last name, else their years, else « en souvenir » ([remembered]) or « sans compte » ([noAccount]).
 * A member: the name of their household (a member has no last name).
 */
internal fun pickLabel(
    person: TreePerson,
    among: Collection<TreePerson>,
    householdNames: Map<String, String>,
    remembered: String,
    noAccount: String,
): String {
    val same = among.count { it.firstName.trim().equals(person.firstName.trim(), ignoreCase = true) }
    if (same <= 1) return person.firstName
    val detail = when (person) {
        is TreePerson.OfKin -> person.kin.lastName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(person.kin.born?.take(4), person.kin.died?.take(4)).joinToString("–").takeIf { it.isNotEmpty() }
            ?: if (person.kin.living) noAccount else remembered
        is TreePerson.OfMember -> householdNames[person.householdId]
    }
    return if (detail == null) person.firstName else "${person.firstName} · $detail"
}
