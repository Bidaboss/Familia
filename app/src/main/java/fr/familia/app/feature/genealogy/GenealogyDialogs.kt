package fr.familia.app.feature.genealogy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.ui.form.ChoiceTile
import fr.familia.app.core.data.KinPhotoChange
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.photo.PhotoProcessing
import fr.familia.app.core.ui.KinAvatar
import fr.familia.app.feature.members.photo.PhotoEditor

/** Stands for « someone new, without an account » in the list of people to join. */
internal const val NEW_KIN = "__new__"

/** My own word for [person] (avis [EXPERT] n° 14): « bonus-maman », a first name… Blank removes it. */
@Composable
internal fun WordDialog(person: TreePerson, current: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var word by rememberSaveable { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.genealogy_word_title, person.firstName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.genealogy_word_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it.take(GenealogyViewModel.MAX_WORD) },
                    label = { Text(stringResource(R.string.genealogy_word_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(word) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.genealogy_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Joins someone to [person]: first how (parent, child, partner), then who, among the people of the
 * tree or someone new without an account. [onPick] gets the relation and the id, or [NEW_KIN].
 */
@Composable
internal fun LinkDialog(
    person: TreePerson,
    tree: GenealogyTree,
    onPick: (Relation, String) -> Unit,
    onDismiss: () -> Unit,
    /** Whom I may join this way: a child only from my household (PRD rév. 195). */
    canJoin: (Relation, String) -> Boolean = { _, _ -> true },
    householdNames: Map<String, String> = emptyMap(),
) {
    var relation by rememberSaveable { mutableStateOf(Relation.Parent) }
    var other by rememberSaveable { mutableStateOf<String?>(null) }
    val joined = (tree.parentsOf(person.id) + tree.childrenOf(person.id) + tree.partnersOf(person.id)).toSet()
    val others = tree.people.values
        .filter { it.id != person.id && it.id !in joined && canJoin(relation, it.id) }
        .sortedBy { it.firstName.lowercase() }
    // A person picked for one relation may not be offered for the next one.
    val picked = other?.takeIf { it == NEW_KIN || others.any { p -> p.id == it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.genealogy_link_title, person.firstName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Relation.entries.forEach { r ->
                        Choice(stringResource(relationLabel(r), person.firstName), selected = relation == r) { relation = r }
                    }
                }
                Text(
                    stringResource(R.string.genealogy_link_who),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.heightIn(min = 40.dp),
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.genealogy_link_new_kin), selected = picked == NEW_KIN) { other = NEW_KIN }
                    val label = rememberPickLabel(tree.people.values, householdNames)
                    others.forEach { p -> Choice(label(p), selected = picked == p.id) { other = p.id } }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { picked?.let { onPick(relation, it) } },
                enabled = picked != null,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.genealogy_link_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * « Relier deux membres » from an empty tree (retour d'essai du 17/11): first the person to join,
 * among [people] (the ones I may edit, by household, mine first); the link dialog follows.
 */
@Composable
internal fun PickPersonDialog(
    people: List<TreePerson>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    everyone: Collection<TreePerson> = people,
    householdNames: Map<String, String> = emptyMap(),
) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val label = rememberPickLabel(everyone, householdNames)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.genealogy_join_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { p -> Choice(label(p), selected = picked == p.id) { picked = p.id } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { picked?.let(onPick) },
                enabled = picked != null,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.genealogy_join_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** [pickLabel] with the words of the screen: two people of the same first name are told apart. */
@Composable
private fun rememberPickLabel(everyone: Collection<TreePerson>, householdNames: Map<String, String>): (TreePerson) -> String {
    val remembered = stringResource(R.string.genealogy_remembered)
    val noAccount = stringResource(R.string.genealogy_pick_no_account)
    return { p -> pickLabel(p, everyone, householdNames, remembered, noAccount) }
}

private fun relationLabel(relation: Relation): Int = when (relation) {
    Relation.Parent -> R.string.genealogy_relation_parent
    Relation.Child -> R.string.genealogy_relation_child
    Relation.Partner -> R.string.genealogy_relation_partner
}

/** One choice of a dialog, as a tile (refonte, DESIGN-57): no more radio buttons. */
@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    ChoiceTile(
        title = label,
        selected = selected,
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        titleStyle = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * A relative without an account (PRD, récit 40): a first name, then whatever is known. Dates are a
 * year or a day, typed as « 1921 » or « 04/03/1921 ». [kin] null adds one. [photo] is the one they
 * have; the « Photo » tile (récit 79) changes or removes it, and [onSave] gets the change, or null.
 */
@Composable
internal fun KinDialog(kin: Kin?, onSave: (Kin, KinPhotoChange?) -> Unit, onDismiss: () -> Unit, photo: ImageBitmap? = null) {
    var firstName by rememberSaveable { mutableStateOf(kin?.firstName.orEmpty()) }
    var lastName by rememberSaveable { mutableStateOf(kin?.lastName.orEmpty()) }
    var living by rememberSaveable { mutableStateOf(kin?.living ?: false) }
    var born by rememberSaveable { mutableStateOf(kin?.born.orEmpty()) }
    var died by rememberSaveable { mutableStateOf(kin?.died.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(kin?.note.orEmpty()) }
    // The photo framed here and not saved yet (kept out of the saved state: 100 KB), or the one taken away.
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    var editingPhoto by rememberSaveable { mutableStateOf(false) }
    val newBitmap = remember(newPhoto) { newPhoto?.let { PhotoProcessing.decodeAvatar(it)?.asImageBitmap() } }
    val shownPhoto = newBitmap ?: photo.takeUnless { photoRemoved }
    val bornValue = Kin.parseDate(born)
    val diedValue = if (living) "" else Kin.parseDate(died)
    val valid = firstName.isNotBlank() && bornValue != null && diedValue != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (kin == null) R.string.genealogy_kin_add else R.string.genealogy_kin_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it.take(Kin.MAX_NAME) },
                    label = { Text(stringResource(R.string.genealogy_kin_first_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it.take(Kin.MAX_NAME) },
                    label = { Text(stringResource(R.string.genealogy_kin_last_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = living, onValueChange = { living = it }, role = Role.Switch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.genealogy_kin_living_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = living, onCheckedChange = null)
                }
                PhotoTile(shownPhoto, firstName) { editingPhoto = true }
                DateField(born, { born = it }, R.string.genealogy_kin_born, bornValue == null)
                if (!living) DateField(died, { died = it }, R.string.genealogy_kin_died, diedValue == null)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(Kin.MAX_NOTE) },
                    label = { Text(stringResource(R.string.genealogy_kin_note)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Kin(
                            id = kin?.id.orEmpty(),
                            firstName = firstName.trim(),
                            lastName = lastName.trim().ifEmpty { null },
                            living = living,
                            born = bornValue?.ifEmpty { null },
                            died = diedValue?.ifEmpty { null },
                            note = note.trim().ifEmpty { null },
                            householdId = kin?.householdId.orEmpty(),
                        ),
                        newPhoto?.let { KinPhotoChange.Set(it) } ?: KinPhotoChange.Remove.takeIf { photoRemoved && photo != null },
                    )
                },
                enabled = valid,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.genealogy_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    if (editingPhoto) {
        val name = firstName.trim()
        PhotoEditor(
            title = if (name.isEmpty()) stringResource(R.string.genealogy_kin_photo) else stringResource(R.string.photo_of, name),
            hasPhoto = shownPhoto != null,
            onSave = { jpeg ->
                newPhoto = jpeg
                photoRemoved = false
            },
            onRemove = {
                newPhoto = null
                photoRemoved = true
            },
            onDismiss = { editingPhoto = false },
        )
    }
}

/** « Photo » (récit 79): the photo or the initial, and what touching it does. 56 dp high. */
@Composable
private fun PhotoTile(photo: ImageBitmap?, firstName: String, onClick: () -> Unit) {
    val action = stringResource(if (photo == null) R.string.genealogy_kin_photo_add else R.string.photo_viewer_change)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KinAvatar(photo, firstName.trim().ifEmpty { "?" }, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.genealogy_kin_photo), style = MaterialTheme.typography.titleSmall)
            Text(action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DateField(value: String, onValue: (String) -> Unit, label: Int, error: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(10)) },
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(if (error) R.string.genealogy_kin_date_error else R.string.genealogy_kin_date_help)) },
        isError = error,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Taking a relative out of the tree takes their links too. */
@Composable
internal fun RemoveKinDialog(kin: Kin, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.genealogy_kin_remove_title, kin.firstName)) },
        text = { Text(stringResource(R.string.genealogy_kin_remove_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_kin_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Asked before a link leaves the tree: it goes for the whole family (relecture #125). */
@Composable
internal fun RemoveLinkDialog(firstName: String, otherName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.genealogy_link_remove_title, firstName, otherName)) },
        text = { Text(stringResource(R.string.genealogy_link_remove_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_link_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
