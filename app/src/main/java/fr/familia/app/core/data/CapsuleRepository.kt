package fr.familia.app.core.data

import fr.familia.app.core.model.Capsule
import kotlinx.coroutines.flow.Flow

/** The time capsules (PRD, récit 39). See docs/data-model.md. */
interface CapsuleRepository {
    /** The capsules [memberId] wrote, sealed or opened, updated live. */
    fun observeWrittenBy(memberId: String): Flow<List<Capsule>>

    /** The capsules already opened for [memberId], updated live. The sealed ones never show. */
    fun observeOpenedFor(memberId: String): Flow<List<Capsule>>

    /** One capsule; null once removed, or when it cannot be read (still sealed for someone else). */
    fun observeCapsule(id: String): Flow<Capsule?>

    suspend fun deleteCapsule(id: String)
}

/** The next to open first; then the last opened first. */
val sealedCapsuleOrder: Comparator<Capsule> = compareBy<Capsule> { it.openAt }.thenBy { it.id }
val openedCapsuleOrder: Comparator<Capsule> = compareByDescending<Capsule> { it.openAt }.thenBy { it.id }
