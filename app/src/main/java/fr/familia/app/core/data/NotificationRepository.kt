package fr.familia.app.core.data

import kotlinx.coroutines.flow.Flow

/** This phone's notifications and the member's notification settings (lot 8). */
interface NotificationRepository {
    /** Tells the Cloud Functions that this phone belongs to [memberId]. Called each time the app opens. */
    suspend fun registerThisDevice(memberId: String)

    /** True unless the member turned the reminder of the evening before off (PRD, récit 6 bis). */
    fun observeDayBeforeReminder(memberId: String): Flow<Boolean>

    suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean)
}
