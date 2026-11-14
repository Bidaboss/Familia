package fr.familia.app.feature.birthdays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/** Birthdays tab. Placeholder until lot 5 computes the 13 birthdays from the members' birth dates. */
@Composable
fun BirthdaysScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Cake,
        title = stringResource(R.string.birthdays_empty_title),
        body = stringResource(R.string.birthdays_empty_body),
        modifier = modifier,
    )
}

@FamiliaPreviews
@Composable
private fun BirthdaysScreenPreview() {
    FamiliaTheme { Surface { BirthdaysScreen() } }
}
