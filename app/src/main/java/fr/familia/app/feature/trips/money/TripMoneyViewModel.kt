package fr.familia.app.feature.trips.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.money.ExchangeRates
import fr.familia.app.core.money.RatesTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The rates of the screen: being read, read (with their date), or none at all. */
sealed interface RatesState {
    data object Loading : RatesState
    data class Ready(val table: RatesTable) : RatesState
    data object Unavailable : RatesState
}

/**
 * « Monnaie » (récit 105, règle 42): the currency of the trip and the rate of the day from the
 * euro. A rate is always a real one with its date, never a guess.
 */
class TripMoneyViewModel(
    private val trips: TripRepository?,
    private val exchangeRates: ExchangeRates?,
    private val tripId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {
    private val _rates = MutableStateFlow<RatesState>(RatesState.Loading)
    val rates: StateFlow<RatesState> = _rates.asStateFlow()

    private val _saved = MutableStateFlow(false)

    /** True once a currency is saved: the sheet closes (only while it is still shown). */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val source = exchangeRates
        if (source == null) {
            _rates.value = RatesState.Unavailable
            return
        }
        _rates.value = RatesState.Loading
        viewModelScope.launch {
            val table = try {
                source.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _rates.value = table?.let { RatesState.Ready(it) } ?: RatesState.Unavailable
        }
    }

    /**
     * The creator or the admin principal picks the currency; the euro takes it back. [saved] turns true
     * once the write is accepted, or still pending after [writeTimeoutMillis] (queued offline).
     */
    fun setCurrency(code: String) {
        val repo = trips ?: return
        if (_saving.value) return
        _saveFailed.value = false
        _saving.value = true
        viewModelScope.launch {
            val ok = try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.setCurrency(tripId, code.takeIf { it != "EUR" }) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _saving.value = false
            if (ok) _saved.value = true else _saveFailed.value = true
        }
    }
}

/** [amount] in a currency worth [perEuro] for 1 €, in euros; null when there is nothing to convert. */
fun toEuro(amount: Double?, perEuro: Double?): Double? =
    if (amount == null || perEuro == null || perEuro <= 0) null else amount / perEuro

/** [euros] in a currency worth [perEuro] for 1 €. */
fun fromEuro(euros: Double?, perEuro: Double?): Double? =
    if (euros == null || perEuro == null || perEuro <= 0) null else euros * perEuro

/** « 12,5 », « 12.5 » or « 1 200 » as typed on a French keyboard; null when it is not a number. */
fun parseAmount(text: String): Double? =
    text.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
