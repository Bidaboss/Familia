package fr.familia.app

import android.app.Application
import fr.familia.app.core.crash.CrashCatcher
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.FirebaseAppContainer
import fr.familia.app.core.data.NetworkMonitor
import fr.familia.app.core.notifications.NotificationChannels

class FamiliaApplication : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash during the start is kept too (« Plantages », récit 6 sexies).
        CrashCatcher.install(this)
        container = FirebaseAppContainer(this)
        networkMonitor = NetworkMonitor(this)
        NotificationChannels.create(this)
    }
}
