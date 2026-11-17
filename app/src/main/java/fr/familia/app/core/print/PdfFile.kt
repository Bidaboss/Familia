package fr.familia.app.core.print

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import java.io.File
import java.io.IOException

/*
 * Writing a PDF of Familia (livre photo, livre de recettes, affiche de l'arbre) without leaving a
 * half-written file in the cache: the pages go to a « .part » file, renamed once complete. A
 * phone short of room or of memory is said so, in the words of the other messages (#315).
 */

/** Why a PDF could not be made, when it is not the network. */
enum class PrintProblem {
    /** The phone has no room left for the file. */
    NoRoom,

    /** The phone has not enough memory for so many photos. */
    TooBig,
}

class PrintException(val problem: PrintProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/** The end of a file still being written: removed when the writing stops, for any reason. */
private const val PART = ".part"

/** Room kept free on top of the file, so the phone itself never runs out. */
private const val MARGIN_BYTES = 20L * 1024 * 1024

/** About what a page of photos weighs in the PDF, to ask for room before starting. */
const val PRINT_BYTES_PER_PHOTO = 500L * 1024

/**
 * Writes [target] with [write], through a « .part » file renamed at the end: an app stopped by
 * Android, a member who leaves the screen or a failure never leaves a broken PDF to share. Asks
 * for [neededBytes] of room first. Throws [PrintException] when the phone has no room or memory.
 */
suspend fun writePdfSafely(
    target: File,
    neededBytes: Long,
    freeSpace: (File) -> Long = { it.usableSpace },
    write: suspend (File) -> Unit,
): File {
    val folder = target.absoluteFile.parentFile ?: throw IOException("No folder for $target")
    folder.mkdirs()
    // What an earlier writing left when Android stopped the app in the middle.
    folder.listFiles { f -> f.name.endsWith(PART) }?.forEach { it.delete() }
    if (freeSpace(folder) < neededBytes + MARGIN_BYTES) throw PrintException(PrintProblem.NoRoom)
    val part = File(folder, target.name + PART)
    try {
        write(part)
        target.delete()
        if (!part.renameTo(target)) throw IOException("Cannot rename $part")
        return target
    } catch (e: OutOfMemoryError) {
        throw PrintException(PrintProblem.TooBig, e)
    } catch (e: PrintException) {
        throw e
    } catch (e: IOException) {
        // The disk filled up while writing: say so, rather than blame the connection.
        if (freeSpace(folder) < MARGIN_BYTES) throw PrintException(PrintProblem.NoRoom, e)
        throw e
    } finally {
        part.delete()
    }
}

/** What to say when a PDF failed: the room or the memory of the phone, else [otherwise] (the connection). */
@Composable
fun printFailedText(problem: PrintProblem?, @StringRes otherwise: Int): String = stringResource(
    when (problem) {
        PrintProblem.NoRoom -> R.string.print_failed_no_room
        PrintProblem.TooBig -> R.string.print_failed_too_big
        null -> otherwise
    },
)
