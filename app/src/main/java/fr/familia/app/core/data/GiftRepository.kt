package fr.familia.app.core.data

import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftContribution
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.ReceivedGift
import fr.familia.app.core.model.SharedWish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Access to the gift lists (PRD, récits 7 to 10 bis). The security rules keep the surprise: each
 * query here is one the rules accept for [memberId], so a beneficiary never asks for what is hidden.
 */
interface GiftRepository {
    /** Every list [memberId] may see: theirs, the ones they are a viewer of, all « Mes envies », and their revealed surprises. */
    fun observeVisibleLists(memberId: String): Flow<List<GiftList>>

    /** One list, live; null once it is deleted or not visible. */
    fun observeList(listId: String): Flow<GiftList?>

    /** The items [memberId] may read: only their own side's items when the list is [hiddenFromMe]. */
    fun observeItems(listId: String, hiddenFromMe: Boolean): Flow<List<GiftItem>>

    /** Reservations of the list. Never called for its beneficiary before the reveal. */
    fun observeClaims(listId: String): Flow<List<GiftClaim>>

    /**
     * The reservations of [itemIds] only, read one by one (récit 73): what whoever shares a wish of
     * the list may read. A reservation they may not read counts as none.
     */
    fun observeClaimsOf(listId: String, itemIds: List<String>): Flow<List<GiftClaim>> =
        observeClaims(listId).map { claims -> claims.filter { it.itemId in itemIds } }

    /** The wishes of [listIds] shared with [memberId] (récit 73), for their own list. */
    fun observeWishesSharedWith(memberId: String, listIds: List<String>): Flow<List<SharedWish>> = flowOf(emptyList())

    /** Parts of the joint gifts of the list, by item id. Same restriction as [observeClaims]. */
    /**
     * The parts of the joint gifts [itemIds]: all of them for the items in [allOf] (those the member
     * is responsible for), only the member's own part for the others. The rules refuse the rest.
     */
    fun observeShares(listId: String, itemIds: List<String>, memberId: String, allOf: Set<String>): Flow<Map<String, List<GiftShare>>>

    fun newListId(): String

    fun newItemId(listId: String): String

    /** Creates the list, or updates its title, occasion, date and viewers when [isNew] is false. */
    suspend fun saveList(list: GiftList, isNew: Boolean)

    /** Removes a list; its items, reservations and parts are removed on the server. */
    suspend fun deleteList(listId: String)

    /** Creates the item, or updates it when [isNew] is false (author and visibility never change). */
    suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean)

    suspend fun deleteItem(listId: String, itemId: String)

    /**
     * « Reçue » (récit 10 bis, rév. 232): [item] of « Mes envies » leaves the list for « On m'a
     * offert », without a giver, in one write. A reservation of it is cancelled by the server.
     */
    suspend fun markWishReceived(listId: String, item: GiftItem) = deleteItem(listId, item.id)

    /** Reserves an item for [holderId] (« Je le prends », or opening a joint gift). */
    suspend fun claim(listId: String, claim: GiftClaim)

    /** Releases a reservation; the parts of a joint gift are removed on the server. */
    suspend fun release(listId: String, itemId: String)

    /** Joins a joint gift, or changes one's part. */
    suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean)

    /** The responsible marks a part received, or not. */
    suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean)

    suspend fun leaveShare(listId: String, itemId: String, memberId: String)

    /**
     * On « Mes envies », the holder of [itemId] says which occasion the gift is for: the next
     * birthday or the next Christmas of the beneficiary (PRD rév. 186). It is revealed the day after.
     */
    suspend fun setClaimOccasion(listId: String, itemId: String, occasion: GiftOccasion) {}

    /**
     * The parts of the wishes already bought [itemIds] (récit 68): all of them when [all] (whoever
     * keeps the lists of the person), else only the part of [memberId]. The rules refuse the rest.
     */
    fun observeContributions(listId: String, itemIds: List<String>, memberId: String, all: Boolean): Flow<Map<String, List<GiftContribution>>> =
        flowOf(emptyMap())

    /** « Je participe »: sets the part of [contribution]'s member, [previous] being what it was (0 for none). */
    suspend fun setContribution(listId: String, itemId: String, contribution: GiftContribution, previous: Int) {}

    /** Takes back the part [previous] of [memberId]. */
    suspend fun removeContribution(listId: String, itemId: String, memberId: String, previous: Int) {}

    /** « On m'a offert » of the wishes [listId] (récit 10 bis). Only its owner and who keeps their lists read it. */
    fun observeReceived(listId: String): Flow<List<ReceivedGift>> = flowOf(emptyList())

    /** « Merci »: the owner of the wishes thanks the givers of [itemId]. */
    suspend fun thank(listId: String, itemId: String) {}
}
