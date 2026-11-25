package fr.neige.stations.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.neige.stations.R
import fr.neige.stations.data.OfficialReading
import fr.neige.stations.data.SnowReport
import fr.neige.stations.data.Station
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Couleurs des repères selon la hauteur de neige au sommet (seuils en cm). */
private val SNOW_LEVELS = listOf(
    0 to 0xFF9AA5B1.toInt(),
    30 to 0xFF7FB3E0.toInt(),
    100 to 0xFF2F7CC4.toInt(),
    200 to 0xFF0B3D6B.toInt(),
)

private fun colorFor(cm: Int?): Int = SNOW_LEVELS.last { (cm ?: -1) >= it.first || it.first == 0 }.second

@Composable
private fun legendLabel(index: Int, u: UnitSystem): String {
    val t = SNOW_LEVELS.map { it.first }
    return when (index) {
        0 -> stringResource(R.string.legend_unknown, fmtDepth(t[1], u))
        SNOW_LEVELS.lastIndex -> stringResource(R.string.legend_plus, fmtDepth(t[index], u))
        else -> stringResource(R.string.legend_range, fmtDepth(t[index], u), fmtDepth(t[index + 1] - 1, u))
    }
}

private fun pin(context: Context, color: Int): Drawable {
    val d = ContextCompat.getDrawable(context, R.drawable.ic_pin)!!.mutate()
    val wrapped = DrawableCompat.wrap(d)
    DrawableCompat.setTint(wrapped, color)
    return wrapped
}

private fun configureOsm(context: Context) {
    Configuration.getInstance().apply {
        // Exigé par les règles d'utilisation des tuiles OpenStreetMap.
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
}

@Composable
fun StationsMap(
    stations: List<Station>,
    reports: Map<String, SnowReport>,
    readings: List<OfficialReading>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val u = LocalUnits.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = 4.0
            maxZoomLevel = 17.0
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(45.0, 6.0))
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            update = { map ->
                map.overlays.removeAll { it is Marker }
                stations.forEach { s ->
                    val r = reports[s.id]
                    val lines = mutableListOf(
                        "${s.country.flag} ${context.getString(s.country.labelRes)} · ${s.massif}",
                        context.getString(R.string.map_base, fmtAlt(s.baseAlt, u), fmtDepth(r?.base?.snowCm, u)),
                        context.getString(R.string.map_top, fmtAlt(s.topAlt, u), fmtDepth(r?.top?.snowCm, u)),
                    )
                    readings.filter { it.stationId == s.id }.forEach { o ->
                        lines += context.getString(R.string.map_reading, o.label, fmtDepth(o.snowCm, u))
                    }
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(s.lat, s.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = pin(context, colorFor(r?.top?.snowCm))
                            title = s.name
                            // La bulle interprète du HTML : chaque ligne est échappée.
                            snippet = lines.joinToString("<br>") { TextUtils.htmlEncode(it) }
                        }
                    )
                }
                // Recadre la carte seulement quand la liste des stations change (filtre, recherche).
                val key = stations.joinToString(",") { it.id }
                if (map.tag != key && stations.isNotEmpty()) {
                    map.tag = key
                    val points = stations.map { GeoPoint(it.lat, it.lon) }
                    map.post {
                        if (points.size == 1) {
                            map.controller.setZoom(11.0)
                            map.controller.setCenter(points[0])
                        } else {
                            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 80)
                        }
                    }
                }
                map.invalidate()
            },
        )

        // Légende
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            Text(stringResource(R.string.legend_title), style = MaterialTheme.typography.labelMedium)
            SNOW_LEVELS.forEachIndexed { i, (_, color) ->
                val label = legendLabel(i, u)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(10.dp).background(Color(color), CircleShape))
                    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        // Attribution obligatoire OpenStreetMap
        Text(
            stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
