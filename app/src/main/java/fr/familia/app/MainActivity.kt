package fr.familia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamiliaTheme {
                FamiliaApp()
            }
        }
    }
}
