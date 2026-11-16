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
import fr.familia.app.core.share.ShareKind
import fr.familia.app.core.share.parseSharedLink
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.viewas.ViewAsHost
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
    /** The birthday of a shared link: the member's page opens (récit 31). */
    private val openMemberId = mutableStateOf<String?>(null)
    /** The time capsule of a tapped notification (récit 39). */
    private val openCapsuleId = mutableStateOf<String?>(null)
    private val openRecap = mutableStateOf(false)
    /** « Une idée ? Un souci ? » (récit 6 sexies): "list" opens Retours, "mine" « Mes demandes ». */
    private val openFeedback = mutableStateOf<String?>(null)

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
            openCapsuleId.value = intent.capsuleId()
            openRecap.value = intent.hasRecap()
            openFeedback.value = intent.feedback()
            sharedWish.value = intent.sharedWish()
            openSharedLink(intent)
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
            val capsuleToOpen by openCapsuleId
            val recapToOpen by openRecap
            val feedbackToOpen by openFeedback
            val wishToAdd by sharedWish
            val memberToOpen by openMemberId
            FamiliaTheme {
                SessionRoute(container = container) { member ->
                    // « Voir comme »: the admin principal may look at the app as another member.
                    ViewAsHost(container, member) { shownContainer, shownMember ->
                        FamiliaApp(
                            shownContainer,
                            shownMember,
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
                            openCapsuleId = capsuleToOpen,
                            onCapsuleOpened = { openCapsuleId.value = null },
                            openRecap = recapToOpen,
                            onRecapOpened = { openRecap.value = false },
                            openFeedback = feedbackToOpen,
                            onFeedbackOpened = { openFeedback.value = null },
                            sharedWish = wishToAdd,
                            onSharedWishOpened = { sharedWish.value = null },
                            openMemberId = memberToOpen,
                            onMemberOpened = { openMemberId.value = null },
                        )
                    }
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
        intent.capsuleId()?.let { openCapsuleId.value = it }
        if (intent.hasRecap()) openRecap.value = true
        intent.feedback()?.let { openFeedback.value = it }
        intent.sharedWish()?.let { sharedWish.value = it }
        openSharedLink(intent)
    }

    /**
     * A link of a share (récit 31): the same doors as a tapped notification. The security rules
     * still decide: an item the member cannot read shows « introuvable », as for a deleted one.
     */
    private fun openSharedLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val link = parseSharedLink(uri.scheme, uri.host, uri.path) ?: return
        when (link.kind) {
            ShareKind.Event -> openEventId.value = link.id
            ShareKind.Poll -> openPollId.value = link.id
            ShareKind.Birthday -> openMemberId.value = link.id
            ShareKind.List -> openListId.value = link.id
            ShareKind.GoodNews -> openGoodNewsId.value = link.id
            ShareKind.Trip -> openTripId.value = link.id
            ShareKind.HelpRequest -> openHelpRequestId.value = link.id
        }
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
    private fun Intent.capsuleId(): String? = getStringExtra(FamiliaMessagingService.EXTRA_CAPSULE_ID)
    private fun Intent.feedback(): String? = getStringExtra(FamiliaMessagingService.EXTRA_FEEDBACK)

    private fun Intent.hasRecap(): Boolean = getStringExtra(FamiliaMessagingService.EXTRA_RECAP_WEEK) != null
}
