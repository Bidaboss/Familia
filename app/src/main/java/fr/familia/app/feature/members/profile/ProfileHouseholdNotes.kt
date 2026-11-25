package fr.familia.app.feature.members.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Household
import fr.familia.app.feature.members.RenameHouseholdDialog

/** The dark of the prototype (#3A2A1A), white text on it. */
private val NoteDark = Color(0xFF3A2A1A)

/**
 * « Foyer » (règle 7, F13): the name of the household and how many live in it, and « Renommer le
 * foyer ». Only for who may rename it: a chef de famille can do nothing else to the household.
 */
@Composable
internal fun HouseholdPart(household: Household, size: Int, onRename: (String) -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    FicheSection(stringResource(R.string.profile_v2_household)) {
        FicheRows(
            listOf<@Composable () -> Unit>(
                { FicheRow("🏡", household.name, pluralStringResource(R.plurals.profile_v2_household_people, size, size)) },
                {
                    FicheRow(
                        "✎",
                        stringResource(R.string.household_rename_title),
                        stringResource(R.string.profile_v2_household_rename_hint),
                        onClick = { renaming = true },
                        onClickLabel = stringResource(R.string.household_rename_title),
                    )
                },
            ),
        )
    }
    if (renaming) {
        RenameHouseholdDialog(household, onConfirm = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
}

/**
 * « Mes notes » (règle 9, F7): a private notepad about [firstName], never seen by them. Saved on its
 * own as I type (« enregistré tout seul »), apart from « Modifier ». « En faire une idée » starts an
 * idea of the carnet with the note in it.
 */
@Composable
internal fun NotesPart(firstName: String, initial: String, onEdit: (String) -> Unit, onMakeIdea: ((String) -> Unit)?) {
    var text by rememberSaveable { mutableStateOf(initial.take(NOTE_MAX)) }
    FicheSection(stringResource(R.string.profile_v2_notes)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NoteDark)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔒", modifier = Modifier.clearAndSetSemantics { })
            Text(
                stringResource(R.string.profile_v2_notes_private, firstName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = stringResource(R.string.profile_v2_notes_label, firstName)
            OutlinedTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed.take(NOTE_MAX)
                    onEdit(text)
                },
                placeholder = { Text(stringResource(R.string.profile_v2_notes_hint, firstName)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.profile_v2_notes_count, text.length, NOTE_MAX),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ficheInkSoft(),
                    modifier = Modifier.weight(1f),
                )
                if (onMakeIdea != null && text.isNotBlank()) {
                    Button(
                        onClick = { onMakeIdea(text.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = NoteDark, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.profile_v2_notes_idea), fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}
