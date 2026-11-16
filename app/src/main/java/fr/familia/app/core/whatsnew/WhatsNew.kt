package fr.familia.app.core.whatsnew

import fr.familia.app.core.model.MemberProfile

/*
 * « Quoi de neuf ? » (PRD, récit 6 septies). The texts live in docs/whats-new/*.json; the
 * « APK de publication » workflow gives each file the number of the version it ships with and
 * writes the whole history in config/app.whatsNew (firebase/functions/scripts/whats-new.mjs).
 * Nothing here is written to Firestore: what a member has seen stays on the phone.
 */

/**
 * One card. [feature] is the key of the module it talks about (config/app.features): the card
 * waits while the module is off for the family. [open] names the screen of « Essayer »
 * ([WhatsNewTarget]); unknown or missing, the card has no button. A child reads [childTitle] and
 * [childBody] when there are some, and never sees an [adultsOnly] card.
 */
data class WhatsNewCard(
    val id: String,
    val title: String,
    val body: String,
    val childTitle: String? = null,
    val childBody: String? = null,
    val feature: String? = null,
    val open: String? = null,
    val adultsOnly: Boolean = false,
) {
    fun titleFor(child: Boolean): String = if (child) childTitle ?: title else title
    fun bodyFor(child: Boolean): String = if (child) childBody ?: body else body
    val target: WhatsNewTarget? get() = WhatsNewTarget.of(open)
}

/** The cards of one version, as the workflow published them. */
data class WhatsNewRelease(
    val id: String,
    val versionCode: Long,
    val versionName: String,
    val cards: List<WhatsNewCard>,
)

/** A card ready to show, with the version it came with. */
data class ShownCard(val card: WhatsNewCard, val release: WhatsNewRelease)

object WhatsNew {
    /** At most 5 cards at once, when versions were skipped (PRD). */
    const val MAX_CARDS = 5

    fun isChild(profile: MemberProfile) = profile != MemberProfile.Adult

    /**
     * Whether [card] concerns this member: its module is on for the family (the admin principal
     * sees what the family sees, not every module in test) and a child is not left out.
     */
    fun concerns(card: WhatsNewCard, child: Boolean, isOnForFamily: (String) -> Boolean): Boolean =
        (card.feature == null || isOnForFamily(card.feature)) && !(child && card.adultsOnly)

    /**
     * The cards of the sheet: those of the versions up to the installed one ([installedVersion]),
     * that concern me and that I have not [seen], the most recent first, [MAX_CARDS] at most. A
     * card of a module switched off waits: it comes once the admin switches the module on.
     */
    fun pending(
        releases: List<WhatsNewRelease>,
        installedVersion: Long,
        seen: Set<String>,
        child: Boolean,
        isOnForFamily: (String) -> Boolean,
    ): List<ShownCard> = history(releases, installedVersion, child, isOnForFamily)
        .flatMap { (release, cards) -> cards.map { ShownCard(it, release) } }
        .filter { it.card.id !in seen }
        .take(MAX_CARDS)

    /** Every version up to the installed one, the most recent first, with the cards that concern me. */
    fun history(
        releases: List<WhatsNewRelease>,
        installedVersion: Long,
        child: Boolean,
        isOnForFamily: (String) -> Boolean,
    ): List<Pair<WhatsNewRelease, List<WhatsNewCard>>> = releases
        .filter { it.versionCode <= installedVersion }
        .sortedByDescending { it.versionCode }
        .map { release -> release to release.cards.filter { concerns(it, child, isOnForFamily) } }
        .filter { (_, cards) -> cards.isNotEmpty() }

    /**
     * config/app.whatsNew as Firestore gives it (a list of maps). A version or a card that is not
     * complete is left out, so a bad file never breaks the app.
     */
    fun parse(raw: Any?): List<WhatsNewRelease> = (raw as? List<*>).orEmpty().mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = map["id"] as? String ?: return@mapNotNull null
        val versionCode = (map["versionCode"] as? Number)?.toLong() ?: return@mapNotNull null
        val cards = (map["cards"] as? List<*>).orEmpty().mapNotNull(::parseCard)
        WhatsNewRelease(id, versionCode, map["versionName"] as? String ?: "", cards)
    }

    private fun parseCard(item: Any?): WhatsNewCard? {
        val map = item as? Map<*, *> ?: return null
        val text = { key: String -> (map[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
        return WhatsNewCard(
            id = text("id") ?: return null,
            title = text("title") ?: return null,
            body = text("body") ?: return null,
            childTitle = text("childTitle"),
            childBody = text("childBody"),
            feature = text("feature"),
            open = text("open"),
            adultsOnly = map["adultsOnly"] == true,
        )
    }
}
