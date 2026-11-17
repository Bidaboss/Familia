package fr.familia.app.core.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.album.FirestoreAlbumPhotoRepository
import fr.familia.app.core.photo.FirebasePhotoStorage
import fr.familia.app.core.photo.PhotoEncoder
import fr.familia.app.core.photo.PhotoLinks
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.PreferencesLinks
import fr.familia.app.core.photo.StoragePhotoUploader
import fr.familia.app.core.place.AndroidPlaceLocator
import fr.familia.app.core.usage.UsageTracker
import fr.familia.app.core.video.Media3PhotoFilmMaker
import fr.familia.app.core.video.PhotoFilmMaker
import fr.familia.app.feature.auth.AuthRepository
import fr.familia.app.feature.auth.FirebaseAuthRepository
import fr.familia.app.feature.auth.GoogleCredentialProvider

/**
 * Manual dependency injection: everything the screens reach, built once by
 * [fr.familia.app.FamiliaApplication] ([FirebaseAppContainer]). Each repository is null when
 * Firebase is not configured yet.
 *
 * « Voir comme » (PRD, rôles) gives the screens a [fr.familia.app.core.viewas.ViewAsContainer]
 * instead, where every write is refused. A new field goes here, in [FirebaseAppContainer] and in
 * ViewAsContainer (the compiler asks for all three), with a read-only version there.
 */
interface AppContainer {
    val authRepository: AuthRepository?
    val memberRepository: MemberRepository?
    val linkPreviewRepository: LinkPreviewRepository
    val memberPhotoRepository: MemberPhotoRepository?
    /** La fiche de chaque membre (récit 7 bis). */
    val memberCardRepository: MemberCardRepository?
    val eventRepository: EventRepository?
    val rsvpRepository: RsvpRepository?
    val bringRepository: BringRepository?

    /** « Ma valise » (récit 62). */
    val packingRepository: PackingRepository? get() = null
    val eventCommentRepository: EventCommentRepository?
    val helpRequestRepository: HelpRequestRepository?
    val tripRepository: TripRepository?
    val expenseRepository: ExpenseRepository?
    val recipeRepository: RecipeRepository?
    val capsuleRepository: CapsuleRepository?
    val goodNewsRepository: GoodNewsRepository?
    val genealogyRepository: GenealogyRepository?

    /** « Qui est-ce ? » (récit 43). */
    val whoIsItRepository: WhoIsItRepository? get() = null
    val weeklyQuestionRepository: WeeklyQuestionRepository?
    val souvenirRepository: SouvenirRepository?
    val birthdayCardRepository: BirthdayCardRepository?
    /** « Pensez à moi » (récit 43). */
    val momentRepository: MomentRepository? get() = null
    /** Bons plans et bonnes adresses (récit 45). */
    val goodDealRepository: GoodDealRepository? get() = null

    /** « On y va ensemble » (récit 52). */
    val carpoolRepository: CarpoolRepository? get() = null

    /** « Me le rappeler » (récit 56): private, so « Voir comme » keeps it null. */
    val eventReminderRepository: EventReminderRepository? get() = null

    /** « Événements qui reviennent » (récit 54). */
    val eventSeriesRepository: EventSeriesRepository? get() = null

    /** Récit 49; null in « Voir comme », where nobody else's secret link shows. */
    val calendarFeedRepository: CalendarFeedRepository? get() = null
    /** On se prête (récit 51). */
    val lendItemRepository: LendItemRepository? get() = null
    val turnRepository: TurnRepository? get() = null
    val treeRepository: TreeRepository?
    val photoStorage: PhotoStorage?
    val photoUploader: PhotoUploader?
    val albumPhotoRepository: AlbumPhotoRepository?
    val filmMaker: PhotoFilmMaker
    val appConfigRepository: AppConfigRepository?
    val giftRepository: GiftRepository?
    val notebookRepository: NotebookRepository?
    val usageRepository: UsageRepository?
    val usageTracker: UsageTracker?
    val consumptionRepository: ConsumptionRepository?
    val pollRepository: PollRepository?
    val feedbackRepository: FeedbackRepository?

    /** « Plantages » (récit 6 sexies, rév. 220). Null in the fakes of the tests. */
    val crashRepository: CrashRepository? get() = null
    val joinMeRepository: JoinMeRepository?
    val placeLocator: AndroidPlaceLocator
    val exportRepository: ExportRepository?
    val notificationRepository: NotificationRepository?
    val googleCredentialProvider: GoogleCredentialProvider?
}

/** The real container, on Firebase. */
class FirebaseAppContainer(context: Context) : AppContainer {

    private val appContext: Context = context.applicationContext

    private val firebaseReady: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val firestore: FirebaseFirestore? by lazy {
        if (!firebaseReady) return@lazy null
        FirebaseFirestore.getInstance().apply {
            // Keeps the planning readable offline and queues writes until the network is back.
            firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings {}) }
        }
    }

    override val authRepository: AuthRepository? by lazy {
        if (firebaseReady) FirebaseAuthRepository(FirebaseAuth.getInstance()) else null
    }

    override val memberRepository: MemberRepository? by lazy { firestore?.let { FirestoreMemberRepository(it) } }

    /** Reads the preview of a link shared to « Mes envies » (récit 8 bis), from the phone. */
    override val linkPreviewRepository: LinkPreviewRepository by lazy { HttpLinkPreviewRepository() }

    override val memberPhotoRepository: MemberPhotoRepository? by lazy { firestore?.let { FirestoreMemberPhotoRepository(it) } }

    override val memberCardRepository: MemberCardRepository? by lazy { firestore?.let { FirestoreMemberCardRepository(it) } }

    override val eventRepository: EventRepository? by lazy { firestore?.let { FirestoreEventRepository(it) } }

    override val rsvpRepository: RsvpRepository? by lazy { firestore?.let { FirestoreRsvpRepository(it) } }

    /** « Qui apporte quoi » of an event (PRD, récit 4 bis). */
    override val bringRepository: BringRepository? by lazy { firestore?.let { FirestoreBringRepository(it) } }
    override val packingRepository: PackingRepository? by lazy { firestore?.let { FirestorePackingRepository(it) } }

    /** The comments of an event (PRD, récit 28). */
    override val eventCommentRepository: EventCommentRepository? by lazy { firestore?.let { FirestoreEventCommentRepository(it) } }

    override val helpRequestRepository: HelpRequestRepository? by lazy { firestore?.let { FirestoreHelpRequestRepository(it) } }
    override val tripRepository: TripRepository? by lazy { firestore?.let { FirestoreTripRepository(it) } }

    /** The accounts of a trip (récit 16 bis). */
    override val expenseRepository: ExpenseRepository? by lazy { firestore?.let { FirestoreExpenseRepository(it) } }

    /** The recipe book (récit 38). */
    override val recipeRepository: RecipeRepository? by lazy { firestore?.let { FirestoreRecipeRepository(it) } }

    /** The time capsules (récit 39). */
    override val capsuleRepository: CapsuleRepository? by lazy { firestore?.let { FirestoreCapsuleRepository(it) } }

    override val goodNewsRepository: GoodNewsRepository? by lazy { firestore?.let { FirestoreGoodNewsRepository(it) } }

    /** L'arbre généalogique (récit 40). */
    override val genealogyRepository: GenealogyRepository? by lazy { firestore?.let { FirestoreGenealogyRepository(it) } }

    override val whoIsItRepository: WhoIsItRepository? by lazy { firestore?.let { FirestoreWhoIsItRepository(it) } }

    override val weeklyQuestionRepository: WeeklyQuestionRepository? by lazy { firestore?.let { FirestoreWeeklyQuestionRepository(it) } }
    override val souvenirRepository: SouvenirRepository? by lazy { firestore?.let { FirestoreSouvenirRepository(it) } }
    override val birthdayCardRepository: BirthdayCardRepository? by lazy { firestore?.let { FirestoreBirthdayCardRepository(it) } }
    override val momentRepository: MomentRepository? by lazy { firestore?.let { FirestoreMomentRepository(it) } }
    override val goodDealRepository: GoodDealRepository? by lazy { firestore?.let { FirestoreGoodDealRepository(it) } }
    override val calendarFeedRepository: CalendarFeedRepository? by lazy { firestore?.let { FirestoreCalendarFeedRepository(it) } }
    override val lendItemRepository: LendItemRepository? by lazy { firestore?.let { FirestoreLendItemRepository(it) } }
    override val turnRepository: TurnRepository? by lazy { firestore?.let { FirestoreTurnRepository(it) } }
    override val carpoolRepository: CarpoolRepository? by lazy { firestore?.let { FirestoreCarpoolRepository(it) } }
    override val eventReminderRepository: EventReminderRepository? by lazy { firestore?.let { FirestoreEventReminderRepository(it) } }
    override val eventSeriesRepository: EventSeriesRepository? by lazy { firestore?.let { FirestoreEventSeriesRepository(it) } }
    override val treeRepository: TreeRepository? by lazy { firestore?.let { FirestoreTreeRepository(it) } }

    /** Photos of the good news and the trips, in Cloud Storage (firebase/storage.rules). */
    override val photoStorage: PhotoStorage? by lazy {
        if (firebaseReady) {
            val prefs = appContext.getSharedPreferences("photo-links", Context.MODE_PRIVATE)
            FirebasePhotoStorage(FirebaseStorage.getInstance(), PhotoLinks(PreferencesLinks(prefs)))
        } else {
            null
        }
    }

    override val photoUploader: PhotoUploader? by lazy {
        photoStorage?.let { StoragePhotoUploader(PhotoEncoder(appContext), it) }
    }

    /** The photo records of the albums: the trips, and later the events. */
    override val albumPhotoRepository: AlbumPhotoRepository? by lazy { firestore?.let { FirestoreAlbumPhotoRepository(it) } }

    /** The films of the albums (récit 16), made on the phone with Media3. */
    override val filmMaker: PhotoFilmMaker by lazy { Media3PhotoFilmMaker(appContext) }

    override val appConfigRepository: AppConfigRepository? by lazy { firestore?.let { FirestoreAppConfigRepository(it) } }

    override val giftRepository: GiftRepository? by lazy { firestore?.let { FirestoreGiftRepository(it) } }
    override val notebookRepository: NotebookRepository? by lazy { firestore?.let { FirestoreNotebookRepository(it) } }

    /** Time spent in the app (PRD, récit 1 nonies). */
    override val usageRepository: UsageRepository? by lazy { firestore?.let { FirestoreUsageRepository(it) } }

    /** One counter for the whole app, so a rotation is not a new opening. */
    override val usageTracker: UsageTracker? by lazy { usageRepository?.let { UsageTracker(appContext, it) } }

    /** The daily use of the database, for the admin principal (PRD, récit 1 sexies). */
    override val consumptionRepository: ConsumptionRepository? by lazy { firestore?.let { FirestoreConsumptionRepository(it) } }

    /** Sondages (PRD, récits 2 bis et 2 ter). */
    override val pollRepository: PollRepository? by lazy { firestore?.let { FirestorePollRepository(it) } }

    /** « Une idée ? Un souci ? » (PRD, récit 6 sexies). */
    override val feedbackRepository: FeedbackRepository? by lazy { firestore?.let { FirestoreFeedbackRepository(it, photoStorage) } }
    override val crashRepository: CrashRepository? by lazy { firestore?.let { FirestoreCrashRepository(it) } }

    /** « Rejoignez-moi » (PRD, récit 33). */
    override val joinMeRepository: JoinMeRepository? by lazy { firestore?.let { FirestoreJoinMeRepository(it) } }

    /** The position of the moment and the geocoder of the phone, for every place (récit 33). */
    override val placeLocator: AndroidPlaceLocator by lazy { AndroidPlaceLocator(appContext) }

    override val exportRepository: ExportRepository? by lazy { firestore?.let { FirestoreExportRepository(it) } }

    override val notificationRepository: NotificationRepository? by lazy {
        firestore?.let { FirestoreNotificationRepository(it, FirebaseMessaging.getInstance()) }
    }

    /** The web client id is generated from google-services.json by the Google services plugin. */
    override val googleCredentialProvider: GoogleCredentialProvider? by lazy {
        @Suppress("DiscouragedApi") // The resource only exists once google-services.json is added.
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (firebaseReady && resId != 0) GoogleCredentialProvider(context.getString(resId)) else null
    }
}
