package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Annonces de l'admin (récit 69). Fictional first names only. */
class AnnouncementTest {
    private fun m(id: String, home: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, null, home, profile, isAdmin = false, parentIds = emptyList(), uid = null)

    private val camille = m("camille", "h1")
    private val family = Family(
        listOf(Household("h1", "Les Martin"), Household("h2", "Les Bernard")),
        listOf(camille, m("jules", "h1"), m("nina", "h1", MemberProfile.ChildWithApp), m("lou", "h2", MemberProfile.ChildWithoutApp), m("ines", "h2")),
    )

    @Test
    fun `the family audience is everyone with the app, never a child without it`() {
        assertEquals(listOf("camille", "jules", "nina", "ines"), familyAudience(family).map { it.id })
    }

    @Test
    fun `the admin never writes to themselves`() {
        assertEquals(listOf("jules", "nina", "ines"), announcementCandidates(family, camille).map { it.id })
    }

    @Test
    fun `a title, a message and one recipient are needed, within their limits`() {
        assertTrue(canSendAnnouncement("Repas", "À midi", setOf("ines")))
        assertFalse(canSendAnnouncement("  ", "À midi", setOf("ines")))
        assertFalse(canSendAnnouncement("Repas", "", setOf("ines")))
        assertFalse(canSendAnnouncement("Repas", "À midi", emptySet()))
        assertFalse(canSendAnnouncement("a".repeat(61), "À midi", setOf("ines")))
        assertFalse(canSendAnnouncement("Repas", "a".repeat(301), setOf("ines")))
        assertTrue(canSendAnnouncement("a".repeat(60), "a".repeat(300), setOf("ines")))
    }

    @Test
    fun `from 22 h to 8 h in Paris, a message without Urgent waits for 8 h`() {
        // November: Paris is UTC+1.
        assertTrue(leavesAtEight(urgent = false, Instant.parse("2026-11-18T21:00:00Z")))
        assertTrue(leavesAtEight(urgent = false, Instant.parse("2026-11-19T06:59:00Z")))
        assertFalse(leavesAtEight(urgent = false, Instant.parse("2026-11-19T07:00:00Z")))
        assertFalse(leavesAtEight(urgent = false, Instant.parse("2026-11-18T20:59:00Z")))
        assertFalse(leavesAtEight(urgent = true, Instant.parse("2026-11-18T23:00:00Z")))
    }

    @Test
    fun `the delivery written by the server is read, and nothing yet reads as pending`() {
        assertEquals(Announcement.Delivery.Sent, Announcement.Delivery.fromKey("sent"))
        assertEquals(Announcement.Delivery.Morning, Announcement.Delivery.fromKey("morning"))
        assertEquals(Announcement.Delivery.Refused, Announcement.Delivery.fromKey("refused"))
        assertEquals(Announcement.Delivery.Pending, Announcement.Delivery.fromKey(null))
        assertEquals(Announcement.Delivery.Pending, Announcement.Delivery.fromKey("autre"))
    }

    @Test
    fun `the newest first, one being sent on top`() {
        fun a(id: String, at: String?) = Announcement(id, "T", "M", false, listOf("ines"), "camille", "Camille", at?.let(Instant::parse))
        val order = announcementOrder(listOf(a("old", "2026-11-01T10:00:00Z"), a("new", "2026-11-18T10:00:00Z"), a("sending", null)))
        assertEquals(listOf("sending", "new", "old"), order.map { it.id })
    }

    @Test
    fun `the module is read for the family, even for the admin principal`() {
        assertFalse(FeatureFlags(isOwner = true).isOnForFamily(Feature.AdminAnnouncements))
        assertTrue(FeatureFlags(mapOf("adminAnnouncements" to true)).isOnForFamily(Feature.AdminAnnouncements))
    }
}
