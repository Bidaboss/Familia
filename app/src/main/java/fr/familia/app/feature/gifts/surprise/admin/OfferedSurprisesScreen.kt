package fr.familia.app.feature.gifts.surprise.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.OfferedSurprise
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface OfferedSurprisesState {
    data object Loading : OfferedSurprisesState
    data object Error : OfferedSurprisesState
    data class Ready(val surprises: List<OfferedSurprise>) : OfferedSurprisesState
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/**
 * « Surprises offertes récemment » (brief § 9): the title, the date and the person, never what was
 * inside; « Annuler C'est offert » with a confirmation puts it back « À confirmer » for its members.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferedSurprisesScreen(
    state: OfferedSurprisesState,
    onBack: () -> Unit,
    onCancel: (String) -> Unit,
    actionFailed: Boolean = false,
    onFailureShown: () -> Unit = {},
) {
    var cancelling by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.offered_surprises_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                OfferedSurprisesState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                OfferedSurprisesState.Error -> EmptyState(
                    icon = Icons.Outlined.CardGiftcard,
                    title = stringResource(R.string.offered_surprises_error_title),
                    body = stringResource(R.string.offered_surprises_error_body),
                )
                is OfferedSurprisesState.Ready -> if (state.surprises.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.CardGiftcard,
                        title = stringResource(R.string.offered_surprises_empty_title),
                        body = stringResource(R.string.offered_surprises_empty_body),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "intro") {
                            Text(stringResource(R.string.offered_surprises_intro), style = MaterialTheme.typography.bodyLarge)
                        }
                        items(state.surprises, key = { it.id }) { surprise ->
                            OfferedCard(surprise, onCancel = { cancelling = surprise.id })
                        }
                    }
                }
            }
        }
    }
    val surprises = (state as? OfferedSurprisesState.Ready)?.surprises.orEmpty()
    surprises.firstOrNull { it.id == cancelling }?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(stringResource(R.string.offered_surprises_cancel_title)) },
            text = { Text(stringResource(R.string.offered_surprises_cancel_body, target.title, target.beneficiaryName)) },
            confirmButton = {
                TextButton(onClick = { cancelling = null; onCancel(target.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.offered_surprises_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
    if (actionFailed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            text = { Text(stringResource(R.string.offered_surprises_failed)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun OfferedCard(surprise: OfferedSurprise, onCancel: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), tint = ClayTint.Family) {
        Text(surprise.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            surprise.date?.let { stringResource(R.string.offered_surprises_for_on, surprise.beneficiaryName, dayFormat.format(it)) }
                ?: stringResource(R.string.offered_surprises_for, surprise.beneficiaryName),
            style = MaterialTheme.typography.bodyLarge,
        )
        surprise.offeredAt?.let {
            Text(
                stringResource(R.string.offered_surprises_offered_on, dayFormat.format(it.atZone(ZoneId.systemDefault()))),
                style = MaterialTheme.typography.bodySmall,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Family),
            )
        }
        if (surprise.cancelAsked) {
            Text(
                stringResource(R.string.offered_surprises_cancel_asked),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Family),
            )
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.offered_surprises_cancel))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OfferedSurprisesScreenPreview() {
    FamiliaTheme {
        OfferedSurprisesScreen(
            OfferedSurprisesState.Ready(
                listOf(
                    OfferedSurprise("a", "Les 40 ans", LocalDate.of(2026, 11, 20), "Adulte", Instant.parse("2026-11-20T18:00:00Z"), false),
                    OfferedSurprise("b", "Noël", LocalDate.of(2026, 11, 2), "Enfant", Instant.parse("2026-11-02T18:00:00Z"), true),
                ),
            ),
            onBack = {},
            onCancel = {},
        )
    }
}
