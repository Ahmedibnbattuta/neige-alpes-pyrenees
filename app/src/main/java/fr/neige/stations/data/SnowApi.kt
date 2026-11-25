package fr.neige.stations.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Conditions à une altitude donnée. */
data class LevelData(
    val altitude: Int,
    val snowCm: Int?,          // hauteur de neige au sol, en cm
    val tempC: Double?,        // température actuelle
    val snowfall3dCm: Double?, // chutes de neige prévues sur 3 jours, en cm
    val time: LocalDateTime?,  // date/heure (Europe/Paris) de la valeur de neige
)

/** Valeurs d'un modèle météo précis, en bas et au sommet du domaine. */
data class ModelData(val base: LevelData?, val top: LevelData?)

data class SnowReport(
    val base: LevelData,
    val mid: LevelData,
    val top: LevelData,
    val icon: ModelData? = null,   // DWD ICON : hauteur de neige + chutes prévues
    val arome: ModelData? = null,  // Météo-France AROME : chutes prévues uniquement
) {
    /** Date de mise à jour de l'enneigement fournie par Open-Meteo. */
    val updatedAt: LocalDateTime? get() = top.time ?: mid.time ?: base.time
}

private enum class Level { BASE, MID, TOP }

/** Modèles comparés. AROME ne fournit pas la hauteur de neige au sol sur Open-Meteo. */
private enum class Model(val id: String, val hasSnowDepth: Boolean) {
    ICON("icon_seamless", true),
    AROME("meteofrance_seamless", false),
}

/**
 * Récupère la hauteur de neige modélisée via l'API gratuite Open-Meteo
 * (https://open-meteo.com, licence CC BY 4.0). Une requête par niveau d'altitude,
 * toutes les stations en une seule fois.
 */
object SnowApi {

    suspend fun fetchAll(stations: List<Station>): Map<String, SnowReport> = withContext(Dispatchers.IO) {
        coroutineScope {
            val main = Level.entries.map { level -> async { level to fetchLevel(stations, level, null) } }
            // Comparaison des modèles : bas et sommet. Un échec ici ne bloque pas l'affichage principal.
            val compare = Model.entries.flatMap { m ->
                listOf(Level.BASE, Level.TOP).map { level ->
                    async { (m to level) to runCatching { fetchLevel(stations, level, m) }.getOrNull() }
                }
            }
            val results = main.awaitAll().toMap()
            val cmp = compare.awaitAll().toMap()

            fun modelData(m: Model, i: Int): ModelData? {
                val b = cmp[m to Level.BASE]?.getOrNull(i)
                val t = cmp[m to Level.TOP]?.getOrNull(i)
                return if (b == null && t == null) null else ModelData(b, t)
            }

            stations.indices.associate { i ->
                stations[i].id to SnowReport(
                    base = results.getValue(Level.BASE)[i],
                    mid = results.getValue(Level.MID)[i],
                    top = results.getValue(Level.TOP)[i],
                    icon = modelData(Model.ICON, i),
                    arome = modelData(Model.AROME, i),
                )
            }
        }
    }

    private fun altitudeOf(s: Station, level: Level) = when (level) {
        Level.BASE -> s.baseAlt
        Level.MID -> s.midAlt
        Level.TOP -> s.topAlt
    }

    private fun fetchLevel(stations: List<Station>, level: Level, model: Model?): List<LevelData> {
        val lats = stations.joinToString(",") { it.lat.toString() }
        val lons = stations.joinToString(",") { it.lon.toString() }
        val elevs = stations.joinToString(",") { altitudeOf(it, level).toString() }
        val current = if (model == null || model.hasSnowDepth) "snow_depth,temperature_2m" else "temperature_2m"
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lats&longitude=$lons&elevation=$elevs" +
            "&current=$current" +
            "&daily=snowfall_sum&forecast_days=3" +
            (model?.let { "&models=${it.id}" } ?: "") +
            "&timezone=" + URLEncoder.encode("Europe/Paris", "UTF-8")

        val body = httpGet(url).trim()
        // Une seule station -> objet JSON ; plusieurs -> tableau.
        val items: List<JSONObject> = if (body.startsWith("[")) {
            val arr = JSONArray(body)
            List(arr.length()) { arr.getJSONObject(it) }
        } else listOf(JSONObject(body))

        return stations.mapIndexed { i, s ->
            val obj = items.getOrNull(i)
            val current = obj?.optJSONObject("current")
            val depthM = current?.optDouble("snow_depth", Double.NaN) ?: Double.NaN
            val temp = current?.optDouble("temperature_2m", Double.NaN) ?: Double.NaN
            // "time" est au format ISO local, ex. "2026-11-25T18:00"
            val time = current?.optString("time")?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val falls = obj?.optJSONObject("daily")?.optJSONArray("snowfall_sum")
            var sum = 0.0
            var any = false
            if (falls != null) for (k in 0 until falls.length()) {
                val v = falls.optDouble(k, Double.NaN)
                if (!v.isNaN()) { sum += v; any = true }
            }
            LevelData(
                altitude = altitudeOf(s, level),
                snowCm = if (depthM.isNaN()) null else (depthM * 100).roundToInt().coerceAtLeast(0),
                tempC = if (temp.isNaN()) null else temp,
                snowfall3dCm = if (any) sum else null,
                time = time,
            )
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Erreur serveur $code ${err.take(200)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
