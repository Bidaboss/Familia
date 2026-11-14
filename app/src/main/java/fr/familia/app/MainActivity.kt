package fr.familia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.notifications.FamiliaMessagingService
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.auth.SessionRoute

class MainActivity : ComponentActivity() {

    /** The event of a tapped notification, opened once the member is signed in. */
    private val openEventId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openEventId.value = intent.eventId()
        val app = application as FamiliaApplication
        val container = app.container
        setContent {
            val isOnline by app.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val eventToOpen by openEventId
            FamiliaTheme {
                SessionRoute(container = container) { member ->
                    FamiliaApp(
                        container,
                        member,
                        isOffline = !isOnline,
                        openEventId = eventToOpen,
                        onEventOpened = { openEventId.value = null },
                    )
                }
            }
        }
    }

    /** The app was already open (singleTop) when a notification was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.eventId()?.let { openEventId.value = it }
    }

    private fun Intent.eventId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_EVENT_ID)
}
