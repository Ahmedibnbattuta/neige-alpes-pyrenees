package fr.neige.stations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.neige.stations.R
import fr.neige.stations.data.CompareReport
import fr.neige.stations.data.LevelData
import fr.neige.stations.data.MAX_ALTITUDE
import fr.neige.stations.data.MAX_LABEL
import fr.neige.stations.data.MAX_SNOW_CM
import fr.neige.stations.data.OfficialReading
import fr.neige.stations.data.STATIONS
import fr.neige.stations.data.SnowReport
import fr.neige.stations.data.Station
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** Formate une date avec un motif pris dans les ressources de la langue courante. */
@Composable
internal fun fmtDate(patternRes: Int, t: TemporalAccessor): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(stringResource(patternRes), locale).format(t)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(vm: StationsViewModel) {
    val state by vm.state.collectAsState()
    val stations = state.visible
    var editing by remember { mutableStateOf<OfficialReading?>(null) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAlerts by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalUnits provides state.units) {
        val detailStation = detailId?.let { id -> STATIONS.firstOrNull { it.id == id } }
        if (detailStation != null) {
            BackHandler { detailId = null }
            StationDetailScreen(
                station = detailStation,
                detail = state.detail[detailStation.id],
                history = state.history[detailStation.id],
                onBack = { detailId = null },
            )
            return@CompositionLocalProvider
        }
        if (showAlerts) {
            AlertsDialog(
                enabled = state.alertsEnabled,
                thresholdCm = state.alertsThresholdCm,
                hasFavorites = state.favorites.isNotEmpty(),
                onEnabled = vm::setAlerts,
                onThreshold = vm::setAlertsThreshold,
                onDismiss = { showAlerts = false },
            )
        }
        editing?.let { r ->
            ReadingDialog(
                initial = r,
                isNew = state.readings.none { it.id == r.id },
                onSave = { vm.saveReading(it); editing = null },
                onDelete = { vm.deleteReading(r.id); editing = null },
                onDismiss = { editing = null },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    actions = {
                        TextButton(onClick = vm::toggleUnits) {
                            Text(
                                if (state.units == UnitSystem.METRIC) "cm · °C" else "in · °F",
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(0.dp),
                            )
                        }
                        IconButton(onClick = { showMap = !showMap }) {
                            Icon(
                                if (showMap) Icons.AutoMirrored.Filled.List else Icons.Default.Place,
                                contentDescription = stringResource(if (showMap) R.string.show_list else R.string.show_map),
                            )
                        }
                        IconButton(onClick = vm::refresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_alerts)) },
                                    leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                    onClick = { menuOpen = false; showAlerts = true },
                                )
                            }
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
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )

                ChipRow {
                    Filter.entries.forEach { f ->
                        FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(f.labelRes)) })
                    }
                }
                if (!showMap) ChipRow {
                    Text(
                        stringResource(R.string.sort_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    Sort.entries.forEach { s ->
                        FilterChip(selected = state.sort == s, onClick = { vm.setSort(s) }, label = { Text(stringResource(s.labelRes)) })
                    }
                }

                state.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                state.notice?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (showMap) {
                    StationsMap(
                        stations = stations,
                        reports = state.reports,
                        readings = state.readings,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    )
                } else {
                    val count = LocalContext.current.resources.getQuantityString(R.plurals.stations_count, stations.size, stations.size)
                    val refreshed = state.updatedAt?.let {
                        stringResource(
                            R.string.refreshed_at,
                            fmtDate(R.string.fmt_refresh_datetime, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                        )
                    } ?: ""
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                text = count + refreshed,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (stations.isEmpty()) {
                            item {
                                Text(
                                    stringResource(if (state.filter == Filter.FAVORITES) R.string.empty_favorites else R.string.empty_search),
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
                                readings = state.readingsFor(s.id),
                                onEditReading = { editing = it },
                                compare = state.compare[s.id],
                                onCompare = { vm.loadCompare(s) },
                                onOpen = { vm.openDetail(s); detailId = s.id },
                                onAddReading = {
                                    editing = OfficialReading(
                                        stationId = s.id, label = "", altitude = null, snowCm = 0, date = LocalDate.now(),
                                    )
                                },
                            )
                        }
                        item {
                            Text(
                                stringResource(R.string.disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationCard(
    station: Station,
    report: SnowReport?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    readings: List<OfficialReading>,
    onEditReading: (OfficialReading) -> Unit,
    compare: CompareUi?,
    onCompare: () -> Unit,
    onOpen: () -> Unit,
    onAddReading: () -> Unit,
) {
    val u = LocalUnits.current
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${station.country.flag} ${stringResource(station.country.labelRes)} · " +
                            "${stringResource(station.range.labelRes)} · ${station.massif}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LevelColumn(stringResource(R.string.level_base), station.baseAlt, report?.base, Modifier.weight(1f))
                LevelColumn(stringResource(R.string.level_top), station.topAlt, report?.top, Modifier.weight(1f))
            }
            report?.top?.let { top ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val fall = top.snowfall3dCm?.let { stringResource(R.string.forecast_3d, fmtFall(it, u)) }
                    ?: stringResource(R.string.forecast_unavailable)
                val temp = top.tempC?.let { stringResource(R.string.temp_top, fmtTemp(it, u)) } ?: ""
                Text(fall + temp, style = MaterialTheme.typography.bodyMedium)
            }
            when (compare) {
                is CompareUi.Loaded -> ModelCompareTable(station, compare.report)
                CompareUi.Loading -> Text(
                    stringResource(R.string.compare_loading),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                is CompareUi.Failed, null -> {
                    if (compare is CompareUi.Failed) Text(
                        stringResource(compare.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextButton(onClick = onCompare, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.compare_button))
                    }
                }
            }
            report?.updatedAt?.let { t ->
                Text(
                    stringResource(R.string.snow_updated, fmtDate(R.string.fmt_data_datetime, t)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (readings.isNotEmpty()) {
                Text(stringResource(R.string.readings_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                readings.sortedBy { it.altitude ?: 0 }.forEach { r -> ReadingRow(r) { onEditReading(r) } }
            }
            TextButton(onClick = onAddReading, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.add_reading), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ModelCompareTable(station: Station, report: CompareReport) {
    val u = LocalUnits.current
    val na = stringResource(R.string.compare_na)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.labelLarge)
        CompareRow(
            stringResource(R.string.compare_model),
            stringResource(R.string.compare_base, fmtAlt(station.baseAlt, u)),
            stringResource(R.string.compare_top, fmtAlt(station.topAlt, u)),
            stringResource(R.string.compare_falls),
            header = true,
        )
        CompareRow(
            "ICON (DWD)",
            fmtDepth(report.icon?.base?.snowCm, u),
            fmtDepth(report.icon?.top?.snowCm, u),
            fmtFall(report.icon?.top?.snowfall3dCm, u),
        )
        CompareRow("AROME (Météo-France)", na, na, fmtFall(report.arome?.top?.snowfall3dCm, u))
        Text(
            stringResource(R.string.compare_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CompareRow(model: String, base: String, top: String, falls: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (header) FontWeight.Normal else FontWeight.SemiBold
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(model, style = style, color = color, modifier = Modifier.weight(1.4f))
        Text(base, style = style, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(top, style = style, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(falls, style = style, color = color, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
    }
}

@Composable
private fun ReadingRow(r: OfficialReading, onClick: () -> Unit) {
    val u = LocalUnits.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label + (r.altitude?.let { " · " + fmtAlt(it, u) } ?: ""), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.reading_date, fmtDate(R.string.fmt_date, r.date)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(fmtDepth(r.snowCm, u), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Icon(
            Icons.Default.Edit, contentDescription = stringResource(R.string.edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Saisie d'un relevé dans l'unité choisie (cm/m ou in/ft) ; stockage toujours en cm et m. */
@Composable
private fun ReadingDialog(
    initial: OfficialReading,
    isNew: Boolean,
    onSave: (OfficialReading) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val u = LocalUnits.current
    val maxSnow = cmToInput(MAX_SNOW_CM, u)
    val maxAlt = mToInput(MAX_ALTITUDE, u)
    var label by remember { mutableStateOf(initial.label) }
    var altitude by remember { mutableStateOf(initial.altitude?.let { mToInput(it, u).toString() } ?: "") }
    var snow by remember { mutableStateOf(if (isNew) "" else cmToInput(initial.snowCm, u).toString()) }
    val snowValue = snow.toIntOrNull()
    val altValue = altitude.toIntOrNull()
    val valid = label.isNotBlank() && snowValue != null && snowValue in 0..maxSnow &&
        (altitude.isBlank() || (altValue != null && altValue in 0..maxAlt))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.dialog_new else R.string.dialog_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.filterNot(Char::isISOControl).take(MAX_LABEL) },
                    label = { Text(stringResource(R.string.field_label)) }, singleLine = true,
                )
                OutlinedTextField(
                    value = altitude, onValueChange = { altitude = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.field_altitude, u.altitudeUnit, maxAlt)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = snow, onValueChange = { snow = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.field_snow, u.depthUnit, maxSnow)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    stringResource(R.string.dialog_dated, fmtDate(R.string.fmt_date, LocalDate.now())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            altitude = altValue?.let { inputToM(it, u).coerceIn(0, MAX_ALTITUDE) },
                            snowCm = inputToCm(snowValue ?: 0, u).coerceIn(0, MAX_SNOW_CM),
                            date = LocalDate.now(),
                        )
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun LevelColumn(label: String, altitude: Int, data: LevelData?, modifier: Modifier) {
    val u = LocalUnits.current
    Column(modifier) {
        Text("$label · ${fmtAlt(altitude, u)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val cm = data?.snowCm
        Text(
            text = fmtDepth(cm, u),
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
