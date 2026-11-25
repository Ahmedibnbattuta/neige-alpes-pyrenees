package fr.neige.stations.ui

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.neige.stations.R
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

enum class Filter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all), ALPES(R.string.filter_alps), PYRENEES(R.string.filter_pyrenees), FAVORITES(R.string.filter_favorites)
}
enum class Sort(@StringRes val labelRes: Int) {
    SNOW_TOP(R.string.sort_snow_top), SNOW_BASE(R.string.sort_snow_base), ALTITUDE(R.string.sort_altitude), NAME(R.string.sort_name)
}

data class UiState(
    val query: String = "",
    val filter: Filter = Filter.ALL,
    val sort: Sort = Sort.SNOW_TOP,
    val favorites: Set<String> = emptySet(),
    val reports: Map<String, SnowReport> = emptyMap(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val updatedAt: Long? = null,          // ms : moment où les données ont été reçues
    val units: UnitSystem = UnitSystem.METRIC,
    val readings: List<OfficialReading> = emptyList(),
    @StringRes val notice: Int? = null,
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
                val okQuery = q.isEmpty() || (listOf(s.name, s.massif) + s.country.searchNames)
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
    data class Failed(@StringRes val messageRes: Int) : CompareUi
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
            units = prefs.getString("units", null)
                ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: UnitSystem.default(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val api = SnowApi(app.cacheDir)

    // Au démarrage, des données de moins de 30 min sont réutilisées sans appeler le serveur.
    init { load(AUTO_MAX_AGE_MS) }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun setSort(s: Sort) = _state.update { it.copy(sort = s) }

    fun toggleUnits() {
        _state.update {
            val u = if (it.units == UnitSystem.METRIC) UnitSystem.US else UnitSystem.METRIC
            prefs.edit().putString("units", u.name).apply()
            it.copy(units = u)
        }
    }

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
                _state.update {
                    it.copy(
                        reports = result.reports,
                        loading = false,
                        updatedAt = result.fetchedAt,
                        notice = if (result.rateLimited) R.string.notice_rate_limited else null,
                    )
                }
            } catch (e: RateLimitException) {
                _state.update {
                    it.copy(loading = false, error = R.string.err_rate_limit)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = R.string.err_load)
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
                CompareUi.Failed(R.string.compare_err_rate)
            } catch (e: Exception) {
                CompareUi.Failed(R.string.compare_err)
            }
            _state.update { it.copy(compare = it.compare + (station.id to ui)) }
        }
    }

    private companion object {
        const val AUTO_MAX_AGE_MS = 30 * 60_000L
        const val MANUAL_MAX_AGE_MS = 10 * 60_000L
    }
}
