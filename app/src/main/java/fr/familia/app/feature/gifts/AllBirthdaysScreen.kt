package fr.familia.app.feature.gifts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.ui.BackButton

/** The former Anniversaires tab, opened from the Cadeaux tab with a back arrow (carte DESIGN-4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllBirthdaysScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_birthdays)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}
