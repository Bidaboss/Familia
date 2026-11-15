package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import java.time.Instant

/** A fictional family for previews and screenshots. */
internal fun previewGoodNewsFamily(): Family {
    fun member(id: String, name: String, profile: MemberProfile = MemberProfile.Adult, parents: List<String> = emptyList(), color: Int) =
        Member(id, name, null, null, "h1", profile, isAdmin = false, parentIds = parents, uid = null, colorIndex = color)
    return Family(
        households = listOf(Household("h1", "Foyer Martin")),
        members = listOf(
            member("me", "Camille", color = 2),
            member("jules", "Jules", color = 5),
            member("ines", "Inès", color = 8),
            member("noe", "Noé", MemberProfile.ChildWithApp, listOf("me", "jules"), color = 11),
        ),
        ownerId = "me",
    )
}

internal fun previewGoodNewsItems(): List<GoodNewsItem> {
    val family = previewGoodNewsFamily()
    val me = family.members.first()
    return listOf(
        GoodNews(
            "n1", "Noé a fait ses premiers tours de vélo sans les roulettes, et sans tomber !", listOf("p1"),
            authorId = "me", authorName = "Camille", forMemberId = "noe", householdId = "h1", mentionIds = listOf("jules"),
            reactions = mapOf("jules" to Reaction.Heart, "ines" to Reaction.Party, "noe" to Reaction.Heart),
            createdAt = Instant.parse("2026-11-21T14:05:00Z"), updatedAt = Instant.parse("2026-11-21T14:05:00Z"),
        ),
        GoodNews(
            "n2", "Le gâteau d'anniversaire est réussi. Merci à tous d'être venus !", listOf("p1", "p2", "p3"),
            authorId = "ines", authorName = "Inès", forMemberId = "ines", householdId = "h1",
            reactions = mapOf("me" to Reaction.Clap),
            createdAt = Instant.parse("2026-11-20T09:30:00Z"), updatedAt = Instant.parse("2026-11-20T10:30:00Z"),
        ),
    ).map { goodNewsItem(it, me, family) }
}
