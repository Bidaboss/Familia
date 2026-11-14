package fr.familia.app

import android.app.Application
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.NetworkMonitor

class FamiliaApplication : Application() {
    lateinit var container: AppContainer
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        networkMonitor = NetworkMonitor(this)
    }
}
