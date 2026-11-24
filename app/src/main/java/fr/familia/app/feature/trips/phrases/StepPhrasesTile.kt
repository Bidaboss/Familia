package fr.familia.app.feature.trips.phrases

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.TripTile

/** What the page of a step shows of « Phrases utiles »: the language of its country and the first phrases. */
data class StepPhrases(val country: String, val language: PhraseLanguage, val phrases: List<Phrase>)

/** How many phrases the page of a step shows; « Les 10 phrases » opens the rest. */
const val STEP_PHRASES_SHOWN = 3

/** The phrases of [countryCode] for the page of a step, or null: France, no country, or no table yet. */
fun stepPhrases(countryCode: String?): StepPhrases? {
    val code = PhraseBook.abroad(listOfNotNull(countryCode)).firstOrNull() ?: return null
    val language = PhraseBook.languageOf(code) ?: return null
    return StepPhrases(code, language, PhraseBook.phrases(language).take(STEP_PHRASES_SHOWN))
}

/**
 * « Phrases utiles » on the page of a step abroad (récit 112, règle 44): a large tile high on the page,
 * the first phrases with their speaker, and « Les 10 phrases » for the language of that country.
 * Nothing in France, nor while the phone has not placed the step.
 */
@Composable
fun StepPhrasesTile(container: AppContainer?, step: TripStep, onOpen: ((country: String) -> Unit)?) {
    val steps = remember(step.id, step.lat, step.lng) { listOf(step) }
    val shown = stepPhrases(rememberAbroadCountries(container, steps).firstOrNull()) ?: return
    val speaker = rememberSpeaker()
    TripTile(Icons.Outlined.QuestionAnswer, stringResource(R.string.phrases_step_title, shown.language.frenchName)) {
        shown.phrases.forEach { phrase -> PhraseRow(phrase, shown.language, speaker) }
        if (onOpen != null) {
            Button(onClick = { onOpen(shown.country) }, modifier = Modifier.padding(top = 4.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.phrases_step_all, PhraseBook.COUNT))
            }
        }
    }
}
