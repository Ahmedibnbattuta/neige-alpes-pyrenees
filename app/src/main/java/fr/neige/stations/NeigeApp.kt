package fr.neige.stations

import android.app.Application
import fr.neige.stations.background.SnowWorker
import fr.neige.stations.data.Stations

/** Point d'entrée de l'application : charge la liste des stations avant tout le reste. */
class NeigeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Stations.init(this)
        SnowWorker.schedule(this)
    }
}
