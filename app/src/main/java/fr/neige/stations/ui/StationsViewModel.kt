package fr.neige.stations.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.neige.stations.data.Range
import fr.neige.stations.data.STATIONS
import fr.neige.stations.data.SnowApi
import fr.neige.stations.data.SnowReport
import fr.neige.stations.data.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Filter(val label: String) { ALL("Toutes"), ALPES("Alpes"), PYRENEES("Pyrénées"), FAVORITES("Favoris") }
enum class Sort(val label: String) { SNOW_TOP("Neige au sommet"), SNOW_BASE("Neige en bas"), ALTITUDE("Altitude"), NAME("Nom") }

data class UiState(
    val query: String = "",
    val filter: Filter = Filter.ALL,
    val sort: Sort = Sort.SNOW_TOP,
    val favorites: Set<String> = emptySet(),
    val reports: Map<String, SnowReport> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: String? = null,
) {
    val visible: List<Station>
        get() {
            val q = query.normalized()
            val filtered = STATIONS.filter { s ->
                val okFilter = when (filter) {
                    Filter.ALL -> true
                    Filter.ALPES -> s.range == Range.ALPES
                    Filter.PYRENEES -> s.range == Range.PYRENEES
                    Filter.FAVORITES -> s.id in favorites
                }
                val okQuery = q.isEmpty() || listOf(s.name, s.massif, s.country.label)
                    .any { it.normalized().contains(q) }
                okFilter && okQuery
            }
            return when (sort) {
                Sort.SNOW_TOP -> filtered.sortedByDescending { reports[it.id]?.top?.snowCm ?: -1 }
                Sort.SNOW_BASE -> filtered.sortedByDescending { reports[it.id]?.base?.snowCm ?: -1 }
                Sort.ALTITUDE -> filtered.sortedByDescending { it.topAlt }
                Sort.NAME -> filtered.sortedBy { it.name.normalized() }
            }
        }
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

class StationsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("neige", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun setSort(s: Sort) = _state.update { it.copy(sort = s) }

    fun toggleFavorite(id: String) {
        _state.update {
            val fav = if (id in it.favorites) it.favorites - id else it.favorites + id
            prefs.edit().putStringSet("favorites", fav).apply()
            it.copy(favorites = fav)
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val reports = SnowApi.fetchAll(STATIONS)
                val now = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH))
                _state.update { it.copy(reports = reports, loading = false, updatedAt = now) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "Impossible de charger les données : ${e.message ?: "erreur réseau"}")
                }
            }
        }
    }
}
