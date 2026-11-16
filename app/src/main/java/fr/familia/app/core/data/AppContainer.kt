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
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import fr.familia.app.core.photo.StoragePhotoUploader
import fr.familia.app.core.usage.UsageTracker
import fr.familia.app.core.video.Media3PhotoFilmMaker
import fr.familia.app.core.video.PhotoFilmMaker
import fr.familia.app.feature.auth.AuthRepository
import fr.familia.app.feature.auth.FirebaseAuthRepository
import fr.familia.app.feature.auth.GoogleCredentialProvider

/**
 * Manual dependency injection: one place that builds the repositories, created once by
 * [fr.familia.app.FamiliaApplication]. Each field is null when Firebase is not configured yet.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val firebaseReady: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val firestore: FirebaseFirestore? by lazy {
        if (!firebaseReady) return@lazy null
        FirebaseFirestore.getInstance().apply {
            // Keeps the planning readable offline and queues writes until the network is back.
            firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings {}) }
        }
    }

    val authRepository: AuthRepository? by lazy {
        if (firebaseReady) FirebaseAuthRepository(FirebaseAuth.getInstance()) else null
    }

    val memberRepository: MemberRepository? by lazy { firestore?.let { FirestoreMemberRepository(it) } }

    /** Reads the preview of a link shared to « Mes envies » (récit 8 bis), from the phone. */
    val linkPreviewRepository: LinkPreviewRepository by lazy { HttpLinkPreviewRepository() }

    val memberPhotoRepository: MemberPhotoRepository? by lazy { firestore?.let { FirestoreMemberPhotoRepository(it) } }

    val eventRepository: EventRepository? by lazy { firestore?.let { FirestoreEventRepository(it) } }

    val rsvpRepository: RsvpRepository? by lazy { firestore?.let { FirestoreRsvpRepository(it) } }

    /** « Qui apporte quoi » of an event (PRD, récit 4 bis). */
    val bringRepository: BringRepository? by lazy { firestore?.let { FirestoreBringRepository(it) } }

    val helpRequestRepository: HelpRequestRepository? by lazy { firestore?.let { FirestoreHelpRequestRepository(it) } }
    val tripRepository: TripRepository? by lazy { firestore?.let { FirestoreTripRepository(it) } }

    /** The recipe book (récit 38). */
    val recipeRepository: RecipeRepository? by lazy { firestore?.let { FirestoreRecipeRepository(it) } }

    val goodNewsRepository: GoodNewsRepository? by lazy { firestore?.let { FirestoreGoodNewsRepository(it) } }

    val weeklyQuestionRepository: WeeklyQuestionRepository? by lazy { firestore?.let { FirestoreWeeklyQuestionRepository(it) } }
    val souvenirRepository: SouvenirRepository? by lazy { firestore?.let { FirestoreSouvenirRepository(it) } }
    val treeRepository: TreeRepository? by lazy { firestore?.let { FirestoreTreeRepository(it) } }

    /** Photos of the good news and the trips, in Cloud Storage (firebase/storage.rules). */
    val photoStorage: PhotoStorage? by lazy {
        if (firebaseReady) FirebasePhotoStorage(FirebaseStorage.getInstance()) else null
    }

    val photoUploader: PhotoUploader? by lazy {
        photoStorage?.let { StoragePhotoUploader(PhotoEncoder(appContext), it) }
    }

    /** The photo records of the albums: the trips, and later the events. */
    val albumPhotoRepository: AlbumPhotoRepository? by lazy { firestore?.let { FirestoreAlbumPhotoRepository(it) } }

    /** The films of the albums (récit 16), made on the phone with Media3. */
    val filmMaker: PhotoFilmMaker by lazy { Media3PhotoFilmMaker(appContext) }

    val appConfigRepository: AppConfigRepository? by lazy { firestore?.let { FirestoreAppConfigRepository(it) } }

    val giftRepository: GiftRepository? by lazy { firestore?.let { FirestoreGiftRepository(it) } }
    val notebookRepository: NotebookRepository? by lazy { firestore?.let { FirestoreNotebookRepository(it) } }

    /** Time spent in the app (PRD, récit 1 nonies). */
    val usageRepository: UsageRepository? by lazy { firestore?.let { FirestoreUsageRepository(it) } }

    /** One counter for the whole app, so a rotation is not a new opening. */
    val usageTracker: UsageTracker? by lazy { usageRepository?.let { UsageTracker(appContext, it) } }

    /** The daily use of the database, for the admin principal (PRD, récit 1 sexies). */
    val consumptionRepository: ConsumptionRepository? by lazy { firestore?.let { FirestoreConsumptionRepository(it) } }

    /** Sondages (PRD, récits 2 bis et 2 ter). */
    val pollRepository: PollRepository? by lazy { firestore?.let { FirestorePollRepository(it) } }

    val exportRepository: ExportRepository? by lazy { firestore?.let { FirestoreExportRepository(it) } }

    val notificationRepository: NotificationRepository? by lazy {
        firestore?.let { FirestoreNotificationRepository(it, FirebaseMessaging.getInstance()) }
    }

    /** The web client id is generated from google-services.json by the Google services plugin. */
    val googleCredentialProvider: GoogleCredentialProvider? by lazy {
        @Suppress("DiscouragedApi") // The resource only exists once google-services.json is added.
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (firebaseReady && resId != 0) GoogleCredentialProvider(context.getString(resId)) else null
    }
}
