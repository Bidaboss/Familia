package fr.familia.app.feature.planning

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/** Planning tab. Placeholder until lot 5 brings the upcoming list and the month view. */
@Composable
fun PlanningScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Event,
        title = stringResource(R.string.planning_empty_title),
        body = stringResource(R.string.planning_empty_body),
        modifier = modifier,
    )
}

@FamiliaPreviews
@Composable
private fun PlanningScreenPreview() {
    FamiliaTheme { Surface { PlanningScreen() } }
}
