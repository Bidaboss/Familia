package fr.familia.app.feature.members.profile

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
import fr.familia.app.R
import fr.familia.app.core.model.MemberCard

/**
 * « À savoir » and « Goûts et passions » in one form, opened by the pencils of the page, for those
 * who may change the card (the member, an adult of their household for a child, the admin principal).
 * Each field stops at its length, so a save is never refused for it.
 */
@Composable
internal fun MemberCardDialog(card: MemberCard, kid: Boolean, onSave: (MemberCard) -> Boolean, onDismiss: () -> Unit) {
    var allergies by rememberSaveable { mutableStateOf(card.allergies) }
    var diet by rememberSaveable { mutableStateOf(card.diet) }
    var avoid by rememberSaveable { mutableStateOf(card.avoid) }
    var shoes by rememberSaveable { mutableStateOf(card.shoes) }
    var top by rememberSaveable { mutableStateOf(card.top) }
    var bottom by rememberSaveable { mutableStateOf(card.bottom) }
    var ring by rememberSaveable { mutableStateOf(card.ring) }
    var tastes by rememberSaveable { mutableStateOf(card.tastes) }
    var hobbies by rememberSaveable { mutableStateOf(card.hobbies) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_card_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.member_card_help), style = MaterialTheme.typography.bodyMedium)
                Field(R.string.profile_v2_allergies, allergies, MemberCard.TEXT_MAX) { allergies = it }
                Field(R.string.profile_v2_diet, diet, MemberCard.TEXT_MAX) { diet = it }
                Field(R.string.profile_v2_avoid, avoid, MemberCard.TEXT_MAX) { avoid = it }
                Field(R.string.member_card_shoes, shoes, MemberCard.SIZE_MAX, singleLine = true) { shoes = it }
                Field(R.string.profile_v2_size_top, top, MemberCard.SIZE_MAX, singleLine = true) { top = it }
                Field(R.string.profile_v2_size_bottom, bottom, MemberCard.SIZE_MAX, singleLine = true) { bottom = it }
                // A child rarely has a ring size: the field shows once one is written (prototype v157).
                if (!kid || ring.isNotBlank()) Field(R.string.profile_v2_size_ring, ring, MemberCard.SIZE_MAX, singleLine = true) { ring = it }
                Field(R.string.member_card_tastes, tastes, MemberCard.TEXT_MAX) { tastes = it }
                Field(R.string.profile_v2_passions, hobbies, MemberCard.TEXT_MAX) { hobbies = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val typed = card.copy(
                        allergies = allergies, diet = diet, avoid = avoid, shoes = shoes, top = top, bottom = bottom,
                        ring = ring, tastes = tastes, hobbies = hobbies,
                    )
                    if (onSave(typed)) onDismiss()
                },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.member_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Field(@StringRes label: Int, value: String, max: Int, singleLine: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}
