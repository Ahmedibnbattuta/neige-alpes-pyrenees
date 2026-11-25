package fr.neige.stations

import android.content.SharedPreferences
import fr.neige.stations.data.OfficialReading
import fr.neige.stations.data.OfficialReadingsStore
import fr.neige.stations.data.Stations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

class OfficialReadingsTest {
    private lateinit var prefs: FakePrefs

    @Before fun setUp() {
        Stations.setForTests(Stations.parse(File("src/main/assets/stations.json").readText()))
        prefs = FakePrefs()
    }

    @Test fun defaultsWhenNothingSaved() {
        val l = OfficialReadingsStore(prefs).load()
        assertTrue(l.any { it.stationId == "tignes" && it.snowCm == 116 && it.altitude == 2100 })
        assertTrue(l.any { it.stationId == "tignes" && it.snowCm == 200 && it.altitude == 3300 })
    }

    @Test fun saveThenLoad() {
        val store = OfficialReadingsStore(prefs)
        val r = OfficialReading(stationId = "zermatt", label = "Trockener Steg", altitude = 2939, snowCm = 150, date = LocalDate.of(2026, 12, 1))
        store.save(listOf(r))
        assertEquals(listOf(r), store.load())
    }

    /** Un relevé corrompu ou hors limites est ignoré sans faire disparaître les autres. */
    @Test fun invalidEntriesAreSkipped() {
        prefs.strings["official_readings"] = """[
            {"id":"a","stationId":"tignes","label":"ok","altitude":2100,"snowCm":100,"date":"2026-11-25"},
            {"id":"b","stationId":"inconnue","label":"x","altitude":null,"snowCm":10,"date":"2026-11-25"},
            {"id":"c","stationId":"tignes","label":"trop","altitude":null,"snowCm":99999,"date":"2026-11-25"},
            {"id":"d","stationId":"tignes","label":"date","altitude":null,"snowCm":10,"date":"pas une date"}
        ]"""
        val l = OfficialReadingsStore(prefs).load()
        assertEquals(listOf("a"), l.map { it.id })
    }
}

/** SharedPreferences en mémoire, suffisant pour les tests. */
class FakePrefs : SharedPreferences {
    val strings = mutableMapOf<String, String?>()
    override fun getAll(): MutableMap<String, *> = strings
    override fun getString(key: String?, defValue: String?) = strings[key] ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = defValue
    override fun getLong(key: String?, defValue: Long) = defValue
    override fun getFloat(key: String?, defValue: Float) = defValue
    override fun getBoolean(key: String?, defValue: Boolean) = defValue
    override fun contains(key: String?) = strings.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { strings[key!!] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor { strings.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { strings.clear(); return this }
        override fun commit() = true
        override fun apply() {}
    }
}
