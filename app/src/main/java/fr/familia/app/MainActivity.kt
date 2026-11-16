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
import fr.familia.app.feature.gifts.share.SharedWish
import fr.familia.app.feature.gifts.share.sharedWish
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
    /** The trip of a tapped « bien arrivé » notification (V3). */
    private val openTripId = mutableStateOf<String?>(null)

    /** The question of the week of a tapped notification (récit 37). */
    private val openWeeklyQuestionId = mutableStateOf<String?>(null)

    /** A « Nouveau souvenir » was tapped: one's own page opens (récit 34). */
    private val openSouvenir = mutableStateOf(false)

    /** The poll of a tapped notification (récits 2 bis et 2 ter). */
    private val openPollId = mutableStateOf<String?>(null)

    /** A « Rejoignez-moi » was tapped: the Accueil shows it (récit 33). */
    private val openJoinMe = mutableStateOf(false)
    /** A link shared from another app, for « Mes envies » (récit 8 bis). */
    private val sharedWish = mutableStateOf<SharedWish?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            openEventId.value = intent.eventId()
            openListId.value = intent.listId()
            openHelpRequestId.value = intent.helpRequestId()
            openGoodNewsId.value = intent.goodNewsId()
            openTripId.value = intent.tripId()
            openWeeklyQuestionId.value = intent.weeklyQuestionId()
            openSouvenir.value = intent.hasSouvenir()
            openPollId.value = intent.pollId()
            openJoinMe.value = intent.hasJoinMe()
            sharedWish.value = intent.sharedWish()
        }
        val app = application as FamiliaApplication
        val container = app.container
        setContent {
            val isOnline by app.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val eventToOpen by openEventId
            val listToOpen by openListId
            val helpRequestToOpen by openHelpRequestId
            val goodNewsToOpen by openGoodNewsId
            val tripToOpen by openTripId
            val weeklyToOpen by openWeeklyQuestionId
            val souvenirToOpen by openSouvenir
            val pollToOpen by openPollId
            val joinMeToOpen by openJoinMe
            val wishToAdd by sharedWish
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
                        openTripId = tripToOpen,
                        onTripOpened = { openTripId.value = null },
                        openWeeklyQuestionId = weeklyToOpen,
                        onWeeklyQuestionOpened = { openWeeklyQuestionId.value = null },
                        openSouvenir = souvenirToOpen,
                        onSouvenirOpened = { openSouvenir.value = false },
                        openPollId = pollToOpen,
                        onPollOpened = { openPollId.value = null },
                        openJoinMe = joinMeToOpen,
                        onJoinMeOpened = { openJoinMe.value = false },
                        sharedWish = wishToAdd,
                        onSharedWishOpened = { sharedWish.value = null },
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
        intent.tripId()?.let { openTripId.value = it }
        intent.weeklyQuestionId()?.let { openWeeklyQuestionId.value = it }
        if (intent.hasSouvenir()) openSouvenir.value = true
        intent.pollId()?.let { openPollId.value = it }
        if (intent.hasJoinMe()) openJoinMe.value = true
        intent.sharedWish()?.let { sharedWish.value = it }
    }

    private fun Intent.eventId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_EVENT_ID)

    private fun Intent.listId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_LIST_ID)

    private fun Intent.helpRequestId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_HELP_REQUEST_ID)

    private fun Intent.goodNewsId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_GOOD_NEWS_ID)
    private fun Intent.tripId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_TRIP_ID)

    private fun Intent.weeklyQuestionId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_WEEKLY_QUESTION_ID)

    private fun Intent.hasSouvenir(): Boolean = getStringExtra(FamiliaMessagingService.EXTRA_SOUVENIR_KEY) != null
    private fun Intent.pollId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_POLL_ID)
    private fun Intent.hasJoinMe(): Boolean = getStringExtra(FamiliaMessagingService.EXTRA_JOIN_ME_ID) != null
}
