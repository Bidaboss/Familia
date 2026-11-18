package fr.familia.app.feature.all

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.toneColors
import java.text.Normalizer
import java.util.Locale

/** The three families of « Tout Familia » (R1 bis, §3), each with the color of its days on the frise. */
enum class ModuleFamily(@StringRes val labelRes: Int, val tone: DayTone) {
    Organise(R.string.all_family_organise, DayTone.Organise),
    Treat(R.string.all_family_treat, DayTone.Party),
    Remember(R.string.all_family_remember, DayTone.Help),
}

/** One round icon of the drawer: only the modules on for me, each opening its screen. */
class AllModule(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val family: ModuleFamily,
    val badge: Int = 0,
    val onOpen: () -> Unit,
)

/** The modules whose name matches [query], accents and capitals aside; all of them for a blank query. */
fun filterModules(modules: List<Pair<AllModule, String>>, query: String): List<AllModule> {
    val wanted = plain(query.trim())
    return modules.filter { (_, name) -> wanted.isEmpty() || plain(name).contains(wanted) }.map { it.first }
}

private fun plain(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.FRANCE)

/**
 * « Tout Familia », like the app drawer of Android in the colors of Familia: « Chercher un module »,
 * then round icons of 60 dp with their name, 4 a line (fewer at a large font size), under the three
 * families. The badge counts what is new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFamiliaScreen(modules: List<AllModule>, onBack: () -> Unit, note: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val named = modules.map { it to stringResource(it.labelRes) }
    val shown = filterModules(named, query)
    val fontScale = LocalDensity.current.fontScale
    val perLine = when {
        fontScale >= 1.8f -> 2
        fontScale >= 1.3f -> 3
        else -> 4
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_r1_all)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.all_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            note?.let { text ->
                item(key = "note") { MovedNote(text) }
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(stringResource(R.string.all_none), style = MaterialTheme.typography.bodyLarge)
                }
            }
            ModuleFamily.entries.forEach { family ->
                val inFamily = shown.filter { it.family == family }
                if (inFamily.isNotEmpty()) {
                    item(key = "family-${family.name}") { SectionLabel(stringResource(family.labelRes)) }
                    inFamily.chunked(perLine).forEachIndexed { line, row ->
                        item(key = "line-${family.name}-$line") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { module -> ModuleIcon(module, Modifier.weight(1f)) }
                                repeat(perLine - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleIcon(module: AllModule, modifier: Modifier) {
    val tone = toneColors(module.family.tone)
    val news = if (module.badge > 0) pluralStringResource(R.plurals.home_r1_all_news, module.badge, module.badge) else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = module.onOpen)
            .semantics(mergeDescendants = true) { if (news != null) stateDescription = news }
            .padding(vertical = 6.dp),
    ) {
        BadgedBox(
            badge = {
                if (module.badge > 0) {
                    Badge(containerColor = Color(0xFFF2C14E), contentColor = Color(0xFF1B1B1B)) { Text(module.badge.toString()) }
                }
            },
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp).clip(CircleShape).background(tone.pill)) {
                Icon(module.icon, contentDescription = null, tint = tone.text)
            }
        }
        Text(
            stringResource(module.labelRes),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The green note of « Se souvenir » (récit 71, maquette SF3): where the other modules went. Shown
 * on the first visit after the update only; the next visits no longer show it.
 */
@Composable
fun rememberMemoriesNote(show: Boolean): Boolean {
    val context = LocalContext.current
    val firstTime = remember(show) {
        show && runCatching {
            val prefs = context.getSharedPreferences(ALL_PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getBoolean(MEMORIES_NOTE_SEEN, false)
            if (!seen) prefs.edit().putBoolean(MEMORIES_NOTE_SEEN, true).apply()
            !seen
        }.getOrDefault(false)
    }
    return firstTime
}

private const val ALL_PREFS = "familia_all"
private const val MEMORIES_NOTE_SEEN = "memoriesNoteSeen"

@Composable
private fun MovedNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF1F5C3C),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEDF7F0))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
