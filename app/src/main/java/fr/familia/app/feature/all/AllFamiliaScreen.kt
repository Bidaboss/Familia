package fr.familia.app.feature.all

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.LocalPhotoHaze
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.core.theme.PhotoBackdrop
import fr.familia.app.core.theme.PhotoScroll
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.PhotoTitleShadow
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.ModuleEmoji
import java.text.Normalizer
import java.util.Locale

/** The three groups of « Tout Familia » (récit 120), each with the color of its rounds. */
enum class ModuleFamily(@StringRes val labelRes: Int, val round: Color) {
    Organise(R.string.all_family_organise, Color(0xFFE3EEF8)),
    Treat(R.string.all_family_treat, Color(0xFFFBE3EE)),
    Remember(R.string.all_family_remember, Color(0xFFE4F0DA)),
}

/**
 * What « Tout Familia » may show, in its exact order (récit 120, brief §3): its picto, its name, its
 * group. Which ones show depends on the modules switched on (FamiliaApp.allModules).
 */
enum class ModuleSpec(
    val key: String,
    @StringRes val labelRes: Int,
    val emoji: String,
    val family: ModuleFamily,
    val double: Boolean = false,
) {
    Planning("planning", R.string.all_planning, "📅", ModuleFamily.Organise),
    Help("help", R.string.all_help, "🤝", ModuleFamily.Organise),
    GoodDeals("goodDeals", R.string.entraide_tab_good_deals, "🏷️", ModuleFamily.Organise),
    Lending("lending", R.string.entraide_tab_lending, "📦", ModuleFamily.Organise),
    Polls("polls", R.string.all_polls, "📊", ModuleFamily.Organise),
    /** Until « Dire où je suis » of the gazette (récit 117): then it leaves the menu. */
    Where("where", R.string.all_where, "📍", ModuleFamily.Organise),
    Gifts("gifts", R.string.all_gifts, "🎁", ModuleFamily.Treat, double = true),
    Trips("trips", R.string.all_trips, "🧳", ModuleFamily.Treat, double = true),
    Recipes("recipes", R.string.all_recipes, "📖", ModuleFamily.Treat),
    Challenges("challenges", R.string.all_challenges, "🏆", ModuleFamily.Treat),
    Albums("albums", R.string.all_albums, "🖼️", ModuleFamily.Remember),
    Capsule("capsule", R.string.all_capsule, "⏳", ModuleFamily.Remember),
    YearInReview("yearInReview", R.string.all_year_in_review, "✨", ModuleFamily.Remember),
    WhoIsIt("whoIsIt", R.string.all_who_is_it, "🧐", ModuleFamily.Remember),
}

/** One round of the menu: only the modules on for me, each opening its screen. */
class AllModule(
    val spec: ModuleSpec,
    val badge: Int = 0,
    val onOpen: () -> Unit,
) {
    val key: String get() = spec.key
    val family: ModuleFamily get() = spec.family
}

/** The modules whose name matches [query], accents and capitals aside; all of them for a blank query. */
fun filterModules(modules: List<Pair<AllModule, String>>, query: String): List<AllModule> {
    val wanted = plain(query.trim())
    return modules.filter { (_, name) -> wanted.isEmpty() || plain(name).contains(wanted) }.map { it.first }
}

private fun plain(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.FRANCE)

/** How many rounds a line holds: 4, then 3 from a text at 130 %, 2 from 180 % (brief §2). */
fun modulesPerLine(fontScale: Float): Int = when {
    fontScale >= 1.8f -> 2
    fontScale >= 1.3f -> 3
    else -> 4
}

/**
 * « Tout Familia » on one screen, without scrolling at the normal text size (récit 120): the title,
 * « Chercher un module » just under it, then each group, its title above a card of glass on the photo
 * of the theme, its rounds of 56 dp with an emoji in color, 4 a line. The badge counts what is new.
 * From a text at 130 % the screen may scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFamiliaScreen(modules: List<AllModule>, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val named = modules.map { it to stringResource(it.spec.labelRes) }
    val shown = filterModules(named, query)
    val perLine = modulesPerLine(LocalDensity.current.fontScale)
    val photo = LocalPhotoTheme.current
    val scroll = remember { PhotoScroll() }
    Box(Modifier.fillMaxSize().then(if (photo == null) Modifier.themeBackground() else Modifier)) {
        CompositionLocalProvider(LocalPhotoHaze provides remember { dev.chrisbanes.haze.HazeState() }) {
            if (photo != null) PhotoBackdrop(photo, scroll)
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    FamiliaTopBar(
                        title = {
                            Text(
                                stringResource(R.string.home_r1_all),
                                style = if (photo != null) LocalTextStyle.current.copy(shadow = PhotoTitleShadow) else LocalTextStyle.current,
                            )
                        },
                        navigationIcon = { BackButton(onClick = onBack) },
                        contentColor = if (photo != null) PhotoSectionText else Color.Unspecified,
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(scroll.connection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ClaySpacing.screen)
                        .padding(bottom = 24.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.all_search)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LocalClayColors.current.card,
                            unfocusedContainerColor = LocalClayColors.current.card,
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(R.string.all_none),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, shadow = if (photo != null) PhotoSectionShadow else null),
                            color = if (photo != null) PhotoSectionText else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                        )
                    }
                    ModuleFamily.entries.forEach { family ->
                        val inFamily = shown.filter { it.family == family }
                        if (inFamily.isNotEmpty()) {
                            Text(
                                stringResource(family.labelRes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = if (photo != null) PhotoSectionShadow else null,
                                ),
                                color = if (photo != null) PhotoSectionText else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp).semantics { heading() },
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (photo != null) {
                                            Modifier.photoCard(24.dp)
                                        } else {
                                            Modifier.clip(RoundedCornerShape(24.dp)).background(LocalClayColors.current.card)
                                        },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                            ) {
                                inFamily.chunked(perLine).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { module -> ModuleRound(module, Modifier.weight(1f)) }
                                        repeat(perLine - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The yellow of the badge of what is new, with its white edge of 2 dp. */
private val NewsYellow = Color(0xFFF2C14E)

@Composable
private fun ModuleRound(module: AllModule, modifier: Modifier) {
    val news = if (module.badge > 0) pluralStringResource(R.plurals.home_r1_all_news, module.badge, module.badge) else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = module.onOpen)
            // TalkBack reads « Entraide, 2 nouveautés »: the emoji and the badge are decorative.
            .semantics(mergeDescendants = true) { if (news != null) stateDescription = news }
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).clip(CircleShape).background(module.family.round)) {
                ModuleEmoji(module.spec.emoji, size = if (module.spec.double) 25.sp else 26.sp, double = module.spec.double)
            }
            if (module.badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .heightIn(min = 22.dp)
                        .widthIn(min = 22.dp)
                        .clip(CircleShape)
                        .background(NewsYellow)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(horizontal = 6.dp)
                        .clearAndSetSemantics { },
                ) {
                    Text(module.badge.toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B1B))
                }
            }
        }
        Text(
            stringResource(module.spec.labelRes),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = Color(0xFF1E1B18),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
