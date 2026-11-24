package fr.familia.app.feature.trips.safety

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.phrases.rememberAbroadCountries

/** The country of the page of a step that has emergency numbers, or null: France, no country, or not in the table. */
fun stepSafetyCountry(countryCode: String?): String? = EmergencyNumbers.abroad(listOfNotNull(countryCode)).firstOrNull()

/**
 * « Sécurité » on the page of a step abroad (récit 112, règles 39 et 43): the emergency numbers of its
 * country, each one opening the dialer, and « Prévenir ma famille » that opens the screen of the alert.
 * Nothing in France, nor while the phone has not placed the step.
 */
@Composable
fun StepSafetyTile(container: AppContainer?, step: TripStep, onOpen: ((country: String) -> Unit)?) {
    val steps = remember(step.id, step.lat, step.lng) { listOf(step) }
    val country = stepSafetyCountry(rememberAbroadCountries(container, steps).firstOrNull()) ?: return
    TripTile(Icons.Outlined.HealthAndSafety, stringResource(R.string.safety_step_title, frenchCountryName(country))) {
        CountryBlock(country, titled = false)
        if (onOpen != null) {
            OutlinedButton(onClick = { onOpen(country) }, modifier = Modifier.padding(top = 4.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.safety_step_open))
            }
        }
    }
}
