package fr.familia.app.feature.trips.money

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
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
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.TripPageField
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
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
    val saving by money.saving.collectAsStateWithLifecycle()
    val saved by money.saved.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onBack)
    LaunchedEffect(saved) { if (saved) close() }
    // Fiche F6: the sheet draws its own dim over the trip, the window of the dialog does not add another.
    val view = LocalView.current
    SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
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
        saving = saving,
    )
}

/**
 * Stateless. [onChange] is null for someone who cannot change the currency of the trip: they see the
 * currency, its rate and the converter, without the choice. [stepCurrency], when « Convertir » comes
 * from a step whose currency is not the one of the trip (a trip in GBP, a step in Switzerland), comes
 * first with its own rate and converter (retour C1 de #574).
 *
 * Fiche F6: a cream sheet, rounded 28, over the dimmed trip; the search by name or code, the currencies
 * in pills (the chosen one dark), the rate of the day read on its own with its date (never typed by
 * hand, DESIGN F6 point 5), and « Enregistrer » at the bottom.
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
    saving: Boolean = false,
) {
    val stepOnly = stepOnlyCurrency(stepCurrency, currency)
    var pickedCode by rememberSaveable(currency.code) { mutableStateOf(currency.code) }
    val picked = Currencies.of(pickedCode)
    val shown = if (onChange != null) picked else currency
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The dimmed trip: a touch on it closes the sheet, as the round ✕ does for TalkBack.
        Box(
            Modifier
                .matchParentSize()
                .background(Scrim)
                .clearAndSetSemantics { }
                .pointerInput(onBack) { detectTapGestures { onBack() } },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.92f)
                .background(Cream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .imePadding(),
        ) {
            CompositionLocalProvider(LocalContentColor provides TripPathColors.Ink) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.money_currency_heading),
                        fontFamily = Fredoka,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    CloseRound(onBack)
                }
                if (loading) {
                    LoadingState(Modifier.heightIn(min = 160.dp))
                    return@CompositionLocalProvider
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.money_sheet_intro), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (stepOnly != null) {
                        SheetHeading(stringResource(R.string.money_step_heading))
                        Text(stringResource(R.string.money_currency_value, stepOnly.name, stepOnly.code), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        RateBlock(stepOnly, rates, onRetry)
                        SheetHeading(stringResource(R.string.money_currency_heading))
                    }
                    if (onChange != null) {
                        CurrencyChoice(picked, listOf(currency) + suggestions, enabled = !saving) { pickedCode = it.code }
                    } else {
                        Text(stringResource(R.string.money_currency_value, currency.name, currency.code), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (saveFailed) {
                        Text(stringResource(R.string.money_save_failed), color = ErrorRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    if (shown == Currencies.EURO) {
                        if (stepOnly == null) Text(stringResource(R.string.money_in_euro), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else {
                        RateBlock(shown, rates, onRetry)
                    }
                }
                if (onChange != null) {
                    Button(
                        onClick = { onChange(picked.code) },
                        enabled = picked != currency && !saving,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TripPathColors.Ink,
                            contentColor = Color.White,
                            disabledContainerColor = SaveOff,
                            disabledContentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.money_save), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

/** The round ✕ of the sheet: 48 dp white drawn in a 56 dp touch area. */
@Composable
private fun CloseRound(onClose: () -> Unit) {
    val label = stringResource(R.string.money_close)
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClose)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = TripPathColors.Ink)
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(text, fontFamily = Fredoka, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

/** The search by name or code, then the currencies in pills: without a search, the current one, those of the trip, then the most common. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChoice(picked: TripCurrency, first: List<TripCurrency>, enabled: Boolean, onPick: (TripCurrency) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val found = remember(query, picked, first) {
        if (query.isBlank()) (listOf(picked) + first + Currencies.ALL).distinct().take(CHIPS_SHOWN) else Currencies.search(query).take(CHIPS_FOUND)
    }
    TripPageField(label = stringResource(R.string.money_sheet_search), value = query, onValueChange = { query = it.take(40) })
    if (found.isEmpty()) {
        Text(stringResource(R.string.money_search_none, query), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        found.forEach { option -> CurrencyPill(option, option == picked, enabled) { onPick(option) } }
    }
}

/** A pill of 48 dp in a touch area of 56: the name, and the code in small. */
@Composable
private fun CurrencyPill(option: TripCurrency, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .background(if (selected) TripPathColors.Ink else Color.White, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ink = if (selected) Color.White else TripPathColors.Ink
            Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ink)
            Text(option.code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun RateBlock(currency: TripCurrency, rates: RatesState, onRetry: () -> Unit) {
    SheetHeading(stringResource(R.string.money_rate_heading))
    when (rates) {
        RatesState.Loading -> Text(stringResource(R.string.money_rate_loading), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        RatesState.Unavailable -> {
            Text(stringResource(R.string.money_rate_unavailable), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.money_retry), fontWeight = FontWeight.ExtraBold) }
        }
        is RatesState.Ready -> {
            val perEuro = rates.table.perEuro(currency.code)
            if (perEuro == null) {
                Text(stringResource(R.string.money_rate_missing, currency.name), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.money_rate_value, number(perEuro), currency.code), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Source(rates.table.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)), color = TripPathColors.Ink.copy(alpha = 0.8f))
                Converter(currency, perEuro)
            }
        }
    }
}

/** The date of the rate and its source, as the terms of the source ask. */
@Composable
internal fun Source(date: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.money_rate_source, date, OpenExchangeRates.SOURCE_NAME),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
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
    SheetHeading(stringResource(R.string.money_convert_heading))
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    TripPageField(
        label = stringResource(R.string.money_amount_in, currency.code),
        value = local,
        onValueChange = { text ->
            local = text.take(MAX_AMOUNT_CHARS)
            euros = toEuro(parseAmount(local), perEuro)?.let(::number).orEmpty()
        },
        keyboardOptions = decimal,
    )
    TripPageField(
        label = stringResource(R.string.money_amount_in, "€"),
        value = euros,
        onValueChange = { text ->
            euros = text.take(MAX_AMOUNT_CHARS)
            local = fromEuro(parseAmount(euros), perEuro)?.let(::number).orEmpty()
        },
        keyboardOptions = decimal,
    )
    Text(stringResource(R.string.money_convert_hint), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TripPathColors.Ink.copy(alpha = 0.8f))
}

private val Cream = Color(0xFFFBF4EA)
private val Scrim = Color.Black.copy(alpha = 0.45f)
private val SaveOff = Color(0xFF8E979D)
private val ErrorRed = Color(0xFFB3261E)
private const val CHIPS_SHOWN = 8
private const val CHIPS_FOUND = 16

private const val MAX_AMOUNT_CHARS = 12

/** A French number: two decimals at most, none for big amounts. */
internal fun number(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.FRANCE)
    format.maximumFractionDigits = if (value >= 1000) 0 else if (value >= 1) 2 else 4
    return format.format(value)
}
