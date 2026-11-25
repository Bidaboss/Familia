package fr.familia.app.feature.gifts.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.photo.CoverThumbnail
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.LocalPhotoTheme
import fr.familia.app.core.theme.PhotoSectionShadow
import fr.familia.app.core.theme.PhotoSectionText
import fr.familia.app.core.theme.photoCard
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.gifts.surpriseAccent
import fr.familia.app.feature.gifts.surpriseDark
import fr.familia.app.feature.gifts.wishOccasionLabel
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.home.PhotoSegmentedSwitch

/**
 * A list of wishes, redone after the prototype (correctif E2 à E4, fiches K2, K3, K7): on the photo of
 * the theme, a header of white rounds, glass rows of 64 dp with a vignette.
 * - My own wishes (E2): « prix · occasion », ⋯ to edit, never who reserves; « + Ajouter une envie ».
 * - Someone else's (E3, E4): « Ses envies | Mes idées ». Their wishes with « prix · état » and
 *   « nouveau »; my private ideas for them from my notebook, which nobody else ever sees.
 * Without a photo theme, the same screen on the theme's background.
 */
@Composable
internal fun WishListScreen(
    state: GiftListUiState.Success,
    actions: GiftListActions,
    /** My notebook ideas for the person; null hides « Mes idées » (notebook off, « Voir comme »). */
    ideas: List<NotebookEntry>?,
    /** The wishes added since my last visit, by item id. */
    freshIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    val photo = LocalPhotoTheme.current
    val owner = state.isMine || state.keptFromMe
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openItemId by rememberSaveable { mutableStateOf<String?>(null) }
    // The page of one of my wishes (correctif E9), over the list; back closes it.
    var mineId by rememberSaveable { mutableStateOf<String?>(null) }
    val mine = state.items.firstOrNull { it.item.id == mineId }?.item?.takeIf { owner }
    if (mine != null) {
        BackHandler { mineId = null }
        MyWishDetail(
            item = mine,
            onBack = { mineId = null },
            onEdit = { actions.onEditItem(mine.id) },
            onSetOccasion = { actions.onSetWishOccasion(mine.id, it) },
            onRemove = {
                mineId = null
                actions.onRemoveWish(mine.id)
            },
        )
        return
    }
    Box(modifier.fillMaxSize().then(if (photo == null) Modifier.themeBackground() else Modifier)) {
        // The photo lies behind the whole app (FamiliaApp), as behind the tab: nothing flashes cream between them.
        Column(Modifier.fillMaxSize()) {
            WishHeader(
                title = if (owner) stringResource(R.string.gift_my_wishes) else state.beneficiary.firstName,
                onBack = actions.onBack,
                onShare = actions.onShareOutside?.takeIf { state.canSendOutside },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val showsIdeas = !owner && ideas != null
                if (showsIdeas) {
                    item(key = "tabs") {
                        PhotoSegmentedSwitch(
                            labels = listOf(stringResource(R.string.wishlist_tab_theirs), stringResource(R.string.wishlist_tab_my_ideas)),
                            counts = listOf(0, 0),
                            chosen = tab,
                            accent = surpriseAccent(),
                            dark = surpriseDark(),
                            onChoose = { tab = it },
                        )
                    }
                }
                if (showsIdeas && tab == 1) {
                    ideasTab(state.beneficiary.firstName, ideas.orEmpty(), actions)
                } else {
                    wishesTab(state, owner, freshIds, actions, onOpen = { openItemId = it }, onOpenMine = { mineId = it })
                }
            }
        }
    }
    state.items.firstOrNull { it.item.id == openItemId }?.let { row ->
        ItemSheet(row, state, actions, onDismiss = { openItemId = null })
    }
}

private fun LazyListScope.wishesTab(
    state: GiftListUiState.Success,
    owner: Boolean,
    freshIds: Set<String>,
    actions: GiftListActions,
    onOpen: (String) -> Unit,
    onOpenMine: (String) -> Unit,
) {
    item(key = "notice") {
        val name = state.beneficiary.firstName
        val notice = when {
            state.list.revealed -> stringResource(R.string.gift_list_revealed)
            state.keptFromReader -> stringResource(R.string.view_as_gift_list_kept)
            owner -> stringResource(R.string.wishlist_mine_notice)
            else -> stringResource(R.string.wishlist_theirs_notice, name)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            OnPhotoText(notice)
            ActionMessage(state)
        }
    }
    if (state.items.isEmpty()) {
        item(key = "empty") {
            GlassRow(onClick = null, onClickLabel = null) {
                Text(
                    if (owner) stringResource(R.string.gift_list_empty_mine) else stringResource(R.string.wishlist_theirs_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
    items(state.items, key = { it.item.id }) { row ->
        val item = row.item
        val edits = owner && row.canEdit
        GlassRow(
            // My own wish opens its page (correctif E9, règle 155), someone else's its sheet.
            onClick = { if (edits) onOpenMine(item.id) else onOpen(item.id) },
            onClickLabel = stringResource(R.string.gift_item_details),
        ) {
            Vignette(item.title, size = if (owner) 44 else 52) { WishPicture(item) }
            Column(Modifier.weight(1f)) {
                RowTitle(item.title)
                val line = if (owner) ownerLine(item) else theirLine(row, state, item.id in freshIds)
                Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (edits) {
                IconButton(onClick = { onOpenMine(item.id) }, modifier = Modifier.size(56.dp)) {
                    val label = stringResource(R.string.wishlist_edit_a11y, item.title)
                    Text("⋯", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = label })
                }
            } else {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    // « Cadeaux communs » (récit 73): a wish of the household shared with the person, opened in its list.
    items(state.sharedWishes, key = { "shared-${it.listId}-${it.item.id}" }) { shared ->
        GlassRow(
            onClick = { actions.onOpenList(shared.listId) },
            onClickLabel = stringResource(R.string.gift_shared_wish_open, shared.ownerName),
        ) {
            Vignette(shared.item.title, size = 52) { WishPicture(shared.item) }
            Column(Modifier.weight(1f)) {
                RowTitle(shared.item.title)
                val details = listOfNotNull(
                    shared.item.price?.let { formatEuros(it) },
                    stringResource(R.string.gift_item_co_with, shared.ownerName),
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
    if (state.canAddItems) {
        item(key = "add") {
            Button(
                onClick = actions.onAddItem,
                colors = ButtonDefaults.buttonColors(containerColor = surpriseDark(), contentColor = Color.White),
                // Full-width action of Cadeaux: 48 dp (règle 57 of regles-cadeaux-themes-photo.md, l'admin 25/11).
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("+ " + stringResource(R.string.gift_add_wish), fontWeight = FontWeight.Bold) }
        }
    }
}

private fun LazyListScope.ideasTab(name: String, ideas: List<NotebookEntry>, actions: GiftListActions) {
    item(key = "lock") {
        Surface(shape = RoundedCornerShape(20.dp), color = IdeasBanner, contentColor = GlassText, modifier = Modifier.fillMaxWidth()) {
            Text("🔒 " + stringResource(R.string.wishlist_ideas_private, name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
    items(ideas, key = { "idea-${it.id}" }) { idea ->
        GlassRow(onClick = { actions.onOpenIdea(idea.id) }, onClickLabel = stringResource(R.string.gift_item_edit_action)) {
            Vignette(idea.title, size = 52) { Text("💡", fontSize = 24.sp) }
            Column(Modifier.weight(1f)) {
                RowTitle(idea.title)
                val accent = surpriseDark()
                val only = stringResource(R.string.wishlist_idea_only_me)
                Text(
                    buildAnnotatedString {
                        idea.price?.let { append(formatEuros(it)); append(" · ") }
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append("🔒 $only") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
    actions.onAddIdea?.let { add ->
        item(key = "add-idea") {
            val edge = GlassText.copy(alpha = 0.6f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.55f))
                    .drawBehind {
                        drawRoundRect(
                            edge,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                        )
                    }
                    .clickable(role = Role.Button, onClick = add),
                contentAlignment = Alignment.Center,
            ) {
                Text("+ " + stringResource(R.string.gift_add_idea), style = MaterialTheme.typography.titleMedium, color = GlassText)
            }
        }
    }
}

/** « 49 € · Anniversaire », the occasion in the dark of the theme; « Déjà acheté · participation souhaitée » when so. */
@Composable
private fun ownerLine(item: GiftItem) = buildAnnotatedString {
    item.price?.let { append(formatEuros(it)); append(" · ") }
    val what = if (item.alreadyBought) stringResource(R.string.wishlist_already_bought) else wishOccasionLabel(item.occasion, true)
    withStyle(SpanStyle(color = surpriseDark(), fontWeight = FontWeight.Bold)) { append(what) }
}

/**
 * « 49 € · Libre · nouveau »: the state of the wish in its color, as far as I may know it (fiche K2):
 * in the surprises I am in for the person, or already bought, or reserved before la bascule.
 */
@Composable
private fun theirLine(row: ItemRow, state: GiftListUiState.Success, fresh: Boolean) = buildAnnotatedString {
    val item = row.item
    val status: Pair<String, Color>? = wishStatus(row, state)
    val parts = listOfNotNull(item.price?.let { formatEuros(it) })
    append(parts.joinToString(" · "))
    val colored = listOfNotNull(status?.first, if (fresh) stringResource(R.string.wishlist_new) else null)
    if (colored.isNotEmpty()) {
        if (parts.isNotEmpty()) append(" · ")
        withStyle(SpanStyle(color = status?.second ?: StatusFree, fontWeight = FontWeight.Bold)) { append(colored.joinToString(" · ")) }
    }
}

@Composable
private fun wishStatus(row: ItemRow, state: GiftListUiState.Success): Pair<String, Color>? {
    val claim = row.claim
    return when {
        row.item.alreadyBought || row.contributions != null -> stringResource(R.string.wishlist_already_bought) to StatusBought
        else -> when (val inSurprise = state.wishStates[row.item.id]) {
            WishSurpriseState.Free -> stringResource(R.string.gift_surprise_free) to StatusFree
            is WishSurpriseState.Taken ->
                (if (inSurprise.mine) stringResource(R.string.wishlist_taken_by_me) else stringResource(R.string.surprise_taken_by, inSurprise.by)) to StatusTaken
            is WishSurpriseState.Joint -> stringResource(R.string.gift_surprise_joint, inSurprise.gathered, inSurprise.goal) to StatusJoint
            null -> when {
                claim == null -> null
                claim.type == ClaimType.Shared -> stringResource(R.string.wishlist_joint) to StatusJoint
                claim.isMine -> stringResource(R.string.wishlist_taken_by_me) to StatusTaken
                else -> stringResource(R.string.wishlist_reserved) to StatusTaken
            }
        }
    }
}

/** Back on the left, the title in the middle, « ? » (and « Partager » when it may) on the right: white rounds on the photo. */
@Composable
private fun WishHeader(title: String, onBack: () -> Unit, onShare: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhiteRound { BackButton(onClick = onBack) }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.merge(TextStyle(shadow = PhotoSectionShadow)),
            fontWeight = FontWeight.ExtraBold,
            color = PhotoSectionText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onShare?.let { share -> WhiteRound { ShareButton(onClick = share) } }
            WhiteRound { HelpButton(ScreenHelp.GiftList) }
        }
    }
}

@Composable
private fun WhiteRound(content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = Color.White, contentColor = GlassText, shadowElevation = 2.dp) { content() }
}

/** A sentence that lies on the photo: white with a shadow, dark on the cream. */
@Composable
private fun OnPhotoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.merge(TextStyle(shadow = PhotoSectionShadow)),
        color = PhotoSectionText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A row of glass, 64 dp at least (fiche K2): the whole row is one touch. */
@Composable
private fun GlassRow(onClick: (() -> Unit)?, onClickLabel: String?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val surface = if (LocalPhotoTheme.current != null) {
        Modifier.photoCard(20.dp)
    } else {
        Modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    CompositionLocalProvider(LocalContentColor provides GlassText) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(surface)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun RowTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** The vignette: a pastel square, its color drawn from the title so it stays the same. */
@Composable
private fun Vignette(seed: String, size: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Pastels[Math.floorMod(seed.hashCode(), Pastels.size)]),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The photo of its author (récit 44), else the picture of its shop page, else a gift. */
@Composable
private fun WishPicture(item: GiftItem) {
    val photo = item.photo?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }
    when {
        photo != null -> CoverThumbnail(photo, size = 52.dp)
        item.imageUrl != null -> AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        else -> Text("🎁", fontSize = 24.sp)
    }
}

/** The ids of the wishes added since [since] by someone else, the ones the person sees; none before a first visit. */
internal fun freshWishIds(items: List<GiftItem>, since: Long?, meId: String): Set<String> {
    if (since == null) return emptySet()
    return items.filter { it.visibleToBeneficiary && it.authorId != meId && (it.addedAt?.toEpochMilli() ?: 0L) > since }.map { it.id }.toSet()
}

/** The ink of the glass (A5). */
private val GlassText = Color(0xFF1E1B18)
private val IdeasBanner = Color(0xFFFBF0C2)

// The colors of the states (fiche K2), dark enough on the glass (4.5:1).
private val StatusFree = Color(0xFF1E6B34)
private val StatusTaken = Color(0xFF9A4A0B)
private val StatusJoint = Color(0xFF5B3A99)
private val StatusBought = Color(0xFF1D4E6E)

private val Pastels = listOf(
    Color(0xFFE3F1FA),
    Color(0xFFE6F4E1),
    Color(0xFFFDECE6),
    Color(0xFFEDE7F8),
    Color(0xFFFFF4D6),
)
