package fr.familia.app.core.mention

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.pickGroups
import fr.familia.app.core.viewas.LocalViewAs

/** The family, for the « @ » suggestions and to find the members named in a text. */
val LocalMentionFamily = staticCompositionLocalOf<Family?> { null }

/** Opens the page of a member, from a « @Prénom ». Null in previews. */
val LocalOpenMember = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The members of [ids], in the family, in that order. */
@Composable
fun mentionMembers(ids: Collection<String>): List<Member> {
    val members = LocalMentionFamily.current?.members.orEmpty()
    return ids.mapNotNull { id -> members.firstOrNull { it.id == id } }
}

/** Whom [me] can name: everyone else in the family, children included, by household, mine first. */
@Composable
fun mentionGroups(me: Member): List<PickGroup> {
    val family = LocalMentionFamily.current ?: return emptyList()
    return pickGroups(family.members.filter { it.id != me.id }, family, me.householdId)
}

/** Touching a « @Prénom » opens the member, never in « Voir comme ». */
@Composable
fun openMemberFromMention(): ((String) -> Unit)? =
    LocalOpenMember.current.takeIf { LocalViewAs.current.viewed == null }
