package fr.familia.app.feature.trips.phrases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.TripPage
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
import java.util.Locale

/**
 * « Phrases utiles » of [tripId] (récit 105, règle 44): one block per country abroad, read by anyone who
 * follows the trip. From the page of a step (récit 112), [country] keeps only the language of that step.
 */
@Composable
fun TripPhrasesRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit, country: String? = null) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-phrases-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by detail.uiState.collectAsStateWithLifecycle()
    val steps = (state as? TripDetailUiState.Success)?.steps.orEmpty()
    val abroad = PhraseBook.abroad(rememberAbroadCountries(container, steps))
    val countries = if (country == null) abroad else PhraseBook.abroad(listOf(country))
    TripPhrasesScreen(loading = state !is TripDetailUiState.Success, countries = countries, speaker = rememberSpeaker(), onBack = onBack)
}

/** Stateless. The light blue page of the trips (fiche F3): one white card of phrases per language. */
@Composable
fun TripPhrasesScreen(loading: Boolean, countries: List<String>, speaker: Speaker?, onBack: () -> Unit) {
    TripPage(title = stringResource(R.string.phrases_title), onBack = onBack) {
        if (loading) {
            LoadingState()
            return@TripPage
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.phrases_intro), fontSize = 15.sp, color = TripPathColors.Ink.copy(alpha = 0.8f))
            if (countries.isEmpty()) {
                Text(stringResource(R.string.phrases_none), fontSize = 16.sp)
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
        fontFamily = Fredoka,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    // Règle 44: a missing voice is said, never just a speaker that is not there.
    if (speaker != null && voiceMissing(speaker.checked, speaker.available, language)) {
        val context = LocalContext.current
        // No screen of voices on this phone (C1 of #568): the button goes, the way through the settings is said.
        var noInstaller by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.phrases_voice_missing, language.frenchName),
                fontSize = 14.sp,
                color = TripPathColors.Ink.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (!noInstaller) {
                TextButton(
                    onClick = { noInstaller = !openVoiceInstall(context) },
                    modifier = Modifier.heightIn(min = 56.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
                ) {
                    Text(stringResource(R.string.phrases_voice_install), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        if (noInstaller) {
            Text(stringResource(R.string.phrases_voice_settings), fontSize = 14.sp, color = TripPathColors.Ink.copy(alpha = 0.75f))
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = TripPathColors.Ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            PhraseBook.phrases(language).forEachIndexed { index, phrase ->
                if (index > 0) HorizontalDivider(color = TripPathColors.Ink.copy(alpha = 0.08f))
                PhraseRow(phrase, language, speaker)
            }
        }
    }
}

/** The cream of the speaker (fiche F3). */
private val SpeakerCream = Color(0xFFFFF1D6)

/**
 * A phrase in the language of the country in bold, then « French · pronunciation », with its cream
 * speaker when the voice is installed (fiche F3).
 */
@Composable
internal fun PhraseRow(phrase: Phrase, language: PhraseLanguage, speaker: Speaker?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(phrase.local, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                buildAnnotatedString {
                    append(phrase.french)
                    append(" · ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(phrase.pronunciation) }
                },
                fontSize = 13.sp,
                color = LocalContentColor.current.copy(alpha = 0.75f),
            )
        }
        if (speaker != null && language in speaker.available) {
            Surface(
                onClick = { speaker.say(phrase.local, language) },
                shape = CircleShape,
                color = SpeakerCream,
                contentColor = TripPathColors.Ink,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Campaign, contentDescription = stringResource(R.string.phrases_say, phrase.local))
                }
            }
        }
    }
}
