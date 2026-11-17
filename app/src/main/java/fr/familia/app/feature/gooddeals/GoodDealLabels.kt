package fr.familia.app.feature.gooddeals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.ui.formatDay
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** "Dernier jour aujourd'hui", "Jusqu'à demain" or "Jusqu'au samedi 21 novembre". */
@Composable
fun dealUntilLabel(endsAt: Instant, today: LocalDate): String {
    val last = dealLastDay(endsAt)
    return when {
        !last.isAfter(today) -> stringResource(R.string.gooddeal_until_today)
        last == today.plusDays(1) -> stringResource(R.string.gooddeal_until_tomorrow)
        else -> stringResource(R.string.gooddeal_until, formatDay(last, today).lowercase(Locale.FRANCE))
    }
}

/** The name of a category of bonnes adresses, as the chips and cards show it. */
@Composable
fun categoryLabel(category: GoodDealCategory): String = stringResource(
    when (category) {
        GoodDealCategory.Health -> R.string.gooddeal_category_health
        GoodDealCategory.Home -> R.string.gooddeal_category_home
        GoodDealCategory.Car -> R.string.gooddeal_category_car
        GoodDealCategory.Kids -> R.string.gooddeal_category_kids
        GoodDealCategory.Outings -> R.string.gooddeal_category_outings
        GoodDealCategory.Other -> R.string.gooddeal_category_other
    },
)
