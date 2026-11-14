package fr.familia.app.core.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import fr.familia.app.feature.auth.AuthRepository
import fr.familia.app.feature.auth.FirebaseAuthRepository
import fr.familia.app.feature.auth.GoogleCredentialProvider

/**
 * Manual dependency injection: one place that builds the repositories, created once by
 * [fr.familia.app.FamiliaApplication]. Each field is null when Firebase is not configured yet.
 */
class AppContainer(context: Context) {

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

    val eventRepository: EventRepository? by lazy { firestore?.let { FirestoreEventRepository(it) } }

    /** The web client id is generated from google-services.json by the Google services plugin. */
    val googleCredentialProvider: GoogleCredentialProvider? by lazy {
        @Suppress("DiscouragedApi") // The resource only exists once google-services.json is added.
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (firebaseReady && resId != 0) GoogleCredentialProvider(context.getString(resId)) else null
    }
}
