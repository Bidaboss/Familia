package fr.familia.app.core.model

import fr.familia.app.core.photo.SinglePhoto
import java.time.Instant

/** The category of a thing to lend (PRD, récit 51), in the order the app lists them. */
enum class LendCategory(val firestoreValue: String) {
    Kitchen("kitchen"),
    Diy("diy"),
    Baby("baby"),
    Sport("sport"),
    Travel("travel"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): LendCategory? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** Where a thing is: free, asked by someone, or lent to them. */
enum class LendStatus(val firestoreValue: String) {
    Available("available"),
    Asked("asked"),
    Lent("lent"),
    ;

    companion object {
        fun fromFirestore(value: String?): LendStatus = entries.firstOrNull { it.firestoreValue == value } ?: Available
    }
}

/**
 * A thing an adult is happy to lend to the family (« On se prête », PRD récit 51). While it is
 * [LendStatus.Asked] or [LendStatus.Lent], [borrowerId] is the one who asked or has it, with the
 * optional dates [from] and [until] of the ask. No money anywhere.
 */
data class LendItem(
    val id: String,
    val name: String,
    val note: String = "",
    val category: LendCategory = LendCategory.Other,
    val photo: String? = null,
    val ownerId: String,
    val ownerName: String,
    val status: LendStatus = LendStatus.Available,
    val borrowerId: String? = null,
    val borrowerName: String? = null,
    val from: Instant? = null,
    val until: Instant? = null,
    val lentAt: Instant? = null,
    val createdAt: Instant? = null,
)

/** Where the photo of a thing goes in Storage: lendItems/{itemId}/{memberId}/{photoId}. */
fun lendPhotoBase(itemId: String, memberId: String, photoId: String = SinglePhoto.newId()): String =
    "lendItems/$itemId/$memberId/$photoId"
