package fr.familia.app.core.data

import fr.familia.app.core.model.Announcement
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/** announcements/{id} (PRD récit 69): the messages of the admin principal to the family. */
interface AnnouncementRepository {
    /** Writes the message; the server sends it (or keeps it for 8 h) and writes back its delivery. */
    suspend fun send(title: String, text: String, urgent: Boolean, recipientIds: List<String>, me: Member)

    /** « Déjà envoyées »: the last [Announcement.HISTORY], newest first, for the admin principal. */
    fun observeSent(): Flow<List<Announcement>>

    /** One message, opened from its notification; null when it is gone or not for me. */
    fun observe(id: String): Flow<Announcement?>
}
