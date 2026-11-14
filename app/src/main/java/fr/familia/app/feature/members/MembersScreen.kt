package fr.familia.app.feature.members

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * Famille tab. Placeholder until lot 3 brings members and households.
 * It already shows the installed version, which helps when checking updates by phone.
 */
@Composable
fun MembersScreen(
    modifier: Modifier = Modifier,
    versionName: String = BuildConfig.VERSION_NAME,
) {
    Column(modifier = modifier) {
        EmptyState(
            icon = Icons.Outlined.Groups,
            title = stringResource(R.string.members_empty_title),
            body = stringResource(R.string.members_empty_body),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.app_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@FamiliaPreviews
@Composable
private fun MembersScreenPreview() {
    FamiliaTheme { Surface { MembersScreen(versionName = "0.1.0") } }
}
