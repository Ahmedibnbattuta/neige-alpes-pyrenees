package fr.neige.stations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.neige.stations.R
import fr.neige.stations.data.SnowReport
import fr.neige.stations.data.Station

/**
 * Top 10 des stations les plus enneigées, en bas ou au sommet du domaine.
 * Suit le filtre et la recherche de l'écran principal.
 */
@Composable
fun Top10View(
    stations: List<Station>,
    reports: Map<String, SnowReport>,
    onOpen: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = LocalUnits.current
    var atTop by rememberSaveable { mutableStateOf(true) }
    val ranked = stations
        .mapNotNull { s ->
            val cm = reports[s.id]?.let { if (atTop) it.top.snowCm else it.base.snowCm } ?: return@mapNotNull null
            Triple(s, cm, if (atTop) s.topAlt else s.baseAlt)
        }
        .sortedByDescending { it.second }
        .take(10)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = atTop, onClick = { atTop = true }, label = { Text(stringResource(R.string.top10_top)) })
                FilterChip(selected = !atTop, onClick = { atTop = false }, label = { Text(stringResource(R.string.top10_base)) })
            }
        }
        if (ranked.isEmpty()) {
            item { Text(stringResource(R.string.top10_empty), modifier = Modifier.padding(vertical = 24.dp)) }
        }
        itemsIndexed(ranked, key = { _, it -> it.first.id }) { i, (s, cm, alt) ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onOpen(s) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i < 3) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(40.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${s.country.flag} ${stringResource(s.country.labelRes)} · ${s.massif}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtDepth(cm, u), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(fmtAlt(alt, u), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
