package fr.familia.app.core.place

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/*
 * The place component every module uses (PRD, récit 33): what a place says, and « Itinéraire »,
 * which opens Google Maps, Waze or whichever app the member chooses. No map in the app, no API key.
 */

/** Opens the route to [uri] (a geo: link); false when the phone has no app for it. */
fun openDirections(context: Context, uri: String): Boolean = try {
    // No chooser of ours: Android asks once between Maps and Waze, and can remember the choice.
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** The name and the address of a place; « Position partagée » when it has neither. */
@Composable
fun PlaceLines(place: Place, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(place.title ?: stringResource(R.string.place_position), style = MaterialTheme.typography.bodyLarge)
            if (place.name != null && place.address != null) {
                Text(place.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** « Itinéraire »: outlined, so a screen keeps its one filled button (Argile). */
@Composable
fun DirectionsButton(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.place_no_maps_app)
    OutlinedButton(
        onClick = { if (!openDirections(context, uri)) Toast.makeText(context, noApp, Toast.LENGTH_LONG).show() },
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Icon(Icons.Outlined.Directions, contentDescription = null)
        Text(stringResource(R.string.place_directions), modifier = Modifier.padding(start = 8.dp))
    }
}
