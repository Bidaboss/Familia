package fr.familia.app.feature.trips.phrases

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * The voice of the phone (récit 105): it says a phrase in the language of the country, never in
 * French. A language whose voice is not installed is not in [available], so its speaker is hidden
 * instead of reading a foreign phrase with a French accent; once [checked], the screen says so in a
 * line (règle 44, audit du 24/11).
 */
class Speaker internal constructor(context: Context) {
    private var engine: TextToSpeech? = null

    /** The languages the phone can speak, known once the engine is ready. */
    var available by mutableStateOf<Set<PhraseLanguage>>(emptySet())
        private set

    /** True once the voices were looked at: before, nothing is said about a missing one. */
    var checked by mutableStateOf(false)
        private set

    /** True once the engine said it is ready; the callback may come before [engine] is assigned. */
    @Volatile private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                refresh()
            } else {
                // No voice engine at all: every language is said missing.
                checked = true
            }
        }
        refresh()
    }

    private fun refresh() {
        val tts = engine ?: return
        if (!ready) return
        available = PhraseLanguage.entries.filter { tts.isLanguageAvailable(it.locale) >= TextToSpeech.LANG_AVAILABLE }.toSet()
        checked = true
    }

    /** Looks again, back from the screen that installs a voice. */
    fun recheck() = refresh()

    fun say(text: String, language: PhraseLanguage) {
        val tts = engine ?: return
        if (language !in available) return
        tts.language = language.locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "phrase")
    }

    internal fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { speaker.recheck() }
    return speaker
}

/** Opens the screen of the phone that installs the voices; false when the phone has none. */
fun openVoiceInstall(context: Context): Boolean = try {
    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Whether to say that the voice of [language] is missing: only once the voices were looked at. */
fun voiceMissing(checked: Boolean, available: Set<PhraseLanguage>, language: PhraseLanguage): Boolean = checked && language !in available
