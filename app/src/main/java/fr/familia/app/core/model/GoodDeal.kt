package fr.familia.app.core.model

import fr.familia.app.core.photo.SinglePhoto
import java.time.Instant

/** A bon plan (éphémère) or a bonne adresse (durable), PRD récit 45. */
enum class GoodDealKind(val firestoreValue: String) {
    Deal("deal"),
    Address("address"),
    ;

    companion object {
        fun fromFirestore(value: String?): GoodDealKind? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/** The category of a bonne adresse, in the order the app lists them. */
enum class GoodDealCategory(val firestoreValue: String) {
    Health("health"),
    Home("home"),
    Car("car"),
    Kids("kids"),
    Outings("outings"),
    Other("other"),
    ;

    companion object {
        fun fromFirestore(value: String?): GoodDealCategory? = entries.firstOrNull { it.firestoreValue == value }
    }
}

/**
 * A post of the « Bons plans » of Entraide (PRD, récit 45). A [GoodDealKind.Deal] has an [endsAt]
 * and may have a [photo] (the Storage base, see SinglePhoto); a [GoodDealKind.Address] has a
 * [category] and may have a [phone]. [place] is the place of a deal or the town of an address.
 */
data class GoodDeal(
    val id: String,
    val kind: GoodDealKind,
    val title: String,
    val text: String = "",
    val link: String? = null,
    val place: String? = null,
    val phone: String? = null,
    val category: GoodDealCategory? = null,
    val endsAt: Instant? = null,
    val photo: String? = null,
    val interestedIds: List<String> = emptyList(),
    val recommendedIds: List<String> = emptyList(),
    val authorId: String,
    val authorName: String,
    val createdAt: Instant? = null,
) {
    /** A bon plan leaves the list at its end; an address never does. */
    fun isActive(now: Instant): Boolean = kind == GoodDealKind.Address || (endsAt != null && endsAt.isAfter(now))
}

/** Where the photo of a bon plan goes in Storage: goodDeals/{dealId}/{memberId}/{photoId}. */
fun goodDealPhotoBase(dealId: String, memberId: String, photoId: String = SinglePhoto.newId()): String =
    "goodDeals/$dealId/$memberId/$photoId"

/** The site of a link, as the list shows it: "exemple.fr" for "https://www.exemple.fr/promo". */
fun linkSiteName(link: String): String =
    link.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.").ifBlank { link }
