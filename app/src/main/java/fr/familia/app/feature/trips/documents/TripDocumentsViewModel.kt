package fr.familia.app.feature.trips.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TripDocument
import fr.familia.app.core.data.TripDocumentKind
import fr.familia.app.core.data.TripDocumentRepository
import fr.familia.app.core.data.TripDocumentType
import fr.familia.app.core.data.TripDocuments
import fr.familia.app.core.model.Member
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
enum class DocumentProblem { TooBig, WrongType, AddFailed, OpenFailed, NoViewer, DeleteFailed }

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
                repo.observe(tripId)
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

    /** Sends [file] under [title], as a [kind]. */
    fun add(file: PickedFile.Ok, title: String, kind: TripDocumentKind) {
        val repo = repository ?: return
        if (_sending.value || title.isBlank()) return
        _problem.value = null
        _sending.value = true
        viewModelScope.launch {
            try {
                repo.add(tripId, me, title, kind, file.type, file.bytes)
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
