package fr.neige.stations.data

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import fr.neige.stations.R

enum class Range(@StringRes val labelRes: Int) { ALPES(R.string.range_alps), PYRENEES(R.string.range_pyrenees) }

/** [searchNames] : noms français et anglais, pour que la recherche marche dans les deux langues. */
enum class Country(@StringRes val labelRes: Int, val flag: String, val searchNames: List<String>) {
    FR(R.string.country_fr, "🇫🇷", listOf("France")),
    CH(R.string.country_ch, "🇨🇭", listOf("Suisse", "Switzerland")),
    AT(R.string.country_at, "🇦🇹", listOf("Autriche", "Austria")),
    IT(R.string.country_it, "🇮🇹", listOf("Italie", "Italy")),
    DE(R.string.country_de, "🇩🇪", listOf("Allemagne", "Germany")),
    SI(R.string.country_si, "🇸🇮", listOf("Slovénie", "Slovenia")),
    ES(R.string.country_es, "🇪🇸", listOf("Espagne", "Spain")),
    AD(R.string.country_ad, "🇦🇩", listOf("Andorre", "Andorra")),
}

/**
 * Une station de ski. Les altitudes (bas / haut du domaine skiable) servent à interroger
 * le modèle météo à la bonne hauteur. [verified] : altitudes vérifiées sur skiresort.info.
 */
data class Station(
    val id: String,
    val name: String,
    val country: Country,
    val range: Range,
    val massif: String,
    val lat: Double,
    val lon: Double,
    val baseAlt: Int,
    val topAlt: Int,
    val verified: Boolean = true,
) {
    val midAlt: Int get() = (baseAlt + topAlt) / 2
}

/**
 * Liste des stations, lue au démarrage depuis assets/stations.json
 * (même fichier que le site web).
 */
object Stations {
    @Volatile private var loaded: List<Station>? = null

    val all: List<Station> get() = loaded ?: error("Stations.init() non appelé")

    fun init(context: Context) {
        if (loaded != null) return
        val text = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        loaded = parse(text)
    }

    /** Pour les tests unitaires uniquement. */
    internal fun setForTests(list: List<Station>) { loaded = list }

    /** Lecture du JSON (séparée pour pouvoir être testée). */
    fun parse(text: String): List<Station> {
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Station(
                id = o.getString("id"),
                name = o.getString("name"),
                country = Country.valueOf(o.getString("country")),
                range = Range.valueOf(o.getString("range")),
                massif = o.getString("massif"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                baseAlt = o.getInt("baseAlt"),
                topAlt = o.getInt("topAlt"),
                verified = o.optBoolean("verified", true),
            )
        }
    }
}

/** Raccourci historique. */
val STATIONS: List<Station> get() = Stations.all
