package fr.familia.app.core.viewas

import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.PackingRepository
import fr.familia.app.core.data.TravelPlaceRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.usage.UsageTracker

/** Thrown by every write in « Voir comme »: nothing was written. */
class ReadOnlyViewException : IllegalStateException("Lecture seule en mode Voir comme")

/**
 * Refuses the writes of « Voir comme ». [onRefused] tells the admin (« Lecture seule en mode Voir
 * comme »); it is called on the thread of the write, so it must be safe from anywhere.
 */
class ViewAsGuard(private val onRefused: () -> Unit = {}) {
    /** A write the admin asked for: they are told, and the caller gets [ReadOnlyViewException]. */
    fun refuse(): Nothing {
        onRefused()
        throw ReadOnlyViewException()
    }

    /**
     * A refusal without exception, for a call that is not suspend (signOut): the admin is told, and
     * nothing is thrown on the main thread (note C de la relecture, rév. 16/11).
     */
    fun tell() = onRefused()

    // No quiet refusal: in « Voir comme » the app makes no write by itself when a screen opens
    // (the phone is not registered, the time is not counted), so any write reaching here was asked.
}

/** What the screens know of « Voir comme »: start it from Administration, or the member looked at. */
data class ViewAsControl(
    /** Null outside « Voir comme ». */
    val viewed: Member? = null,
    /** Null when « Voir comme » cannot start here (not the admin principal, or already in it). */
    val start: ((Member) -> Unit)? = null,
    val stop: () -> Unit = {},
    /** In « Voir comme », the admin principal, who still reads with their own account; null outside. */
    val readerId: String? = null,
)

val LocalViewAs = staticCompositionLocalOf { ViewAsControl() }

/**
 * The container of « Voir comme »: the same reads as [base], every write refused. The member's
 * own time is not counted (no [usageTracker]). [adminId] is the admin principal, who still reads
 * with their own account: the help requests of the member show (the rules let the admin principal
 * read them all), the lists kept from the admin do not.
 */
class ViewAsContainer(private val base: AppContainer, guard: ViewAsGuard, adminId: String, viewedId: String) : AppContainer {
    override val authRepository = base.authRepository?.let { ReadOnlyAuthRepository(it, guard) }
    override val memberRepository = base.memberRepository?.let { ReadOnlyMemberRepository(it, guard) }
    override val linkPreviewRepository = base.linkPreviewRepository
    override val memberPhotoRepository = base.memberPhotoRepository?.let { ReadOnlyMemberPhotoRepository(it, guard) }
    override val memberCardRepository = base.memberCardRepository?.let { ReadOnlyMemberCardRepository(it, guard) }
    override val eventRepository = base.eventRepository?.let { ReadOnlyEventRepository(it, guard) }
    override val rsvpRepository = base.rsvpRepository?.let { ReadOnlyRsvpRepository(it, guard) }
    override val bringRepository = base.bringRepository?.let { ReadOnlyBringRepository(it, guard) }

    // « Ma valise » is private: in « Voir comme », nobody's bag is read or written.
    override val packingRepository: PackingRepository? = null
    override val eventCommentRepository = base.eventCommentRepository?.let { ReadOnlyEventCommentRepository(it, guard) }
    override val goodNewsCommentRepository = base.goodNewsCommentRepository?.let { ReadOnlyEventCommentRepository(it, guard) }
    override val helpRequestRepository = base.helpRequestRepository?.let { ReadOnlyHelpRequestRepository(it, guard) }
    override val tripRepository = base.tripRepository?.let { ReadOnlyTripRepository(it, guard) }
    override val tripWordRepository = base.tripWordRepository?.let { ReadOnlyTripWordRepository(it, guard) }
    override val tripPlaceRepository = base.tripPlaceRepository?.let { ReadOnlyTripPlaceRepository(it, guard) }

    // « Carnet de baroudeur » is private: in « Voir comme », nobody's hand-added destinations are read.
    override val travelPlaceRepository: TravelPlaceRepository? = null
    override val expenseRepository = base.expenseRepository?.let { ReadOnlyExpenseRepository(it, guard) }
    override val recipeRepository = base.recipeRepository?.let { ReadOnlyRecipeRepository(it, guard) }
    override val capsuleRepository = base.capsuleRepository?.let { ReadOnlyCapsuleRepository(it, guard) }
    override val whoIsItRepository = base.whoIsItRepository?.let { ReadOnlyWhoIsItRepository(it, guard) }
    override val goodNewsRepository = base.goodNewsRepository?.let { ReadOnlyGoodNewsRepository(it, guard) }
    override val genealogyRepository = base.genealogyRepository?.let { ReadOnlyGenealogyRepository(it, guard) }
    override val weeklyQuestionRepository = base.weeklyQuestionRepository?.let { ReadOnlyWeeklyQuestionRepository(it, guard) }
    override val souvenirRepository = base.souvenirRepository?.let { ReadOnlySouvenirRepository(it, guard) }
    override val birthdayCardRepository = base.birthdayCardRepository?.let { ReadOnlyBirthdayCardRepository(it, guard) }
    override val goodDealRepository = base.goodDealRepository?.let { ReadOnlyGoodDealRepository(it, guard) }
    override val lendItemRepository = base.lendItemRepository?.let { ReadOnlyLendItemRepository(it, guard) }
    override val carpoolRepository = base.carpoolRepository?.let { ReadOnlyCarpoolRepository(it, guard) }
    override val eventSeriesRepository = base.eventSeriesRepository?.let { ReadOnlyEventSeriesRepository(it, guard) }
    override val treeRepository = base.treeRepository?.let { ReadOnlyTreeRepository(it, guard) }
    override val photoStorage = base.photoStorage?.let { ReadOnlyPhotoStorage(it, guard) }
    override val photoUploader = base.photoUploader?.let { ReadOnlyPhotoUploader(guard) }
    override val albumPhotoRepository = base.albumPhotoRepository?.let { ReadOnlyAlbumPhotoRepository(it, guard) }
    override val filmMaker = base.filmMaker
    override val appConfigRepository = base.appConfigRepository?.let { ReadOnlyAppConfigRepository(it, guard) }
    override val giftRepository = base.giftRepository?.let { ReadOnlyGiftRepository(it, guard, adminId, viewedId) }
    override val notebookRepository = base.notebookRepository?.let { ReadOnlyNotebookRepository(it, guard) }
    override val usageRepository = base.usageRepository?.let { ReadOnlyUsageRepository(it, guard) }
    override val usageTracker: UsageTracker? = null
    override val consumptionRepository = base.consumptionRepository
    override val pollRepository = base.pollRepository?.let { ReadOnlyPollRepository(it, guard) }
    override val feedbackRepository = base.feedbackRepository?.let { ReadOnlyFeedbackRepository(it, guard) }
    // Nothing is sent or settled through someone else's eyes.
    override val crashRepository: fr.familia.app.core.data.CrashRepository? = null
    override val announcementRepository: fr.familia.app.core.data.AnnouncementRepository? = null
    override val joinMeRepository = base.joinMeRepository?.let { ReadOnlyJoinMeRepository(it, guard) }
    override val placeLocator = base.placeLocator
    override val exportRepository = base.exportRepository?.let { ReadOnlyExportRepository(guard) }
    override val notificationRepository = base.notificationRepository?.let { ReadOnlyNotificationRepository(it, guard) }
    override val googleCredentialProvider = base.googleCredentialProvider
}
