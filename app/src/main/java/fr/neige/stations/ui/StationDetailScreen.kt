package fr.neige.stations.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.neige.stations.R
import fr.neige.stations.data.Country
import fr.neige.stations.data.DetailReport
import fr.neige.stations.data.HistoryReport
import fr.neige.stations.data.LevelSeries
import fr.neige.stations.data.Station
import java.time.LocalDateTime
import kotlin.math.max

private val BASE_COLOR = Color(0xFF7FB3E0)
private val MID_COLOR = Color(0xFF2F7CC4)
private val TOP_COLOR = Color(0xFF0B3D6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    station: Station,
    detail: LoadUi<DetailReport>?,
    history: LoadUi<HistoryReport>?,
    onBack: () -> Unit,
) {
    val u = LocalUnits.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        "${station.country.flag} ${stringResource(station.country.labelRes)} · " +
                            "${stringResource(station.range.labelRes)} · ${station.massif}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${fmtAlt(station.baseAlt, u)} – ${fmtAlt(station.topAlt, u)} · " +
                            stringResource(if (station.verified) R.string.alt_verified else R.string.alt_estimated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (detail) {
                null, LoadUi.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadUi.Failed -> item { Text(stringResource(detail.messageRes), color = MaterialTheme.colorScheme.error) }
                is LoadUi.Loaded -> {
                    val r = detail.value
                    item { NowCard(r) }
                    item { DepthChartCard(r) }
                    item { SnowfallCard(r.top) }
                    item { DayTable(r) }
                }
            }
            item { HistoryCard(history) }
            item { LinksCard(station) }
        }
    }
}

/** Valeur la plus proche de l'heure actuelle. */
private fun currentDepth(s: LevelSeries): Int? {
    val now = LocalDateTime.now()
    val i = s.times.indexOfLast { !it.isAfter(now) }.coerceAtLeast(0)
    return s.depthCm.getOrNull(i)
}

@Composable
private fun NowCard(r: DetailReport) {
    val u = LocalUnits.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.detail_now), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(R.string.level_base, r.base, BASE_COLOR),
                    Triple(R.string.level_mid, r.mid, MID_COLOR),
                    Triple(R.string.level_top, r.top, TOP_COLOR),
                ).forEach { (label, s, color) ->
                    Column(Modifier.weight(1f)) {
                        Text("${stringResource(label)} · ${fmtAlt(s.altitude, u)}", style = MaterialTheme.typography.labelMedium)
                        Text(fmtDepth(currentDepth(s), u), style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun DayLabels(r: LevelSeries) {
    Row(Modifier.fillMaxWidth()) {
        r.days.forEach { d ->
            Text(
                fmtDate(R.string.fmt_day, d.date),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DepthChartCard(r: DetailReport) {
    val u = LocalUnits.current
    val series = listOf(r.base to BASE_COLOR, r.mid to MID_COLOR, r.top to TOP_COLOR)
    val maxCm = series.flatMap { it.first.depthCm.filterNotNull() }.maxOrNull() ?: 0
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.detail_chart_depth), style = MaterialTheme.typography.titleSmall)
            Text(
                "max ${fmtDepth(maxCm, u)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
                val top = max(maxCm, 10).toFloat() * 1.1f
                // Lignes de repère : quart, moitié, trois quarts
                for (k in 1..3) {
                    val y = size.height * k / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                series.forEach { (s, color) ->
                    val n = s.depthCm.size
                    if (n < 2) return@forEach
                    val path = Path()
                    var started = false
                    s.depthCm.forEachIndexed { i, v ->
                        if (v == null) { started = false; return@forEachIndexed }
                        val x = size.width * i / (n - 1).toFloat()
                        val y = size.height - size.height * (v / top)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 5f))
                }
            }
            DayLabels(r.top)
            Legend(
                listOf(
                    "${stringResource(R.string.level_base)} ${fmtAlt(r.base.altitude, u)}" to BASE_COLOR,
                    "${stringResource(R.string.level_mid)} ${fmtAlt(r.mid.altitude, u)}" to MID_COLOR,
                    "${stringResource(R.string.level_top)} ${fmtAlt(r.top.altitude, u)}" to TOP_COLOR,
                )
            )
        }
    }
}

@Composable
private fun SnowfallCard(top: LevelSeries) {
    val u = LocalUnits.current
    val maxFall = top.days.mapNotNull { it.snowfallCm }.maxOrNull() ?: 0.0
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.detail_chart_falls), style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 8.dp)) {
                val n = top.days.size
                if (n == 0) return@Canvas
                val slot = size.width / n
                val scale = max(maxFall, 5.0).toFloat()
                top.days.forEachIndexed { i, d ->
                    val v = (d.snowfallCm ?: 0.0).toFloat()
                    val h = size.height * (v / scale)
                    drawRect(
                        TOP_COLOR,
                        topLeft = Offset(i * slot + slot * 0.2f, size.height - h),
                        size = Size(slot * 0.6f, h),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                top.days.forEach { d ->
                    Text(
                        d.snowfallCm?.let { fmtFall(it, u) } ?: "–",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            DayLabels(top)
        }
    }
}

@Composable
private fun DayTable(r: DetailReport) {
    val u = LocalUnits.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.detail_table_title), style = MaterialTheme.typography.titleSmall)
            TableRow(
                listOf(
                    stringResource(R.string.col_day), stringResource(R.string.col_snow), stringResource(R.string.col_rain),
                    stringResource(R.string.col_temp), stringResource(R.string.col_wind), stringResource(R.string.col_freezing),
                ),
                header = true,
            )
            r.top.days.forEach { d ->
                HorizontalDivider()
                TableRow(
                    listOf(
                        fmtDate(R.string.fmt_day, d.date),
                        d.snowfallCm?.let { fmtFall(it, u) } ?: "–",
                        fmtRain(d.rainMm, u),
                        (d.tMinC?.let { fmtTemp(it, u) } ?: "–") + " / " + (d.tMaxC?.let { fmtTemp(it, u) } ?: "–"),
                        fmtWind(d.gustMaxKmh, u),
                        r.freezingMaxM[d.date]?.let { fmtAlt(it.coerceAtLeast(0), u) } ?: "–",
                    )
                )
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    val weights = listOf(1.1f, 1f, 0.9f, 1.4f, 1.1f, 1.1f)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(weights[i]),
            )
        }
    }
}

@Composable
private fun HistoryCard(history: LoadUi<HistoryReport>?) {
    val u = LocalUnits.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleSmall)
            when (history) {
                null, LoadUi.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                is LoadUi.Failed -> Text(stringResource(history.messageRes), color = MaterialTheme.colorScheme.error)
                is LoadUi.Loaded -> {
                    val h = history.value
                    Text(
                        stringResource(R.string.history_text, fmtDate(R.string.fmt_date, h.date), fmtDepth(h.baseCm, u), fmtDepth(h.topCm, u)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.history_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Liens de vérification : une recherche web, pour ne jamais pointer vers une page périmée. */
private fun searchUrl(q: String) = "https://www.google.com/search?q=" + Uri.encode(q)

/** Pages officielles connues et vérifiées. */
private val OFFICIAL_PAGES = mapOf("tignes" to "https://www.tignes.net/meteo-neige?id=1")

@Composable
private fun LinksCard(station: Station) {
    val uri = LocalUriHandler.current
    val avalancheQuery = when (station.country) {
        Country.FR -> "BERA bulletin avalanche ${station.massif}"
        Country.CH -> "SLF avalanche bulletin ${station.name}"
        Country.ES -> "boletín peligro aludes ${station.massif}"
        Country.AD -> "butlletí perill allaus Andorra"
        else -> "avalanche bulletin ${station.massif} ${station.name}"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.links_title), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { uri.openUri(OFFICIAL_PAGES[station.id] ?: searchUrl("${station.name} enneigement bulletin neige")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.link_official)) }
            OutlinedButton(onClick = { uri.openUri(searchUrl("${station.name} webcam")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.link_webcam))
            }
            OutlinedButton(onClick = { uri.openUri(searchUrl(avalancheQuery)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.link_avalanche))
            }
            Text(
                stringResource(R.string.links_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
