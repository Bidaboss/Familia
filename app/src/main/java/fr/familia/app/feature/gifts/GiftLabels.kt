package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.ui.formatDay
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun occasionLabel(occasion: GiftOccasion): String = stringResource(
    when (occasion) {
        GiftOccasion.Christmas -> R.string.gift_occasion_christmas
        GiftOccasion.Birthday -> R.string.gift_occasion_birthday
        GiftOccasion.Birth -> R.string.gift_occasion_birth
        GiftOccasion.Other -> R.string.gift_occasion_other
    },
)

/** The title shown for a list: « Mes envies », « Envies de Camille », or its own title. */
@Composable
fun listTitle(list: GiftList, beneficiaryName: String, isMine: Boolean): String = when {
    list.kind == GiftListKind.Ideas -> stringResource(R.string.gift_ideas_of, beneficiaryName)
    list.kind != GiftListKind.Wishes -> list.title
    isMine -> stringResource(R.string.gift_my_wishes)
    else -> stringResource(R.string.gift_wishes_of, beneficiaryName)
}

/** « Noël · Vendredi 25 décembre », with « Surprise pour Camille » in front of a surprise list. */
@Composable
fun listSubtitle(list: GiftList, beneficiaryName: String, today: LocalDate): String {
    if (list.kind == GiftListKind.Wishes) return stringResource(R.string.gift_wishes_subtitle)
    if (list.kind == GiftListKind.Ideas) return stringResource(R.string.gift_ideas_subtitle, beneficiaryName)
    val occasion = list.occasion?.let { occasionLabel(it) }
    val day = list.date?.let { formatDay(it, today) }
    val base = listOfNotNull(occasion, day).joinToString(" · ")
    return if (list.kind == GiftListKind.Surprise) {
        stringResource(R.string.gift_surprise_for, beneficiaryName) + if (base.isEmpty()) "" else " · $base"
    } else {
        base
    }
}

/** « 29,90 € », the French way, without decimals for a round amount. */
fun formatEuros(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
    if (amount % 1.0 == 0.0) format.maximumFractionDigits = 0
    return format.format(amount)
}

/** Reads « 29,90 », « 29.9 » or « 30 € » typed by a member; null when it is not an amount. */
fun parseEuros(text: String): Double? {
    val cleaned = text.replace("€", "").replace('\u00A0', ' ').replace('\u202F', ' ').replace(" ", "").replace(',', '.').trim()
    if (cleaned.isEmpty()) return null
    return cleaned.toDoubleOrNull()?.takeIf { it >= 0 && !it.isNaN() && !it.isInfinite() }
}
