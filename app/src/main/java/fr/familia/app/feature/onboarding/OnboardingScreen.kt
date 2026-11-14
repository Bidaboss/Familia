package fr.familia.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * One screen of the tutorial. Stateless: [index] is the position in [pages]. "Passer" jumps to the
 * last screen, so that the notifications are always explained before the app opens.
 */
@Composable
fun OnboardingScreen(
    pages: List<OnboardingPage>,
    index: Int,
    notificationsAllowed: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAllowNotifications: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = pages[index]
    val isLast = index == pages.lastIndex
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_progress, index + 1, pages.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!isLast) {
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.padding(top = 16.dp)) {
                    Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
                }
                Text(
                    stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(page.bodyRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.size(16.dp))
            when {
                !isLast -> Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.onboarding_next))
                }
                notificationsAllowed -> Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.onboarding_start))
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAllowNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.onboarding_allow_notifications))
                    }
                    TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.onboarding_not_now))
                    }
                }
            }
        }
    }
}

@FamiliaPreviews
@Composable
private fun OnboardingScreenPreview() {
    FamiliaTheme {
        OnboardingScreen(onboardingPages(MemberProfile.Adult), 0, false, {}, {}, {}, {})
    }
}
