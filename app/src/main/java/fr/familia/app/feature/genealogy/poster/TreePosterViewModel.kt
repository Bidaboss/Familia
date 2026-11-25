package fr.familia.app.feature.genealogy.poster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GenealogyRepository
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.print.PrintException
import fr.familia.app.core.print.PrintProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.Collator
import java.util.Locale

/** Someone the poster may start from, as the list shows them. */
data class PosterChoice(val id: String, val firstName: String)

/** The people who may start the poster, under the name of their household (règle de l'admin, 17/11). */
data class PosterChoiceGroup(val name: String, val choices: List<PosterChoice>)

sealed interface TreePosterUiState {
    data object Loading : TreePosterUiState

    /** Nobody joined to anyone yet, or the tree could not be read. */
    data object Empty : TreePosterUiState

    data class Choosing(
        val tree: GenealogyTree,
        /** The people with descendants, by household (mine first), then by first name: they may start the poster. */
        val starts: List<PosterChoiceGroup>,
        val title: String,
        val year: Int,
        val start: PosterStart = PosterStart.Everyone,
        val withPhotos: Boolean = true,
        val format: PosterFormat = PosterFormat.A4,
        val isMaking: Boolean = false,
        val failed: Boolean = false,
        /** The room or the memory of the phone, when that is why it failed; else the connection. */
        val problem: PrintProblem? = null,
    ) : TreePosterUiState {
        val canMake: Boolean get() = title.isNotBlank() && !isMaking
    }
}

/**
 * L'affiche de l'arbre (récit 53): reads the tree and the profile photos once, lets the member pick
 * the title, the start, the photos and the size, then [print] writes the PDF.
 */
class TreePosterViewModel(
    private val genealogy: GenealogyRepository?,
    private val members: MemberRepository?,
    private val photos: MemberPhotoRepository?,
    private val currentMemberId: String,
    private val defaultTitle: String,
    private val year: Int,
    /** Writes the poster, with the photo of a member by id, and gives its file, or null when it failed. */
    private val print: suspend (PosterContent, (String) -> ByteArray?) -> File?,
    private val readTimeoutMillis: Long = 10_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TreePosterUiState>(TreePosterUiState.Loading)
    val uiState: StateFlow<TreePosterUiState> = _uiState.asStateFlow()

    private val _made = MutableStateFlow<File?>(null)

    /** The poster just made, for the share sheet; [shared] clears it. */
    val made: StateFlow<File?> = _made.asStateFlow()

    private var photoBytes: Map<String, ByteArray> = emptyMap()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val genealogyRepo = genealogy
        val memberRepo = members
        val loaded = if (genealogyRepo == null || memberRepo == null) null else try {
            withTimeoutOrNull(readTimeoutMillis) {
                val family = memberRepo.observeFamily().first()
                family to GenealogyTree(family.members, genealogyRepo.observeKin().first(), genealogyRepo.observeLinks().first())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        photoBytes = readPhotos()
        val (family, tree) = loaded ?: (null to null)
        _uiState.value = if (family == null || tree == null || tree.links.isEmpty()) {
            TreePosterUiState.Empty
        } else {
            val collator = Collator.getInstance(Locale.FRENCH)
            val mine = family.members.firstOrNull { it.id == currentMemberId }?.householdId
            val parents = tree.people.values.filter { tree.childrenOf(it.id).isNotEmpty() }.groupBy { it.householdId }
            val starts = family.households.sortedBy { if (it.id == mine) 0 else 1 }.mapNotNull { household ->
                parents[household.id]?.let { people ->
                    PosterChoiceGroup(household.name, people.map { PosterChoice(it.id, it.firstName) }.sortedWith(compareBy(collator) { it.firstName }))
                }
            }
            // Someone of a household that is gone stays on the poster, at the end of the list.
            val known = family.households.map { it.id }.toSet()
            val lost = parents.filterKeys { it !in known }.values.flatten().map { PosterChoice(it.id, it.firstName) }.sortedWith(compareBy(collator) { it.firstName })
            TreePosterUiState.Choosing(tree, if (lost.isEmpty()) starts else starts + PosterChoiceGroup("", lost), defaultTitle, year)
        }
    }

    /** No photo when they cannot be read: the poster still has its initials. */
    private suspend fun readPhotos(): Map<String, ByteArray> = try {
        withTimeoutOrNull(readTimeoutMillis) { photos?.observePhotos()?.first() }.orEmpty().mapValues { it.value.jpeg }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun change(transform: (TreePosterUiState.Choosing) -> TreePosterUiState.Choosing) {
        val state = _uiState.value as? TreePosterUiState.Choosing ?: return
        if (state.isMaking) return
        _uiState.value = transform(state).copy(failed = false, problem = null)
    }

    fun setTitle(title: String) = change { it.copy(title = title.take(POSTER_TITLE_MAX)) }

    fun setStart(start: PosterStart) = change { it.copy(start = start) }

    fun setWithPhotos(withPhotos: Boolean) = change { it.copy(withPhotos = withPhotos) }

    fun setFormat(format: PosterFormat) = change { it.copy(format = format) }

    fun make() {
        val state = _uiState.value as? TreePosterUiState.Choosing ?: return
        if (!state.canMake) return
        val bytes = photoBytes
        val content = posterContent(
            tree = state.tree,
            start = state.start,
            makerId = currentMemberId,
            withPhotos = state.withPhotos,
            hasPhoto = { it in bytes },
            title = state.title.trim(),
            year = state.year,
            format = state.format,
        )
        _uiState.value = state.copy(isMaking = true, failed = false, problem = null)
        viewModelScope.launch {
            var problem: PrintProblem? = null
            val file = try {
                print(content) { bytes[it] }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintException) {
                problem = e.problem
                null
            } catch (e: Exception) {
                null
            }
            (_uiState.value as? TreePosterUiState.Choosing)?.let { _uiState.value = it.copy(isMaking = false, failed = file == null, problem = problem) }
            _made.value = file
        }
    }

    fun shared() {
        _made.value = null
    }
}
