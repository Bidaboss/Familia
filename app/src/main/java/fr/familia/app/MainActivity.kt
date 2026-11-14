package fr.familia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.auth.SessionRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FamiliaApplication
        val container = app.container
        setContent {
            val isOnline by app.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            FamiliaTheme {
                SessionRoute(container = container) { member ->
                    FamiliaApp(container, member, isOffline = !isOnline)
                }
            }
        }
    }
}
