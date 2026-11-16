package fr.familia.app.core.whatsnew

import android.content.Context
import androidx.core.content.edit

/** What a member has seen of « Quoi de neuf ? », kept on the phone only. */
interface WhatsNewStore {
    /** The ids of the cards already shown. */
    fun seen(memberId: String): Set<String>
    fun addSeen(memberId: String, ids: Set<String>)

    /** False until the first decision on this phone: a new install or an update. */
    fun started(memberId: String): Boolean

    /** A new install whose cards are still to be put aside, once config/app is read. */
    fun freshInstall(memberId: String): Boolean
    fun setFreshInstall(memberId: String)
    fun setStarted(memberId: String)
}

class WhatsNewPreferences(context: Context) : WhatsNewStore {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    override fun seen(memberId: String): Set<String> = prefs.getStringSet("whatsNewSeen-$memberId", emptySet()).orEmpty().toSet()
    override fun addSeen(memberId: String, ids: Set<String>) = prefs.edit { putStringSet("whatsNewSeen-$memberId", seen(memberId) + ids) }
    override fun started(memberId: String): Boolean = prefs.getBoolean("whatsNewStarted-$memberId", false)
    override fun freshInstall(memberId: String): Boolean = prefs.getBoolean("whatsNewFresh-$memberId", false)
    override fun setFreshInstall(memberId: String) = prefs.edit { putBoolean("whatsNewFresh-$memberId", true) }
    override fun setStarted(memberId: String) = prefs.edit {
        putBoolean("whatsNewStarted-$memberId", true)
        remove("whatsNewFresh-$memberId")
    }
}

/**
 * When the sheet opens (PRD, récit 6 septies). On a new install the tutorial comes first: every
 * card that concerns me now is put aside, and only what comes later shows. A phone that already
 * had the app before « Quoi de neuf ? » shows the latest cards at its first opening.
 */
class WhatsNewDecider(private val store: WhatsNewStore) {

    /** Called at once on the opening that showed the tutorial, before config/app is read. */
    fun noteFreshInstall(memberId: String) {
        if (!store.started(memberId)) store.setFreshInstall(memberId)
    }

    fun cardsToShow(
        memberId: String,
        releases: List<WhatsNewRelease>,
        installedVersion: Long,
        child: Boolean,
        isOnForFamily: (String) -> Boolean,
    ): List<ShownCard> {
        if (!store.started(memberId)) {
            if (store.freshInstall(memberId)) {
                val now = WhatsNew.history(releases, installedVersion, child, isOnForFamily)
                    .flatMap { (_, cards) -> cards.map { it.id } }
                store.addSeen(memberId, now.toSet())
                store.setStarted(memberId)
                return emptyList()
            }
            store.setStarted(memberId)
        }
        return WhatsNew.pending(releases, installedVersion, store.seen(memberId), child, isOnForFamily)
    }

    /** « C'est parti », « Plus tard » or « Essayer »: these cards do not come back by themselves. */
    fun markSeen(memberId: String, cards: List<ShownCard>) = store.addSeen(memberId, cards.map { it.card.id }.toSet())
}
