package fr.neige.stations.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.neige.stations.data.CompareReport
import fr.neige.stations.data.OfficialReading
import fr.neige.stations.data.OfficialReadingsStore
import fr.neige.stations.data.Range
import fr.neige.stations.data.RateLimitException
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
import java.time.Instant
import java.time.ZoneId
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
    val readings: List<OfficialReading> = emptyList(),
    val notice: String? = null,
    val compare: Map<String, CompareUi> = emptyMap(),
) {
    fun readingsFor(stationId: String) = readings.filter { it.stationId == stationId }

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

/** État de la comparaison des modèles pour une station. */
sealed interface CompareUi {
    data object Loading : CompareUi
    data class Loaded(val report: CompareReport) : CompareUi
    data class Failed(val message: String) : CompareUi
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

class StationsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("neige", Context.MODE_PRIVATE)
    private val readingsStore = OfficialReadingsStore(prefs)
    private val _state = MutableStateFlow(
        UiState(
            favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet(),
            readings = readingsStore.load(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val api = SnowApi(app.cacheDir)

    // Au démarrage, des données de moins de 30 min sont réutilisées sans appeler le serveur.
    init { load(AUTO_MAX_AGE_MS) }

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

    /** Ajoute ou remplace (même id) un relevé officiel. */
    fun saveReading(r: OfficialReading) {
        _state.update {
            val list = if (it.readings.any { x -> x.id == r.id }) it.readings.map { x -> if (x.id == r.id) r else x }
            else it.readings + r
            readingsStore.save(list)
            it.copy(readings = list)
        }
    }

    fun deleteReading(id: String) {
        _state.update {
            val list = it.readings.filterNot { x -> x.id == id }
            readingsStore.save(list)
            it.copy(readings = list)
        }
    }

    /** Bouton Actualiser : redemande au serveur si les données ont plus de 10 min. */
    fun refresh() = load(MANUAL_MAX_AGE_MS)

    private fun load(maxAgeMs: Long) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val result = api.fetchAll(STATIONS, maxAgeMs)
                val at = Instant.ofEpochMilli(result.fetchedAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH))
                _state.update {
                    it.copy(
                        reports = result.reports,
                        loading = false,
                        updatedAt = at,
                        notice = if (result.rateLimited)
                            "Le service météo limite le nombre de demandes : dernières données reçues affichées. Réessayez dans quelques minutes."
                        else null,
                    )
                }
            } catch (e: RateLimitException) {
                _state.update {
                    it.copy(loading = false, error = "Le service météo limite le nombre de demandes. Réessayez dans quelques minutes.")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = "Impossible de charger les données : ${e.message ?: "erreur réseau"}")
                }
            }
        }
    }

    /** Comparaison ICON / AROME, demandée seulement pour la station consultée. */
    fun loadCompare(station: Station) {
        if (_state.value.compare[station.id] is CompareUi.Loading) return
        _state.update { it.copy(compare = it.compare + (station.id to CompareUi.Loading)) }
        viewModelScope.launch {
            val ui = try {
                CompareUi.Loaded(api.fetchCompare(station))
            } catch (e: RateLimitException) {
                CompareUi.Failed("Trop de demandes, réessayez dans quelques minutes.")
            } catch (e: Exception) {
                CompareUi.Failed("Comparaison indisponible pour le moment.")
            }
            _state.update { it.copy(compare = it.compare + (station.id to ui)) }
        }
    }

    private companion object {
        const val AUTO_MAX_AGE_MS = 30 * 60_000L
        const val MANUAL_MAX_AGE_MS = 10 * 60_000L
    }
}
