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

    /** The gift list of a tapped notification (V2). */
    private val openListId = mutableStateOf<String?>(null)

    /** The « coup de main » of a tapped notification, opened the same way. */
    private val openHelpRequestId = mutableStateOf<String?>(null)

    /** The good news of a tapped notification. */
    private val openGoodNewsId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            openEventId.value = intent.eventId()
            openListId.value = intent.listId()
            openHelpRequestId.value = intent.helpRequestId()
            openGoodNewsId.value = intent.goodNewsId()
        }
        val app = application as FamiliaApplication
        val container = app.container
        setContent {
            val isOnline by app.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val eventToOpen by openEventId
            val listToOpen by openListId
            val helpRequestToOpen by openHelpRequestId
            val goodNewsToOpen by openGoodNewsId
            FamiliaTheme {
                SessionRoute(container = container) { member ->
                    FamiliaApp(
                        container,
                        member,
                        isOffline = !isOnline,
                        openEventId = eventToOpen,
                        onEventOpened = { openEventId.value = null },
                        openListId = listToOpen,
                        onListOpened = { openListId.value = null },
                        openHelpRequestId = helpRequestToOpen,
                        onHelpRequestOpened = { openHelpRequestId.value = null },
                        openGoodNewsId = goodNewsToOpen,
                        onGoodNewsOpened = { openGoodNewsId.value = null },
                    )
                }
            }
        }
    }

    /** The app was already open (singleTop) when a notification was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.eventId()?.let { openEventId.value = it }
        intent.listId()?.let { openListId.value = it }
        intent.helpRequestId()?.let { openHelpRequestId.value = it }
        intent.goodNewsId()?.let { openGoodNewsId.value = it }
    }

    private fun Intent.eventId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_EVENT_ID)

    private fun Intent.listId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_LIST_ID)

    private fun Intent.helpRequestId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_HELP_REQUEST_ID)

    private fun Intent.goodNewsId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_GOOD_NEWS_ID)
}
