package fr.familia.app.core.data

import kotlinx.coroutines.flow.Flow

/**
 * A profile photo (PRD, récit 3 bis): a square JPEG of about 100 KB made by the app.
 * [version] changes each time the photo is replaced, so a decoded copy can be reused until then.
 */
class MemberPhoto(val memberId: String, val jpeg: ByteArray, val version: Long)

/** Profile photos of the family. Screens never talk to Firestore directly. */
interface MemberPhotoRepository {
    /** Every photo, by member id, updated live and served from the offline cache. */
    fun observePhotos(): Flow<Map<String, MemberPhoto>>

    /** The member, an adult of their household for a child, or the admin principal (the rules check it). */
    suspend fun savePhoto(memberId: String, jpeg: ByteArray)

    /** Same rights as [savePhoto]. Without a photo the avatar shows the initial again. */
    suspend fun removePhoto(memberId: String)
}
