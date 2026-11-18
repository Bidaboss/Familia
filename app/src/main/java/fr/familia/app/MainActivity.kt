package fr.familia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.splash.holdStartScreen
import fr.familia.app.core.welcome.OpeningStyle
import fr.familia.app.core.welcome.WelcomePreferences
import fr.familia.app.core.photo.LocalOnline
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
    private val openRecap = mutableStateOf<String?>(null)
    /** « Une idée ? Un souci ? » (récit 6 sexies): "list" opens Retours, "mine" « Mes demandes ». */
    private val openFeedback = mutableStateOf<String?>(null)
    /** The summary of the new bons plans (récit 45). */
    private val openGoodDeals = mutableStateOf(false)
    /** A thing to lend whose ask, answer or reminder was touched (récit 51). */
    private val openLendItemId = mutableStateOf<String?>(null)
    private val openTurnId = mutableStateOf<String?>(null)
    /** A message of the admin principal whose notification was touched (récit 69). */
    private val openAnnouncementId = mutableStateOf<String?>(null)

    /** A link shared from another app, for « Mes envies » (récit 8 bis). */
    private val sharedWish = mutableStateOf<SharedWish?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The tree of the start screen is held only for the short opening: the long one, the
        // welcome, follows at once, and « Aucune » opens as soon as the app is ready (17/11).
        if (WelcomePreferences(this).style == OpeningStyle.Short) holdStartScreen(restored = savedInstanceState != null)
        // One door for every link (audit): the same in onCreate and onNewIntent.
        if (savedInstanceState == null) open(intent)
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
            val goodDealsToOpen by openGoodDeals
            val lendItemToOpen by openLendItemId
            val turnToOpen by openTurnId
            val announcementToOpen by openAnnouncementId
            val wishToAdd by sharedWish
            val memberToOpen by openMemberId
            FamiliaTheme {
                CompositionLocalProvider(LocalOnline provides isOnline) {
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
                                onRecapOpened = { openRecap.value = null },
                                openFeedback = feedbackToOpen,
                                onFeedbackOpened = { openFeedback.value = null },
                                openGoodDeals = goodDealsToOpen,
                                onGoodDealsOpened = { openGoodDeals.value = false },
                                openLendItemId = lendItemToOpen,
                                onLendItemOpened = { openLendItemId.value = null },
                                openTurnId = turnToOpen,
                                onTurnOpened = { openTurnId.value = null },
                                openAnnouncementId = announcementToOpen,
                                onAnnouncementOpened = { openAnnouncementId.value = null },
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
    }

    /** The app was already open (singleTop) when a notification was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /** Each door of [intent] (see [deepLinks]) is opened once the member is signed in. */
    private fun open(intent: Intent) {
        intent.deepLinks().forEach { link ->
            when (link) {
                is DeepLink.Event -> openEventId.value = link.id
                is DeepLink.GiftList -> openListId.value = link.id
                is DeepLink.HelpRequest -> openHelpRequestId.value = link.id
                is DeepLink.GoodNews -> openGoodNewsId.value = link.id
                is DeepLink.Trip -> openTripId.value = link.id
                is DeepLink.WeeklyQuestion -> openWeeklyQuestionId.value = link.id
                DeepLink.Souvenir -> openSouvenir.value = true
                is DeepLink.Poll -> openPollId.value = link.id
                DeepLink.JoinMe -> openJoinMe.value = true
                is DeepLink.Capsule -> openCapsuleId.value = link.id
                is DeepLink.Recap -> openRecap.value = link.week
                is DeepLink.Member -> openMemberId.value = link.id
                is DeepLink.Feedback -> openFeedback.value = link.key
                DeepLink.GoodDeals -> openGoodDeals.value = true
                is DeepLink.LendItem -> openLendItemId.value = link.id
                is DeepLink.Turn -> openTurnId.value = link.id
                is DeepLink.Announcement -> openAnnouncementId.value = link.id
            }
        }
        intent.sharedWish()?.let { sharedWish.value = it }
    }
}
