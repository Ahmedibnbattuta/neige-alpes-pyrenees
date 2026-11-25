package fr.neige.stations

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.neige.stations.ui.NeigeTheme
import fr.neige.stations.ui.StationsScreen
import fr.neige.stations.ui.StationsViewModel

class MainActivity : ComponentActivity() {
    // Applique la langue choisie dans l'app (anglais par défaut), quelle que soit celle du téléphone.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeigeTheme {
                val vm: StationsViewModel = viewModel()
                StationsScreen(vm)
            }
        }
    }
}
