package fr.familia.app

import android.app.Application
import fr.familia.app.core.data.AppContainer

class FamiliaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
