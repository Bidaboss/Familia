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
        )
    }
}
