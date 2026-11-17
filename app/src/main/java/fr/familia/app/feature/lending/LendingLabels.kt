package fr.familia.app.feature.lending

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.LendCategory

/** The name of a category of things to lend, as the chips and cards show it. */
@Composable
fun lendCategoryLabel(category: LendCategory): String = stringResource(
    when (category) {
        LendCategory.Kitchen -> R.string.lend_category_kitchen
        LendCategory.Diy -> R.string.lend_category_diy
        LendCategory.Baby -> R.string.lend_category_baby
        LendCategory.Sport -> R.string.lend_category_sport
        LendCategory.Travel -> R.string.lend_category_travel
        LendCategory.Other -> R.string.lend_category_other
    },
)
