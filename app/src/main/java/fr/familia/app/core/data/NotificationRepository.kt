package fr.familia.app.core.data

import fr.familia.app.core.model.NotificationFamily
import fr.familia.app.core.model.NotificationPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** This phone's notifications and the member's notification settings (lot 8). */
interface NotificationRepository {
    /** Tells the Cloud Functions that this phone belongs to [memberId]. Called each time the app opens. */
    suspend fun registerThisDevice(memberId: String)

    /** True unless the member turned the reminder of the evening before off (PRD, récit 6 bis). */
    fun observeDayBeforeReminder(memberId: String): Flow<Boolean>

    suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean)

    /** True unless the member turned the good news notifications off (récit 32). */
    fun observeGoodNews(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setGoodNews(memberId: String, enabled: Boolean) = Unit

    /** True unless the member turned the question of the week off (récit 37). */
    fun observeWeekly(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setWeekly(memberId: String, enabled: Boolean) = Unit

    /** True unless the member turned the « Nouveau souvenir » notice off (récit 34). */
    fun observeSouvenirs(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setSouvenirs(memberId: String, enabled: Boolean) = Unit

    /** True unless the member turned the « Défi réussi » notice off (récit 35). */
    fun observeTree(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setTree(memberId: String, enabled: Boolean) = Unit

    /** « La semaine de la famille » (récit 36), on unless turned off. */
    fun observeRecap(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setRecap(memberId: String, enabled: Boolean) = Unit

    /** Vœux d'anniversaire (récit 36): « C'est l'anniversaire de … » and the cards received, on unless turned off. */
    fun observeBirthdayWishes(memberId: String): Flow<Boolean> = flowOf(true)

    suspend fun setBirthdayWishes(memberId: String, enabled: Boolean) = Unit

    /** The families and the quiet hours (récit 6 quinquies). */
    fun observePrefs(memberId: String): Flow<NotificationPrefs> = flowOf(NotificationPrefs())

    /** Turns these families on or off in one write: one switch, or a whole level. */
    suspend fun setFamilies(memberId: String, families: Map<NotificationFamily, Boolean>) = Unit

    /** From 22 h to 8 h, notices wait until 8 h, grouped. */
    suspend fun setQuietHours(memberId: String, enabled: Boolean) = Unit
}
