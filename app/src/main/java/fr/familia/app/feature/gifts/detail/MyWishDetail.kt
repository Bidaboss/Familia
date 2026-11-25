package fr.familia.app.feature.gifts.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.photo.CoverBanner
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.gifts.WishLook
import fr.familia.app.feature.gifts.WishPill
import fr.familia.app.feature.gifts.formatEuros

/**
 * The page of one of my wishes (correctif E9, E10, fiche K20): its picture wide, title, price and
 * occasion, « Voir sur … », the occasion changed in one touch, the reminder that nobody tells me who
 * reserves it, « Modifier cette envie » and « Retirer de mes envies » (asked first).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MyWishDetail(
    item: GiftItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSetOccasion: (GiftOccasion?) -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().themeBackground()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White, contentColor = WishLook.Ink, border = BorderStroke(1.dp, WishLook.Edge)) {
                BackButton(onClick = onBack)
            }
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            // Keeps the title in the middle.
            Box(Modifier.size(56.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BigPicture(item)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.price?.let { Text(formatEuros(it), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF1E8DA), contentColor = WishLook.Ink) {
                        Text(
                            occasionLabel(item.occasion),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item.whereToFind?.let { Text(stringResource(R.string.wish_where, it), style = MaterialTheme.typography.bodyLarge) }
            item.url?.let { url -> LinkCard(url) }
            Surface(shape = RoundedCornerShape(24.dp), color = WishLook.Field, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF1E8DA)), contentAlignment = Alignment.Center) {
                            Text("🎉", fontSize = 20.sp)
                        }
                        Text(stringResource(R.string.wish_detail_occasion), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(GiftOccasion.Christmas, GiftOccasion.Birthday, null).forEach { occasion ->
                            WishPill(occasionLabel(occasion), selected = item.occasion == occasion, onClick = { onSetOccasion(occasion) })
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = WishLook.InfoYellow, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
                Text("👪 " + stringResource(R.string.wish_detail_family_sees), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = WishLook.Dark, contentColor = Color.White),
                // Full-width actions of Cadeaux: 48 dp (règle 57 of regles-cadeaux-themes-photo.md, l'admin 25/11).
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("✏️ " + stringResource(R.string.wish_detail_edit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { confirmRemove = true },
                border = BorderStroke(2.dp, WishLook.Edge),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WishLook.Remove),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.wish_detail_remove), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.wish_detail_remove_title, item.title)) },
            text = { Text(stringResource(R.string.wish_detail_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.wish_detail_remove_confirm), color = WishLook.Remove, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.wish_detail_keep), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun occasionLabel(occasion: GiftOccasion?): String = stringResource(
    when (occasion) {
        GiftOccasion.Christmas -> R.string.wish_occasion_christmas
        GiftOccasion.Birthday -> R.string.wish_occasion_birthday
        else -> R.string.wish_occasion_none_short
    },
)

/** The photo of its author first (récit 44), then the picture of the shop page, else a gift on a pastel. */
@Composable
private fun BigPicture(item: GiftItem) {
    val photo = item.photo?.takeIf { LocalFeatureFlags.current.isOn(Feature.MorePhotos) }
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)).background(WishLook.pastel(item.title)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> CoverBanner(photo, Modifier.fillMaxSize())
            item.imageUrl != null -> AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text("🎁", fontSize = 72.sp)
        }
    }
}

/** « Voir sur boutique.fr », « Ton lien », ↗: opens the shop page. */
@Composable
private fun LinkCard(url: String) {
    val uriHandler = LocalUriHandler.current
    val host = linkHost(url)
    Surface(shape = RoundedCornerShape(20.dp), color = WishLook.Field, contentColor = WishLook.Ink, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button) { runCatching { uriHandler.openUri(url) } }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1E8DA)), contentAlignment = Alignment.Center) {
                Text("🔗", fontSize = 22.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (host != null) stringResource(R.string.wish_detail_link, host) else stringResource(R.string.gift_item_open_link),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.wish_detail_link_mine), style = MaterialTheme.typography.bodyMedium, color = WishLook.Muted)
            }
            Text("↗", fontSize = 22.sp)
        }
    }
}
