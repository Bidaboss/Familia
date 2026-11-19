package fr.familia.app.core.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.place.addressFromPasted

/**
 * « Où » in pills (DESIGN-50, the place of récit 74): « Chez un foyer », « Une adresse », « Autre ».
 * A household shows the households in pills; an address keeps its field, where a pasted Maps link
 * becomes the place it names; « Autre » is a free text. [text] is the address or the free text,
 * [onText] gets it already read. [error] shows under the field. Shared by the event and the
 * coup de main.
 */
@Composable
fun PlaceChooser(
    kind: PlaceKind,
    onKind: (PlaceKind) -> Unit,
    households: List<Household>,
    householdId: String?,
    onHousehold: (Household) -> Unit,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipGroup {
            if (households.isNotEmpty()) {
                FormChip(
                    stringResource(R.string.event_place_household),
                    selected = kind == PlaceKind.Household,
                    onClick = { onKind(PlaceKind.Household) },
                    icon = Icons.Outlined.Home,
                )
            }
            FormChip(
                stringResource(R.string.form_place_address),
                selected = kind == PlaceKind.Address,
                onClick = { onKind(PlaceKind.Address) },
                icon = Icons.Outlined.Place,
            )
            FormChip(
                stringResource(R.string.event_place_other),
                selected = kind == PlaceKind.Other,
                onClick = { onKind(PlaceKind.Other) },
            )
        }
        when (kind) {
            PlaceKind.Household -> {
                FormLabel(stringResource(R.string.form_place_which_household))
                ChipGroup {
                    households.forEach { household ->
                        FormChip(household.name, selected = householdId == household.id, onClick = { onHousehold(household) })
                    }
                }
                val chosen = households.firstOrNull { it.id == householdId }
                if (chosen != null && chosen.address.isNullOrBlank()) FormHint(stringResource(R.string.event_place_no_address))
                if (error != null) FormHint(error, isError = true)
            }
            PlaceKind.Address -> {
                FormLabel(stringResource(R.string.event_place_address))
                OutlinedTextField(
                    value = text,
                    onValueChange = { onText(addressFromPasted(it)) },
                    isError = error != null,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormHint(error ?: stringResource(R.string.event_place_address_help), isError = error != null)
            }
            PlaceKind.Other -> {
                FormLabel(stringResource(R.string.form_place_other_label))
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    isError = error != null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) FormHint(error, isError = true)
            }
        }
    }
}
