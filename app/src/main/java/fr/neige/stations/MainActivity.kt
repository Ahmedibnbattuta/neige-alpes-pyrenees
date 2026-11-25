package fr.neige.stations

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.neige.stations.ui.NeigeTheme
import fr.neige.stations.ui.StationsScreen
import fr.neige.stations.ui.StationsViewModel

class MainActivity : ComponentActivity() {
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
