package fr.familia.app.core.model

/**
 * The families of notifications (PRD, récit 6 quinquies), each with its key in
 * members/{id}/settings/notifications and the same list as functions/src/prefs.ts.
 */
enum class NotificationFamily(val key: String) {
    Planning("planning"),
    Reminders("reminders"),
    Gifts("gifts"),
    GoodNews("goodNews"),
    Comments("comments"),
    HelpRequests("helpRequests"),
    Trips("trips"),

    /** « Rendez-vous de la semaine »: souvenirs, défi, récap du dimanche, question, vœux. */
    Weekly("weekly"),
}

/**
 * The small news that can wait for the résumé du soir (récit 70), each with its key in the settings
 * document and the same list as functions/src/prefs.ts. `false` keeps the kind immediate.
 */
enum class EveningKind(val key: String, val family: NotificationFamily) {
    Reactions("eveningReactions", NotificationFamily.Comments),
    Comments("eveningComments", NotificationFamily.Comments),
    Weekly("eveningWeekly", NotificationFamily.Weekly),

    /** The souvenirs, « Pensez à moi » and the récaps. */
    Souvenirs("eveningSouvenirs", NotificationFamily.Weekly),
}

/**
 * The three levels at the top of the screen. Each one only sets the switches at once, so a switch
 * changed afterwards always counts (the server reads the switches alone).
 */
enum class NotificationLevel(val on: Set<NotificationFamily>) {
    All(NotificationFamily.entries.toSet()),
    Essential(setOf(NotificationFamily.Planning, NotificationFamily.Reminders)),
    Minimal(emptySet()),
}

/** What a member chose. Without a document, everything is on, « Calme la nuit » too (rév. 266). */
data class NotificationPrefs(
    val off: Set<NotificationFamily> = emptySet(),
    val quietHours: Boolean = true,
    /** The kinds that wait for 19 h when the résumé du soir is on: all of them by default. */
    val evening: Set<EveningKind> = EveningKind.entries.toSet(),
) {
    fun isOn(family: NotificationFamily): Boolean = family !in off

    /** The level the switches match, or null when they were set one by one. */
    val level: NotificationLevel?
        get() = NotificationLevel.entries.firstOrNull { level -> NotificationFamily.entries.all { (it in level.on) == isOn(it) } }

    companion object {
        /** Reads the settings document; anything else than `false` keeps a family on. */
        fun from(data: Map<String, Any?>?): NotificationPrefs = NotificationPrefs(
            off = NotificationFamily.entries.filter { data?.get(it.key) == false }.toSet(),
            // On unless the member turned it off themselves.
            quietHours = data?.get("quietHours") != false,
            evening = EveningKind.entries.filter { data?.get(it.key) != false }.toSet(),
        )
    }
}
