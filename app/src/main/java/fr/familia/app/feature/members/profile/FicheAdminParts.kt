package fr.familia.app.feature.members.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.ui.HouseholdMemberPicker
import fr.familia.app.core.ui.form.ChoiceTile
import fr.familia.app.core.ui.form.ChoiceTiles
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.pickGroups
import fr.familia.app.feature.members.MembersViewModel
import fr.familia.app.feature.members.edit.FormError
import fr.familia.app.feature.members.edit.errorText
import fr.familia.app.feature.members.edit.labelRes

/*
 * What only the admin principal changes, in « Modifier » (règle 4, F9): Profil, Foyer et parents,
 * and « Retirer de Familia » at the bottom (F11). Nobody else ever sees these tiles.
 */

/** « Profil »: adult, child with the app, child without; the Google address of who signs in. */
@Composable
internal fun ProfileEdit(edit: FicheEdit, onDraft: (FicheDraft) -> Unit) {
    val draft = edit.draft
    val form = draft.form ?: return
    val errors = edit.admin?.errors.orEmpty()
    FicheSection(stringResource(R.string.profile_edit_profile)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceTiles(MemberProfile.entries, columns = 3) { profile, tileModifier ->
                ChoiceTile(
                    title = stringResource(profile.labelRes()),
                    selected = form.profile == profile,
                    onClick = { onDraft(draft.copy(form = form.copy(profile = profile))) },
                    modifier = tileModifier,
                )
            }
            if (form.needsEmail) {
                val help: @Composable () -> Unit = { Text(stringResource(R.string.member_email_help_optional)) }
                OutlinedTextField(
                    value = form.email,
                    onValueChange = { onDraft(draft.copy(form = form.copy(email = it))) },
                    label = { Text(stringResource(R.string.member_email)) },
                    isError = FormError.EmailInvalid in errors || FormError.EmailTaken in errors,
                    supportingText = errorText(errors, FormError.EmailInvalid, FormError.EmailTaken) ?: help,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** « Foyer », and « Foyer et parents » for a child: the household to choose, « Nouveau foyer », the responsible adults. */
@Composable
internal fun HouseholdEdit(edit: FicheEdit, onDraft: (FicheDraft) -> Unit, onAddHousehold: (String) -> Unit) {
    val draft = edit.draft
    val form = draft.form ?: return
    val admin = edit.admin ?: return
    val errors = admin.errors
    var adding by rememberSaveable { mutableStateOf(false) }
    FicheSection(stringResource(if (form.needsParents) R.string.profile_edit_household_parents else R.string.profile_v2_household)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceTiles(admin.households, columns = 2) { household, tileModifier ->
                ChoiceTile(
                    title = household.name,
                    selected = household.id == form.householdId,
                    onClick = { onDraft(draft.copy(form = form.copy(householdId = household.id))) },
                    modifier = tileModifier,
                )
            }
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("+ " + stringResource(R.string.member_household_new))
            }
            errorText(errors, FormError.HouseholdMissing)?.invoke()
            if (form.needsParents) {
                Text(stringResource(R.string.member_parents), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.member_parents_help), style = MaterialTheme.typography.bodyMedium, color = ficheInkSoft())
                HouseholdMemberPicker(
                    groups = pickGroups(admin.adults, admin.households, form.householdId),
                    selected = form.parentIds,
                    onChange = { ids -> onDraft(draft.copy(form = form.copy(parentIds = ids))) },
                )
                errorText(errors, FormError.SoleParentOfChild, FormError.ParentsMissing, FormError.TooManyParents)?.invoke()
            }
        }
    }
    if (adding) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.member_household_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MembersViewModel.MAX_HOUSEHOLD_NAME) },
                    label = { Text(stringResource(R.string.member_household_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            },
            confirmButton = {
                TextButton(onClick = { onAddHousehold(name); adding = false }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * « Retirer de Familia » (F11) at the bottom of « Modifier »: never the admin principal themselves,
 * and blocked, with why, while the member is a child's only responsible adult. What they shared
 * (recettes, photos, commentaires) goes with them.
 */
@Composable
internal fun RemovePart(firstName: String, edit: FicheEdit, onRemove: () -> Unit) {
    val admin = edit.admin ?: return
    if (admin.isSelf) {
        FicheSection(stringResource(R.string.profile_edit_remove_section)) {
            FicheRow("👑", stringResource(R.string.profile_edit_remove_self))
        }
        return
    }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val blocked = admin.soleParentOf.isNotEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormDeleteButton(stringResource(R.string.profile_edit_remove_button, firstName), onClick = { confirm = true }, enabled = !blocked && !edit.saving)
        if (blocked) {
            Text(
                stringResource(R.string.member_remove_blocked, admin.soleParentOf.joinToString(", ")),
                style = onPhotoStyle(MaterialTheme.typography.bodyLarge),
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.profile_edit_remove_title, firstName)) },
            text = { Text(stringResource(R.string.profile_edit_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.member_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
