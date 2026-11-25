package fr.neige.stations.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

const val MAX_LABEL = 40
const val MAX_ALTITUDE = 5000
const val MAX_SNOW_CM = 2000
private const val MAX_READINGS = 500

/** Relevé de neige publié par la station elle-même (saisi à la main). */
data class OfficialReading(
    val id: String = UUID.randomUUID().toString(),
    val stationId: String,
    val label: String,
    val altitude: Int?,
    val snowCm: Int,
    val date: LocalDate,
)

/** Relevés pré-remplis (source : application officielle de Tignes, 25/11/2026). */
private val DEFAULT_READINGS = listOf(
    OfficialReading("tignes-2100", "tignes", "Tignes Le Lac", 2100, 116, LocalDate.of(2026, 11, 25)),
    OfficialReading("tignes-gm", "tignes", "Grande Motte", 3300, 200, LocalDate.of(2026, 11, 25)),
)

/** Stockage local des relevés sur le téléphone. */
class OfficialReadingsStore(private val prefs: SharedPreferences) {

    fun load(): List<OfficialReading> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULT_READINGS
        val validStations = STATIONS.map { it.id }.toSet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until minOf(arr.length(), MAX_READINGS)).mapNotNull { i ->
                // Un relevé illisible ou hors limites est ignoré, sans faire échouer les autres.
                runCatching {
                    val o = arr.getJSONObject(i)
                    OfficialReading(
                        id = o.getString("id").take(64),
                        stationId = o.getString("stationId"),
                        label = o.getString("label").take(MAX_LABEL),
                        altitude = if (o.isNull("altitude")) null else o.getInt("altitude"),
                        snowCm = o.getInt("snowCm"),
                        date = LocalDate.parse(o.getString("date")),
                    )
                }.getOrNull()?.takeIf {
                    it.stationId in validStations && it.snowCm in 0..MAX_SNOW_CM &&
                        (it.altitude == null || it.altitude in 0..MAX_ALTITUDE)
                }
            }
        }.getOrDefault(DEFAULT_READINGS)
    }

    fun save(list: List<OfficialReading>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("stationId", r.stationId)
                    .put("label", r.label)
                    .put("altitude", r.altitude ?: JSONObject.NULL)
                    .put("snowCm", r.snowCm)
                    .put("date", r.date.toString())
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object { const val KEY = "official_readings" }
}
