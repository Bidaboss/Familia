package fr.familia.app.feature.trips.money

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.money.Currencies
import fr.familia.app.core.money.RatesTable
import fr.familia.app.core.money.TripCurrency
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.phrases.rememberAbroadCountries
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The currency of the country of a step, or null: France, the euro zone, or a currency not in the list. */
fun stepCurrency(countryCode: String?): TripCurrency? = Currencies.ofCountries(listOfNotNull(countryCode)).firstOrNull()

/**
 * « Monnaie » on the page of a step outside the euro zone (récit 112, règles 39 et 42, PRODUIT-162):
 * the currency of its country, the rate of the day with its date and source, and « Convertir » that
 * opens « Monnaie » on that currency (retour C1 de #574). Shown with or without « Comptes »; nothing in the
 * euro zone, and only the name of the currency while no rate is known.
 */
@Composable
fun StepMoneyTile(container: AppContainer?, step: TripStep, onOpen: ((String) -> Unit)?) {
    val steps = remember(step.id, step.lat, step.lng) { listOf(step) }
    val currency = stepCurrency(rememberAbroadCountries(container, steps).firstOrNull()) ?: return
    var table by remember { mutableStateOf<RatesTable?>(null) }
    LaunchedEffect(container) { table = container?.exchangeRates?.latest() }
    TripTile(Icons.Outlined.Euro, stringResource(R.string.money_step_title, currency.name, currency.code)) {
        val rates = table
        val perEuro = rates?.perEuro(currency.code)
        if (rates != null && perEuro != null) {
            Text(stringResource(R.string.money_rate_value, number(perEuro), currency.code), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Source(rates.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)))
        }
        if (onOpen != null) {
            OutlinedButton(onClick = { onOpen(currency.code) }, modifier = Modifier.padding(top = 4.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.money_step_open))
            }
        }
    }
}
