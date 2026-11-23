package fr.familia.app.feature.trips.phrases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import java.util.Locale

/** « Phrases utiles » of [tripId] (récit 105, règle 44): one block per country abroad, read by anyone who follows the trip. */
@Composable
fun TripPhrasesRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-phrases-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by detail.uiState.collectAsStateWithLifecycle()
    val steps = (state as? TripDetailUiState.Success)?.steps.orEmpty()
    val countries = PhraseBook.abroad(rememberAbroadCountries(container, steps))
    TripPhrasesScreen(loading = state !is TripDetailUiState.Success, countries = countries, speaker = rememberSpeaker(), onBack = onBack)
}

/** Stateless. */
@Composable
fun TripPhrasesScreen(loading: Boolean, countries: List<String>, speaker: Speaker?, onBack: () -> Unit) {
    Scaffold(topBar = { FamiliaTopBar(title = { Text(stringResource(R.string.phrases_title)) }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        if (loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.phrases_intro), style = MaterialTheme.typography.bodyLarge)
            if (countries.isEmpty()) {
                Text(stringResource(R.string.phrases_none), style = MaterialTheme.typography.bodyLarge)
            }
            // Two countries that speak the same language show it once.
            countries.groupBy { PhraseBook.languageOf(it) }.forEach { (language, codes) ->
                if (language != null) LanguageBlock(language, codes, speaker)
            }
        }
    }
}

@Composable
private fun LanguageBlock(language: PhraseLanguage, codes: List<String>, speaker: Speaker?) {
    val countries = codes.joinToString(", ") { code -> Locale("", code).getDisplayCountry(Locale.FRANCE).replaceFirstChar { it.titlecase(Locale.FRANCE) } }
    Text(
        stringResource(R.string.phrases_language, countries, language.frenchName),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    PhraseBook.phrases(language).forEach { phrase ->
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(phrase.french, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phrase.local, style = MaterialTheme.typography.titleMedium)
                Text(phrase.pronunciation, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            if (speaker != null && language in speaker.available) {
                IconButton(onClick = { speaker.say(phrase.local, language) }, Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Campaign, contentDescription = stringResource(R.string.phrases_say, phrase.local))
                }
            }
        }
    }
}
