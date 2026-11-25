package fr.neige.stations.background

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ligne affichée dans le widget. Valeurs stockées en cm ; l'unité est appliquée à l'affichage. */
data class WidgetRow(val name: String, val topCm: Int?, val next48hCm: Double?)

/** Instantané des favoris partagé entre la tâche de fond et le widget. */
object WidgetData {
    private const val PREFS = "neige"
    private const val KEY = "widget_snapshot"
    private const val KEY_TIME = "widget_snapshot_time"

    fun save(context: Context, rows: List<WidgetRow>) {
        val arr = JSONArray()
        rows.forEach { r ->
            arr.put(
                JSONObject()
                    .put("name", r.name)
                    .put("topCm", r.topCm ?: JSONObject.NULL)
                    .put("next48hCm", r.next48hCm ?: JSONObject.NULL)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): Pair<List<WidgetRow>, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList<WidgetRow>() to 0L
        val rows = runCatching {
            val arr = JSONArray(raw)
            List(minOf(arr.length(), 20)) { i ->
                val o = arr.getJSONObject(i)
                WidgetRow(
                    name = o.getString("name").take(60),
                    topCm = if (o.isNull("topCm")) null else o.getInt("topCm"),
                    next48hCm = if (o.isNull("next48hCm")) null else o.getDouble("next48hCm"),
                )
            }
        }.getOrDefault(emptyList())
        return rows to prefs.getLong(KEY_TIME, 0L)
    }
}
