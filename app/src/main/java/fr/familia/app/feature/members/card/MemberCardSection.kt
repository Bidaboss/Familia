package fr.familia.app.feature.members.card

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import fr.familia.app.feature.members.profile.LocalProfileSection
import fr.familia.app.feature.members.profile.ProfileInviteCard
import fr.familia.app.feature.members.profile.ProfileSectionCard
import fr.familia.app.feature.members.profile.SectionAccent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaPreviews

/** The card of [member] on their page (récit 7 bis), with its own ViewModel. */
@Composable
fun MemberCardSection(container: AppContainer?, currentMember: Member, member: Member) {
    val viewModel: MemberCardViewModel = viewModel(
        key = "member-card-${member.id}",
        factory = viewModelFactory {
            initializer { MemberCardViewModel(container?.memberCardRepository, container?.memberRepository, currentMember.id, member.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    (state as? MemberCardUiState.Ready)?.let { MemberCardContent(it, onSave = viewModel::save) }
}

/** The card, read-only for most; « Modifier » opens the form for those who may. Stateless. */
@Composable
fun MemberCardContent(state: MemberCardUiState.Ready, onSave: (MemberCard) -> Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val card = state.card
    if (LocalProfileSection.current) {
        InProfileSection(state) { editing = true }
        if (editing) MemberCardDialog(card, onSave = onSave, onDismiss = { editing = false })
        return
    }
    ClayCard(Modifier.fillMaxWidth(), contentPadding = ClaySpacing.card) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClayCardTitle(stringResource(R.string.member_card_title))
            if (card.isEmpty) {
                Text(
                    if (state.isMe) stringResource(R.string.member_card_empty_mine) else stringResource(R.string.member_card_empty_other, state.firstName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Line(R.string.member_card_shoes, card.shoes)
                Line(R.string.member_card_tastes, card.tastes)
                Line(R.string.member_card_hobbies, card.hobbies)
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.member_card_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.canEdit) {
                ClayLink(stringResource(if (card.isEmpty) R.string.member_card_fill else R.string.member_card_edit), onClick = { editing = true })
            }
        }
    }
    if (editing) {
        MemberCardDialog(card, onSave = onSave, onDismiss = { editing = false })
    }
}

/**
 * On the new member page (PRODUIT-115): « Goûts et passions » as a section card, with a pencil for
 * those who may change it. Empty, it shows only to them, as an invitation; never to the others.
 */
@Composable
private fun InProfileSection(state: MemberCardUiState.Ready, onEdit: () -> Unit) {
    val card = state.card
    val title = stringResource(if (state.isMe) R.string.member_card_section_mine else R.string.member_card_section)
    if (card.isEmpty) {
        if (!state.canEdit) return
        ProfileInviteCard(
            title = title,
            icon = Icons.Outlined.FavoriteBorder,
            accent = SectionAccent.Rose,
            text = stringResource(R.string.member_card_invite),
            action = stringResource(if (state.isMe) R.string.member_card_fill_mine else R.string.member_card_fill_theirs),
            onAction = onEdit,
        )
        return
    }
    ProfileSectionCard(
        title = title,
        icon = Icons.Outlined.FavoriteBorder,
        accent = SectionAccent.Rose,
        onEdit = if (state.canEdit) onEdit else null,
        editLabel = stringResource(R.string.member_card_edit),
    ) {
        Line(R.string.member_card_tastes, card.tastes)
        Line(R.string.member_card_hobbies, card.hobbies)
        Line(R.string.member_card_shoes, card.shoes)
        if (state.saveFailed) {
            Text(stringResource(R.string.member_card_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One line of the card; nothing when the field is blank. */
@Composable
private fun Line(@StringRes label: Int, value: String) {
    if (value.isBlank()) return
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MemberCardDialog(card: MemberCard, onSave: (MemberCard) -> Boolean, onDismiss: () -> Unit) {
    var shoes by rememberSaveable { mutableStateOf(card.shoes) }
    var tastes by rememberSaveable { mutableStateOf(card.tastes) }
    var hobbies by rememberSaveable { mutableStateOf(card.hobbies) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_card_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.member_card_help), style = MaterialTheme.typography.bodyMedium)
                Field(R.string.member_card_shoes, shoes, MemberCard.SIZE_MAX, singleLine = true) { shoes = it }
                Field(R.string.member_card_tastes, tastes, MemberCard.TEXT_MAX) { tastes = it }
                Field(R.string.member_card_hobbies, hobbies, MemberCard.TEXT_MAX) { hobbies = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (onSave(card.copy(shoes = shoes, tastes = tastes, hobbies = hobbies))) onDismiss() },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.member_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** A field that stops at [max] characters, so a save is never refused for its length. */
@Composable
private fun Field(@StringRes label: Int, value: String, max: Int, singleLine: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@FamiliaPreviews
@Composable
private fun MemberCardPreview() {
    FamiliaTheme {
        Surface {
            MemberCardContent(
                MemberCardUiState.Ready(
                    MemberCard("a", shoes = "39", tastes = "Le chocolat noir, les polars", hobbies = "La randonnée, le jardin"),
                    firstName = "Camille",
                    isMe = true,
                    canEdit = true,
                ),
                onSave = { true },
            )
        }
    }
}
