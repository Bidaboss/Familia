package fr.familia.app.feature.albums

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.month.monthName
import java.time.LocalDate
import java.time.YearMonth

/**
 * « Albums » (R1 bis, Tout Familia): the albums of the trips and of the events, then the months.
 * Each album starts its own diaporama (DESIGN-18). Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    onBack: () -> Unit,
    onOpenAlbum: (AlbumEntry) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.albums_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.albums.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.albums_empty_title),
                    body = stringResource(R.string.albums_empty_body),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.albums, key = { "${it.kind}-${it.id}" }) { album -> AlbumRow(album) { onOpenAlbum(album) } }
                    item(key = "months-title") {
                        Text(
                            stringResource(R.string.albums_months),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        )
                    }
                    items(state.months, key = { "month-$it" }) { month -> MonthRow(month) { onOpenMonth(month) } }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntry, onClick: () -> Unit) {
    val open = stringResource(R.string.albums_open)
    ClayCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = open) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(12.dp)
            val cover = album.coverThumb
            if (cover != null) {
                StorageImage(path = cover, contentDescription = null, modifier = Modifier.size(64.dp).clip(shape))
            } else {
                Placeholder(if (album.kind == AlbumEntry.Kind.Trip) Icons.Outlined.CardTravel else Icons.Outlined.Event)
            }
            Column(Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        R.string.albums_row_detail,
                        pluralStringResource(R.plurals.trip_photo_count, album.photoCount, album.photoCount),
                        monthName(YearMonth.from(album.date)).lowercase(),
                        album.date.year,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Placeholder(icon: ImageVector) {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null) }
}

/** « Novembre 2026 », which opens the diaporama of the month. */
@Composable
private fun MonthRow(month: YearMonth, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.albums_open_month), onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.Slideshow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.albums_month, monthName(month), month.year), style = MaterialTheme.typography.titleMedium)
    }
}

@FamiliaPreviews
@Composable
private fun AlbumsScreenPreview() {
    val albums = listOf(
        AlbumEntry(AlbumEntry.Kind.Event, "e1", "Pique-nique au lac", LocalDate.of(2026, 10, 4), photoCount = 12),
        AlbumEntry(AlbumEntry.Kind.Trip, "t1", "Corse", LocalDate.of(2026, 8, 2), photoCount = 40),
    )
    FamiliaTheme {
        Surface {
            AlbumsScreen(AlbumsUiState(loading = false, albums = albums, months = albums.map { YearMonth.from(it.date) }), {}, {}, {})
        }
    }
}
