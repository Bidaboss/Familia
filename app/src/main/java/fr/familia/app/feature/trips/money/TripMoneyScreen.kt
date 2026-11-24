package fr.familia.app.feature.trips.money

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.money.Currencies
import fr.familia.app.core.money.OpenExchangeRates
import fr.familia.app.core.money.TripCurrency
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.phrases.rememberAbroadCountries
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The currency of the step to show above the one of the trip: none when it is the same, or the euro. */
fun stepOnlyCurrency(step: TripCurrency?, trip: TripCurrency): TripCurrency? = step?.takeIf { it != trip && it != Currencies.EURO }

/** « Monnaie » of [tripId] (récit 105, règle 42): the currency of the trip, the rate of the day and a converter. */
@Composable
fun TripMoneyRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit, stepCurrency: String? = null) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-money-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val money: TripMoneyViewModel = viewModel(
        key = "trip-money-rates-$tripId",
        factory = viewModelFactory { initializer { TripMoneyViewModel(container?.tripRepository, container?.exchangeRates, tripId) } },
    )
    val state by detail.uiState.collectAsStateWithLifecycle()
    val rates by money.rates.collectAsStateWithLifecycle()
    val saveFailed by money.saveFailed.collectAsStateWithLifecycle()
    val shown = state as? TripDetailUiState.Success
    val countries = rememberAbroadCountries(container, shown?.steps.orEmpty())
    TripMoneyScreen(
        loading = shown == null,
        currency = Currencies.of(shown?.trip?.currency),
        suggestions = Currencies.ofCountries(countries),
        stepCurrency = stepCurrency?.let(Currencies::of),
        rates = rates,
        saveFailed = saveFailed,
        onChange = if (shown?.canManage == true && container != null) money::setCurrency else null,
        onRetry = money::refresh,
        onBack = onBack,
    )
}

/**
 * Stateless. [onChange] is null for someone who cannot change the currency of the trip. [stepCurrency],
 * when « Convertir » comes from a step whose currency is not the one of the trip (a trip in GBP, a step
 * in Switzerland), comes first with its own rate and converter (retour C1 de #574).
 */
@Composable
fun TripMoneyScreen(
    loading: Boolean,
    currency: TripCurrency,
    suggestions: List<TripCurrency>,
    rates: RatesState,
    saveFailed: Boolean,
    onChange: ((String) -> Unit)?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    stepCurrency: TripCurrency? = null,
) {
    val stepOnly = stepOnlyCurrency(stepCurrency, currency)
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { FamiliaTopBar(title = { Text(stringResource(R.string.money_title)) }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        if (loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stepOnly != null) {
                Text(stringResource(R.string.money_step_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.money_currency_value, stepOnly.name, stepOnly.code), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                RateBlock(stepOnly, rates, onRetry)
            }
            Text(
                stringResource(R.string.money_currency_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = if (stepOnly != null) 16.dp else 0.dp).semantics { heading() },
            )
            Text(stringResource(R.string.money_currency_value, currency.name, currency.code), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (onChange != null) {
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.money_change)) }
                // The currency of a country of the trip, in one tap, while the trip is still in euros.
                if (currency == Currencies.EURO) {
                    suggestions.forEach { suggestion ->
                        TextButton(onClick = { onChange(suggestion.code) }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.money_suggest, suggestion.name, suggestion.code))
                        }
                    }
                }
            }
            if (saveFailed) Text(stringResource(R.string.money_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            if (currency == Currencies.EURO) {
                if (stepOnly == null) Text(stringResource(R.string.money_in_euro), style = MaterialTheme.typography.bodyLarge)
            } else {
                RateBlock(currency, rates, onRetry)
            }
        }
    }
    if (picking && onChange != null) {
        CurrencyPicker(current = currency, onPick = { picking = false; onChange(it.code) }, onDismiss = { picking = false })
    }
}

@Composable
private fun RateBlock(currency: TripCurrency, rates: RatesState, onRetry: () -> Unit) {
    Text(stringResource(R.string.money_rate_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    when (rates) {
        RatesState.Loading -> Text(stringResource(R.string.money_rate_loading), style = MaterialTheme.typography.bodyLarge)
        RatesState.Unavailable -> {
            Text(stringResource(R.string.money_rate_unavailable), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.money_retry)) }
        }
        is RatesState.Ready -> {
            val perEuro = rates.table.perEuro(currency.code)
            if (perEuro == null) {
                Text(stringResource(R.string.money_rate_missing, currency.name), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.money_rate_value, number(perEuro), currency.code), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Source(rates.table.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)))
                Converter(currency, perEuro)
            }
        }
    }
}

/** The date of the rate and its source, as the terms of the source ask. */
@Composable
internal fun Source(date: String) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.money_rate_source, date, OpenExchangeRates.SOURCE_NAME),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OpenExchangeRates.SOURCE_URL)))
                } catch (_: ActivityNotFoundException) {
                    // No browser: the name of the source stays on the screen.
                }
            }
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun Converter(currency: TripCurrency, perEuro: Double) {
    var local by rememberSaveable(currency.code) { mutableStateOf("") }
    var euros by rememberSaveable(currency.code) { mutableStateOf("") }
    Text(stringResource(R.string.money_convert_heading), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    OutlinedTextField(
        value = local,
        onValueChange = { text ->
            local = text.take(MAX_AMOUNT_CHARS)
            euros = toEuro(parseAmount(local), perEuro)?.let(::number).orEmpty()
        },
        label = { Text(stringResource(R.string.money_amount_in, currency.code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = euros,
        onValueChange = { text ->
            euros = text.take(MAX_AMOUNT_CHARS)
            local = fromEuro(parseAmount(euros), perEuro)?.let(::number).orEmpty()
        },
        label = { Text(stringResource(R.string.money_amount_in, "€")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.money_convert_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CurrencyPicker(current: TripCurrency, onPick: (TripCurrency) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(query) { Currencies.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.money_pick_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    label = { Text(stringResource(R.string.money_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.padding(top = 8.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (found.isEmpty()) {
                        Text(stringResource(R.string.money_search_none, query), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    found.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.RadioButton) { onPick(option) }
                                .semantics { selected = option == current },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Text(option.code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.money_close)) }
        },
    )
}

private const val MAX_AMOUNT_CHARS = 12

/** A French number: two decimals at most, none for big amounts. */
internal fun number(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.FRANCE)
    format.maximumFractionDigits = if (value >= 1000) 0 else if (value >= 1) 2 else 4
    return format.format(value)
}
