package fr.familia.app.feature.members.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard

/*
 * « Modifier » in place on my own page (règle 3, F10): the same tiles, the same sizes, only the
 * fields open. The first name, the birth date and the address are the admin principal's: they stay
 * shown, locked, with why.
 */

/** « Identité »: my first name and birth date locked, my surnom, and « En ce moment » with the card. */
@Composable
internal fun IdentityEdit(member: Member, draft: FicheDraft, hasCard: Boolean, onDraft: (FicheDraft) -> Unit) {
    val unknown = stringResource(R.string.profile_v2_unknown)
    FicheSection(stringResource(R.string.profile_edit_identity)) {
        FicheLockedRow("🙂", stringResource(R.string.member_first_name), member.firstName, stringResource(R.string.profile_edit_lock_name))
        FicheDivider()
        FicheEditRow("💬", R.string.profile_edit_nickname, draft.nickname, Member.NICKNAME_MAX, hint = stringResource(R.string.profile_edit_nickname_hint)) {
            onDraft(draft.copy(nickname = it))
        }
        FicheDivider()
        FicheLockedRow(
            "🎂",
            stringResource(R.string.profile_edit_birth),
            member.birthDate?.let { fr.familia.app.core.ui.frenchDate(it, "d MMMM yyyy") } ?: unknown,
            stringResource(R.string.profile_edit_lock_birth),
        )
        if (hasCard) {
            FicheDivider()
            FicheEditRow("💭", R.string.profile_v2_now, draft.card.now, MemberCard.NOW_MAX, hint = stringResource(R.string.profile_edit_now_hint)) {
                onDraft(draft.copy(card = draft.card.copy(now = it)))
            }
        }
    }
}

/** « À savoir »: allergies, diet and what not to offer me, seen by the whole family. */
@Composable
internal fun KnowEdit(draft: FicheDraft, onDraft: (FicheDraft) -> Unit) {
    val card = draft.card
    fun set(c: MemberCard) = onDraft(draft.copy(card = c))
    FicheSection(stringResource(R.string.profile_v2_know)) {
        FicheEditRow("⚠️", R.string.profile_v2_allergies, card.allergies, MemberCard.TEXT_MAX) { set(card.copy(allergies = it)) }
        FicheDivider()
        FicheEditRow("🥗", R.string.profile_v2_diet, card.diet, MemberCard.TEXT_MAX) { set(card.copy(diet = it)) }
        FicheDivider()
        FicheEditRow("🚫", R.string.profile_v2_avoid_mine, card.avoid, MemberCard.TEXT_MAX) { set(card.copy(avoid = it)) }
        FicheSmall(stringResource(R.string.profile_v2_know_seen))
    }
}

/** « Mes goûts et passions »: the sizes, then the tastes and the passions. A child's ring size only once written. */
@Composable
internal fun TastesEdit(draft: FicheDraft, kid: Boolean, onDraft: (FicheDraft) -> Unit) {
    val card = draft.card
    fun set(c: MemberCard) = onDraft(draft.copy(card = c))
    FicheSection(stringResource(R.string.member_card_section_mine)) {
        FicheEditRow("👟", R.string.member_card_shoes, card.shoes, MemberCard.SIZE_MAX, singleLine = true) { set(card.copy(shoes = it)) }
        FicheDivider()
        FicheEditRow("👕", R.string.profile_v2_size_top, card.top, MemberCard.SIZE_MAX, singleLine = true) { set(card.copy(top = it)) }
        FicheDivider()
        FicheEditRow("👖", R.string.profile_v2_size_bottom, card.bottom, MemberCard.SIZE_MAX, singleLine = true) { set(card.copy(bottom = it)) }
        if (!kid || card.ring.isNotBlank()) {
            FicheDivider()
            FicheEditRow("💍", R.string.profile_v2_size_ring, card.ring, MemberCard.SIZE_MAX, singleLine = true) { set(card.copy(ring = it)) }
        }
        FicheDivider()
        FicheEditRow("❤️", R.string.member_card_tastes, card.tastes, MemberCard.TEXT_MAX) { set(card.copy(tastes = it)) }
        FicheDivider()
        FicheEditRow("✨", R.string.profile_v2_passions, card.hobbies, MemberCard.TEXT_MAX) { set(card.copy(hobbies = it)) }
    }
}

/** « Mes coordonnées »: my phone open; the address of the household shown, locked (the admin's). */
@Composable
internal fun CoordinatesEdit(address: String?, edit: FicheEdit, onDraft: (FicheDraft) -> Unit) {
    val draft = edit.draft
    FicheSection(stringResource(R.string.profile_v2_coordinates_mine)) {
        FicheEditRow(
            "📞",
            R.string.profile_v2_phone_title,
            draft.phone,
            max = 24,
            singleLine = true,
            hint = stringResource(R.string.profile_edit_phone_hint),
            keyboard = KeyboardType.Phone,
            error = if (edit.phoneValid) null else stringResource(R.string.profile_edit_phone_invalid),
        ) { onDraft(draft.copy(phone = it)) }
        FicheDivider()
        FicheLockedRow("🏠", stringResource(R.string.profile_v2_address_of), address ?: stringResource(R.string.profile_v2_unknown), stringResource(R.string.profile_edit_lock_address))
        FicheSmall(stringResource(R.string.profile_v2_coordinates_help))
    }
}

/** A row whose value is open: the emoji in its cream round, the field under its title. */
@Composable
internal fun FicheEditRow(
    emoji: String,
    @StringRes label: Int,
    value: String,
    max: Int,
    singleLine: Boolean = false,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FicheEmoji(emoji)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(max)) },
            label = { Text(stringResource(label)) },
            placeholder = if (hint != null) ({ Text(hint) }) else null,
            singleLine = singleLine,
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            keyboardOptions = KeyboardOptions(
                capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                keyboardType = keyboard,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A row that only the admin principal changes: the value shown, a 🔒 and why, never a refusal after typing. */
@Composable
internal fun FicheLockedRow(emoji: String, title: String, value: String, why: String) {
    FicheRow(emoji, title, value, trailing = { Text("🔒", modifier = Modifier.clearAndSetSemantics { }) })
    Text(
        why,
        style = MaterialTheme.typography.bodyMedium,
        color = ficheInkSoft(),
        modifier = Modifier.fillMaxWidth().padding(start = 76.dp, end = 16.dp, bottom = 8.dp),
    )
}
