package fr.neige.stations.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
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
    val top: LevelData,
    val mid: LevelData? = null,
) {
    /** Date de mise à jour de l'enneigement fournie par Open-Meteo. */
    val updatedAt: LocalDateTime? get() = top.time ?: base.time
}

/** Comparaison des modèles pour une station, chargée à la demande. */
data class CompareReport(
    val icon: ModelData?,   // DWD ICON : hauteur de neige + chutes prévues
    val arome: ModelData?,  // Météo-France AROME : chutes prévues uniquement
)

/** Résultat d'un chargement : données + moment où elles ont été reçues du serveur. */
data class FetchResult(
    val reports: Map<String, SnowReport>,
    val fetchedAt: Long,          // horodatage (ms) de la réponse du serveur
    val rateLimited: Boolean,     // true si la limite d'appels a été atteinte (données plus anciennes)
)

class RateLimitException : Exception("limite d'appels du service météo atteinte")

private const val API_HOST = "api.open-meteo.com"
private const val MAX_RESPONSE_BYTES = 2_000_000

private enum class Level { BASE, MID, TOP }

/** Modèles comparés. AROME ne fournit pas la hauteur de neige au sol sur Open-Meteo. */
private enum class Model(val id: String, val hasSnowDepth: Boolean) {
    ICON("icon_seamless", true),
    AROME("meteofrance_seamless", false),
}

/**
 * Récupère la hauteur de neige modélisée via l'API gratuite Open-Meteo
 * (https://open-meteo.com, licence CC BY 4.0).
 *
 * Open-Meteo compte chaque station comme un appel (limites gratuites : 600/min, 5 000/h,
 * 10 000/jour). Les réponses sont donc gardées en cache sur le téléphone, et la comparaison
 * des modèles n'est demandée que pour la station consultée.
 */
class SnowApi(cacheRoot: File) {

    private val cacheDir = File(cacheRoot, "meteo").apply { mkdirs() }

    /** Liste principale : 2 requêtes (bas et sommet) pour toutes les stations. */
    suspend fun fetchAll(stations: List<Station>, maxAgeMs: Long): FetchResult = withContext(Dispatchers.IO) {
        val urls = listOf(Level.BASE, Level.TOP).associateWith { level ->
            buildUrl(stations.map { Point(it.lat, it.lon, altitudeOf(it, level)) }, null)
        }
        var rateLimited = false
        var oldest = Long.MAX_VALUE
        val bodies = urls.mapValues { (_, url) ->
            val (body, time, limited) = cachedGet(url, maxAgeMs)
            if (limited) rateLimited = true
            oldest = minOf(oldest, time)
            body
        }
        val parsed = bodies.mapValues { (level, body) ->
            parse(body, stations.map { altitudeOf(it, level) }, withDepth = true)
        }
        val reports = stations.indices.associate { i ->
            stations[i].id to SnowReport(
                base = parsed.getValue(Level.BASE)[i],
                top = parsed.getValue(Level.TOP)[i],
            )
        }
        FetchResult(reports, oldest, rateLimited)
    }

    /** Comparaison ICON / AROME pour une station : 2 requêtes de 2 points (bas et sommet). */
    suspend fun fetchCompare(s: Station): CompareReport = withContext(Dispatchers.IO) {
        val points = listOf(Point(s.lat, s.lon, s.baseAlt), Point(s.lat, s.lon, s.topAlt))
        fun load(m: Model): ModelData? = runCatching {
            val (body, _, _) = cachedGet(buildUrl(points, m), COMPARE_MAX_AGE_MS)
            val l = parse(body, listOf(s.baseAlt, s.topAlt), withDepth = m.hasSnowDepth)
            ModelData(l.getOrNull(0), l.getOrNull(1))
        }.getOrNull()
        val icon = load(Model.ICON)
        val arome = load(Model.AROME)
        if (icon == null && arome == null) throw IllegalStateException("comparaison indisponible pour le moment")
        CompareReport(icon, arome)
    }

    private data class Point(val lat: Double, val lon: Double, val elevation: Int)

    private fun altitudeOf(s: Station, level: Level) = when (level) {
        Level.BASE -> s.baseAlt
        Level.MID -> s.midAlt
        Level.TOP -> s.topAlt
    }

    private fun buildUrl(points: List<Point>, model: Model?): String {
        val current = if (model == null || model.hasSnowDepth) "snow_depth,temperature_2m" else "temperature_2m"
        return "https://$API_HOST/v1/forecast" +
            "?latitude=" + points.joinToString(",") { it.lat.toString() } +
            "&longitude=" + points.joinToString(",") { it.lon.toString() } +
            "&elevation=" + points.joinToString(",") { it.elevation.toString() } +
            "&current=$current" +
            "&daily=snowfall_sum&forecast_days=3" +
            (model?.let { "&models=${it.id}" } ?: "") +
            "&timezone=" + URLEncoder.encode("Europe/Paris", "UTF-8")
    }

    private fun parse(rawBody: String, altitudes: List<Int>, withDepth: Boolean): List<LevelData> {
        val body = rawBody.trim()
        // Un seul point -> objet JSON ; plusieurs -> tableau.
        val items: List<JSONObject> = if (body.startsWith("[")) {
            val arr = JSONArray(body)
            List(arr.length()) { arr.getJSONObject(it) }
        } else listOf(JSONObject(body))

        return altitudes.mapIndexed { i, alt ->
            val obj = items.getOrNull(i)
            val current = obj?.optJSONObject("current")
            val depthM = if (withDepth) current?.optDouble("snow_depth", Double.NaN) ?: Double.NaN else Double.NaN
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
                altitude = alt,
                // Valeurs hors limites physiques plausibles -> ignorées.
                snowCm = depthM.takeIf { !it.isNaN() && it in 0.0..30.0 }?.let { (it * 100).roundToInt() },
                tempC = temp.takeIf { !it.isNaN() && it in -60.0..50.0 },
                snowfall3dCm = sum.takeIf { any && it in 0.0..1000.0 },
                time = time,
            )
        }
    }

    // ---------- Cache disque ----------

    private data class Cached(val body: String, val time: Long, val rateLimited: Boolean)

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.json")
    }

    /**
     * Renvoie la réponse en cache si elle a moins de [maxAgeMs], sinon interroge le serveur.
     * Si le serveur répond 429 (trop d'appels) ou est injoignable, renvoie l'ancienne réponse
     * en cache quand il y en a une.
     */
    private fun cachedGet(url: String, maxAgeMs: Long): Cached {
        val f = cacheFile(url)
        val now = System.currentTimeMillis()
        if (f.exists() && now - f.lastModified() < maxAgeMs) {
            return Cached(f.readText(), f.lastModified(), false)
        }
        return try {
            val body = httpGet(url)
            val tmp = File(cacheDir, f.name + ".tmp")
            tmp.writeText(body)
            tmp.renameTo(f)
            Cached(body, now, false)
        } catch (e: RateLimitException) {
            if (f.exists()) Cached(f.readText(), f.lastModified(), true) else throw e
        } catch (e: Exception) {
            // Réseau coupé ou erreur serveur : on garde les dernières données connues.
            if (f.exists()) Cached(f.readText(), f.lastModified(), false) else throw e
        }
    }

    private companion object {
        const val COMPARE_MAX_AGE_MS = 30 * 60_000L
    }

    private fun httpGet(url: String): String {
        val u = URL(url)
        // Seule l'API Open-Meteo, en HTTPS, est autorisée.
        require(u.protocol == "https" && u.host == API_HOST) { "Adresse non autorisée" }
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code == 429) throw RateLimitException()
            if (code !in 200..299) {
                // Le détail de l'erreur serveur n'est pas affiché à l'écran.
                throw IllegalStateException("le serveur météo a répondu $code")
            }
            val type = conn.contentType.orEmpty()
            if (!type.startsWith("application/json")) throw IllegalStateException("réponse inattendue du serveur")
            // Lecture limitée en taille pour éviter une réponse anormalement grosse.
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_RESPONSE_BYTES) throw IllegalStateException("réponse trop volumineuse")
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
