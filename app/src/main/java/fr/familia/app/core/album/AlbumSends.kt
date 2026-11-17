package fr.familia.app.core.album

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The album photos being sent from this phone, kept for the whole app and not for one screen:
 * a member who chose 30 photos may leave the album, the photos go on (the evening of a party,
 * from a small phone). Android may still stop the app: the album then picks up what is left.
 */
class AlbumSends(private val scope: CoroutineScope) {

    /** Where the photos of one album are: [done] of [total] sent, in the places [slots]. */
    data class Progress(
        val total: Int = 0,
        val done: Int = 0,
        /** Places taken by photos still being sent, so two quick choices never take the same one. */
        val slots: Set<Int> = emptySet(),
        val problem: PhotoProblem? = null,
    )

    private val all = MutableStateFlow<Map<String, Progress>>(emptyMap())

    fun progress(folder: String): Flow<Progress> = all.map { it[folder] ?: Progress() }.distinctUntilChanged()

    fun current(folder: String): Progress = all.value[folder] ?: Progress()

    /** [slots] are being sent: the bar counts them. */
    fun start(folder: String, slots: List<Int>) = edit(folder) { it.copy(total = it.total + slots.size, slots = it.slots + slots) }

    /** The photo of [slot] is sent, or failed: the bar moves on, and starts again at 0 once all are done. */
    fun finish(folder: String, slot: Int) = edit(folder) {
        val left = it.slots - slot
        if (left.isEmpty()) it.copy(total = 0, done = 0, slots = left) else it.copy(done = it.done + 1, slots = left)
    }

    fun setProblem(folder: String, problem: PhotoProblem?) = edit(folder) { it.copy(problem = problem) }

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    private fun edit(folder: String, change: (Progress) -> Progress) =
        all.update { it + (folder to change(it[folder] ?: Progress())) }

    companion object {
        /**
         * The one of the app: its sends outlive every screen. On the main thread, like the screen,
         * so the loop and the screen never write the list of photos to send at the same time (the
         * reduction of each photo and the upload still run off the main thread).
         */
        val shared: AlbumSends by lazy { AlbumSends(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    }
}
