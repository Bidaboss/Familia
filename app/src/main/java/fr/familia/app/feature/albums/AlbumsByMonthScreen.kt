package fr.familia.app.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.feature.feed.FeedTone
import fr.familia.app.feature.month.monthName
import java.time.LocalDate
import java.time.YearMonth

/**
 * « Albums » with the fil on (récit 71, maquette SF2): « Toutes les photos de la famille, rangées
 * toutes seules. », the black « Diaporama de <mois> », then each month in a grid of 2, the « Photos
 * du fil » of the month first. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsByMonthScreen(
    state: AlbumsUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumEntry) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenFeedPhotos: (YearMonth) -> Unit,
    onOpenTrips: (() -> Unit)? = null,
) {
    // Two a line, one at a very large font size so the titles stay whole.
    val perLine = if (LocalDensity.current.fontScale >= 1.8f) 1 else 2
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.albums_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.albums_empty_title),
                    body = stringResource(R.string.albums_empty_body),
                    action = {
                        if (onOpenTrips != null) ClayLink(stringResource(R.string.albums_empty_trips), onClick = onOpenTrips)
                        else ClayLink(stringResource(R.string.empty_go_back), onClick = onBack)
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "subtitle") {
                        Text(
                            stringResource(R.string.albums_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The diaporama reads the albums of the events and the trips: the newest month that has one.
                    (state.months.firstOrNull { it == YearMonth.from(today) } ?: state.months.firstOrNull())?.let { month ->
                        item(key = "slideshow") { SlideshowButton(month) { onOpenMonth(month) } }
                    }
                    state.sections.forEach { section ->
                        item(key = "month-${section.month}") {
                            Text(
                                stringResource(R.string.albums_month, monthName(section.month), section.month.year),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                            )
                        }
                        section.tiles.chunked(perLine).forEachIndexed { line, row ->
                            item(key = "line-${section.month}-$line") {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { tile ->
                                        when (tile) {
                                            is AlbumTile.Album -> AlbumTileView(tile.entry, Modifier.weight(1f)) { onOpenAlbum(tile.entry) }
                                            is AlbumTile.Feed -> FeedTileView(tile.album, Modifier.weight(1f)) { onOpenFeedPhotos(tile.album.month) }
                                        }
                                    }
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

/** « Diaporama de novembre », black, the whole width (DESIGN-18: Albums and Diaporama together). */
@Composable
private fun SlideshowButton(month: YearMonth, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B1B), contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(Icons.Outlined.Slideshow, contentDescription = null)
        Text(
            stringResource(R.string.albums_slideshow, monthName(month).lowercase()),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private val COVER = RoundedCornerShape(20.dp)

@Composable
private fun AlbumTileView(album: AlbumEntry, modifier: Modifier, onClick: () -> Unit) {
    val count = pluralStringResource(R.plurals.trip_photo_count, album.photoCount, album.photoCount)
    Tile(
        cover = album.coverThumb,
        placeholder = { Icon(if (album.kind == AlbumEntry.Kind.Trip) Icons.Outlined.CardTravel else Icons.Outlined.Event, contentDescription = null) },
        title = album.title,
        detail = album.coverAuthor?.let { stringResource(R.string.albums_tile_detail, count, it) } ?: count,
        pill = null,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun FeedTileView(album: FeedPhotoAlbum, modifier: Modifier, onClick: () -> Unit) {
    val count = pluralStringResource(R.plurals.trip_photo_count, album.photos.size, album.photos.size)
    val names = album.authorNames
    val who = if (names.size == 1) names.first() else pluralStringResource(R.plurals.feed_album_authors, names.size - 1, names.first(), names.size - 1)
    Tile(
        cover = album.cover.thumbnailPath,
        placeholder = {},
        title = stringResource(R.string.albums_feed_title),
        detail = stringResource(R.string.albums_tile_detail, count, who),
        pill = stringResource(R.string.albums_feed_pill),
        modifier = modifier,
        onClick = onClick,
    )
}

/** A square cover with corners of 20 dp, its title, then « n photos · auteur »; the whole tile opens. */
@Composable
private fun Tile(
    cover: String?,
    placeholder: @Composable () -> Unit,
    title: String,
    detail: String,
    pill: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.albums_open), onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(COVER).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) StorageImage(cover, null, Modifier.fillMaxSize()) else placeholder()
            pill?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = FeedTone.Green.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(FeedTone.Green.labelBackground)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
