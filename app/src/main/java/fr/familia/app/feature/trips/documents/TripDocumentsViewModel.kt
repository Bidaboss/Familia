package fr.familia.app.feature.trips.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TripDocument
import fr.familia.app.core.data.TripDocumentKind
import fr.familia.app.core.data.TripDocumentRepository
import fr.familia.app.core.data.TripDocumentType
import fr.familia.app.core.data.TripDocuments
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.PickGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The list of the screen: being read, read, or out of reach. */
sealed interface DocumentsState {
    data object Loading : DocumentsState
    data class Ready(val documents: List<TripDocument>) : DocumentsState
    data object Failed : DocumentsState
}

/** What went wrong last, said under the list; null when all is well. */
enum class DocumentProblem { TooBig, WrongType, AddFailed, OpenFailed, NoViewer, DeleteFailed, ShareFailed }

/** A file picked on the phone, checked before anything is sent. */
sealed interface PickedFile {
    class Ok(val type: TripDocumentType, val bytes: ByteArray, val title: String) : PickedFile
    data class Refused(val problem: DocumentProblem) : PickedFile
}

/**
 * « Documents » of a trip (récit 105, règle 41): tickets, bookings and papers, read only by those
 * who go. Each one adds their own; the owner, the creator of the trip or the admin principal
 * removes one.
 */
class TripDocumentsViewModel(
    private val repository: TripDocumentRepository?,
    private val tripId: String,
    private val me: Member,
) : ViewModel() {
    private val _state = MutableStateFlow<DocumentsState>(if (repository == null) DocumentsState.Failed else DocumentsState.Loading)
    val state: StateFlow<DocumentsState> = _state.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _problem = MutableStateFlow<DocumentProblem?>(null)
    val problem: StateFlow<DocumentProblem?> = _problem.asStateFlow()

    init {
        repository?.let { repo ->
            viewModelScope.launch {
                repo.observe(tripId, me.id)
                    .catch { _state.value = DocumentsState.Failed }
                    .collect { _state.value = DocumentsState.Ready(it) }
            }
        }
    }

    fun refuse(problem: DocumentProblem) {
        _problem.value = problem
    }

    fun dismissProblem() {
        _problem.value = null
    }

    /** Sends [file] under [title], as a [kind], read by me and [others] (none: private, DESIGN-105). */
    fun add(file: PickedFile.Ok, title: String, kind: TripDocumentKind, others: Set<String> = emptySet()) {
        val repo = repository ?: return
        if (_sending.value || title.isBlank()) return
        _problem.value = null
        _sending.value = true
        viewModelScope.launch {
            try {
                repo.add(tripId, me, title, kind, file.type, file.bytes, sharedList(me.id, others))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _problem.value = DocumentProblem.AddFailed
            } finally {
                _sending.value = false
            }
        }
    }

    /** The bytes of [document], to open it; null (and said) when it cannot be read. */
    suspend fun bytes(document: TripDocument): ByteArray? {
        _problem.value = null
        val bytes = repository?.bytes(tripId, document)
        if (bytes == null) _problem.value = DocumentProblem.OpenFailed
        return bytes
    }

    /** « Partager… »: [document], mine, is read by me and [others] from now on. */
    fun share(document: TripDocument, others: Set<String>) {
        val repo = repository ?: return
        if (document.ownerId != me.id) return
        _problem.value = null
        viewModelScope.launch {
            try {
                repo.share(tripId, document, sharedList(me.id, others))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _problem.value = DocumentProblem.ShareFailed
            }
        }
    }

    fun delete(document: TripDocument) {
        val repo = repository ?: return
        _problem.value = null
        viewModelScope.launch {
            try {
                repo.delete(tripId, document)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _problem.value = DocumentProblem.DeleteFailed
            }
        }
    }
}

/**
 * Checks a picked file: a PDF, a JPEG or a PNG of 5 MB at most. [bytes] is read up to one byte over
 * the limit, so a bigger file is refused without being read whole. The title comes from its name.
 */
fun checkPicked(contentType: String?, displayName: String?, bytes: ByteArray?): PickedFile {
    val type = TripDocumentType.of(contentType) ?: return PickedFile.Refused(DocumentProblem.WrongType)
    if (bytes == null || bytes.isEmpty()) return PickedFile.Refused(DocumentProblem.AddFailed)
    if (bytes.size > TripDocuments.MAX_BYTES) return PickedFile.Refused(DocumentProblem.TooBig)
    return PickedFile.Ok(type, bytes, suggestedTitle(displayName))
}

/** « billet-aller.pdf » gives « billet-aller », cut to the length of a title. */
fun suggestedTitle(displayName: String?): String =
    displayName.orEmpty().substringBeforeLast('.').trim().take(TripDocuments.TITLE_MAX)

/** The owner, the creator of the trip or the admin principal ([canManage]) removes a document. */
fun canDeleteDocument(document: TripDocument, me: Member, canManage: Boolean): Boolean =
    document.ownerId == me.id || canManage

/** Who reads a document: its owner first, then [others] (the owner never twice), in a stable order. */
fun sharedList(ownerId: String, others: Set<String>): List<String> = listOf(ownerId) + others.filter { it != ownerId }.sorted()

/**
 * Those [document] is shared with, among the travellers [going], its owner aside: a traveller who left
 * the trip is dropped, so a new choice never names them (the rules refuse it).
 */
fun sharedOthers(document: TripDocument, going: Collection<String>): Set<String> =
    document.sharedWith.filter { it != document.ownerId && it in going }.toSet()

/**
 * The travellers I can share with, by household (règle du 17/11): mine first, then the others
 * by name; in each, by first name. Me aside: I always read my documents. Unknown household last, in a
 * group with a blank name.
 */
fun travellerGroups(people: List<Member>, households: List<Household>, me: Member): List<PickGroup> {
    val others = people.filter { it.id != me.id }
    val byName = compareBy<Member> { it.firstName.lowercase() }
    val ordered = households.sortedWith(compareBy({ it.id != me.householdId }, { it.name.lowercase() }))
    val groups = ordered.mapNotNull { household ->
        others.filter { it.householdId == household.id }.sortedWith(byName).takeIf { it.isNotEmpty() }?.let { PickGroup(household.name, it) }
    }
    val known = ordered.map { it.id }.toSet()
    val rest = others.filter { it.householdId !in known }.sortedWith(byName)
    return if (rest.isEmpty()) groups else groups + PickGroup("", rest)
}
