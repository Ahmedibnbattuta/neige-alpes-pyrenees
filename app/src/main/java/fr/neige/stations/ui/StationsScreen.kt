package fr.neige.stations.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.neige.stations.data.LevelData
import fr.neige.stations.data.SnowReport
import fr.neige.stations.data.Station
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(vm: StationsViewModel) {
    val state by vm.state.collectAsState()
    val stations = state.visible

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neige Alpes & Pyrénées", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Rechercher une station, un massif, un pays") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )

            ChipRow {
                Filter.entries.forEach { f ->
                    FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                }
            }
            ChipRow {
                Text("Trier :", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                Sort.entries.forEach { s ->
                    FilterChip(selected = state.sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) })
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "${stations.size} stations" +
                            (state.updatedAt?.let { " · mis à jour à $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (stations.isEmpty()) {
                    item {
                        Text(
                            if (state.filter == Filter.FAVORITES) "Aucun favori pour l'instant : touchez le cœur d'une station pour l'ajouter."
                            else "Aucune station ne correspond à votre recherche.",
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(stations, key = { it.id }) { s ->
                    StationCard(
                        station = s,
                        report = state.reports[s.id],
                        favorite = s.id in state.favorites,
                        onFavorite = { vm.toggleFavorite(s.id) },
                    )
                }
                item {
                    Text(
                        "Hauteurs de neige estimées par modèle météo à l'altitude indiquée (pas des relevés officiels des stations). " +
                            "Altitudes des domaines indicatives. Données : Open-Meteo.com (CC BY 4.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun StationCard(station: Station, report: SnowReport?, favorite: Boolean, onFavorite: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${station.country.flag} ${station.country.label} · ${station.range.label} · ${station.massif}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelColumn("Bas", station.baseAlt, report?.base, Modifier.weight(1f))
                LevelColumn("Milieu", station.midAlt, report?.mid, Modifier.weight(1f))
                LevelColumn("Sommet", station.topAlt, report?.top, Modifier.weight(1f))
            }
            report?.top?.let { top ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val fall = top.snowfall3dCm?.let { "+${it.roundToInt()} cm prévus sur 3 jours" } ?: "Prévision indisponible"
                val temp = top.tempC?.let { " · ${it.roundToInt()} °C au sommet" } ?: ""
                Text(fall + temp, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LevelColumn(label: String, altitude: Int, data: LevelData?, modifier: Modifier) {
    Column(modifier) {
        Text("$label · $altitude m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val cm = data?.snowCm
        Text(
            text = if (cm == null) "–" else "$cm cm",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { ((cm ?: 0) / 300f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
