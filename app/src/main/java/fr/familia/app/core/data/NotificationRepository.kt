package fr.familia.app.core.data

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
}
