package fr.neige.stations

import fr.neige.stations.data.SnowApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class SnowApiTest {
    @get:Rule val tmp = TemporaryFolder()

    private val twoPoints = """
        [
          {"current":{"time":"2026-11-25T18:00","snow_depth":1.16,"temperature_2m":-3.4},
           "daily":{"snowfall_sum":[2.1,0.0,5.6]}},
          {"current":{"time":"2026-11-25T18:00","snow_depth":null,"temperature_2m":99.0},
           "daily":{"snowfall_sum":[null,null,null]}}
        ]
    """.trimIndent()

    @Test fun parsesSeveralPoints() {
        val l = SnowApi(tmp.root).parse(twoPoints, listOf(2100, 3300), withDepth = true)
        assertEquals(2, l.size)
        assertEquals(116, l[0].snowCm)
        assertEquals(-3.4, l[0].tempC!!, 0.001)
        assertEquals(7.7, l[0].snowfall3dCm!!, 0.001)
        assertEquals(LocalDateTime.of(2026, 11, 25, 18, 0), l[0].time)
        assertEquals(3300, l[1].altitude)
    }

    @Test fun rejectsMissingOrImpossibleValues() {
        val l = SnowApi(tmp.root).parse(twoPoints, listOf(2100, 3300), withDepth = true)
        assertNull(l[1].snowCm)        // null
        assertNull(l[1].tempC)         // 99 °C : impossible
        assertNull(l[1].snowfall3dCm)  // que des null
    }

    @Test fun singleObjectResponse() {
        val one = """{"current":{"snow_depth":0.5},"daily":{"snowfall_sum":[1.0]}}"""
        val l = SnowApi(tmp.root).parse(one, listOf(1500), withDepth = true)
        assertEquals(50, l[0].snowCm)
    }

    @Test fun depthIgnoredWhenModelHasNone() {
        val l = SnowApi(tmp.root).parse(twoPoints, listOf(2100, 3300), withDepth = false)
        assertNull(l[0].snowCm)
        assertEquals(7.7, l[0].snowfall3dCm!!, 0.001)
    }

    /** Une réponse récente en cache est servie sans aucun appel réseau. */
    @Test fun freshCacheIsUsedWithoutNetwork() {
        val api = SnowApi(tmp.root)
        val url = "https://api.open-meteo.com/v1/forecast?test=1"
        api.cacheFile(url).writeText("""{"ok":true}""")
        val c = api.cachedGet(url, maxAgeMs = 60_000)
        assertEquals("""{"ok":true}""", c.body)
        assertTrue(!c.rateLimited)
    }

    /** Lecture du fichier calculé snow.json (bas, milieu, sommet, an dernier). */
    @Test fun parsesComputedFeed() {
        val stations = listOf(
            fr.neige.stations.data.Station("tignes", "Tignes", fr.neige.stations.data.Country.FR, fr.neige.stations.data.Range.ALPES, "Tarentaise", 45.47, 6.9, 1550, 3456),
            fr.neige.stations.data.Station("absente", "Absente", fr.neige.stations.data.Country.FR, fr.neige.stations.data.Range.ALPES, "x", 45.0, 6.0, 1000, 2000),
        )
        val json = """
            {"generated":"2026-11-25T20:00:00Z","lastYearDate":"2025-11-25",
             "stations":{"tignes":{
               "base":{"alt":1550,"now":5,"days":[{"d":"2026-11-25","depth":5,"snowfall":0.0,"tmax":3.1,"tmin":-2.0},{"d":"2026-11-26","depth":7,"snowfall":2.5,"tmax":1,"tmin":-4}]},
               "mid":{"alt":2503,"now":183,"days":[]},
               "top":{"alt":3456,"now":261,"days":[{"d":"2026-11-25","depth":261,"snowfall":4.0,"tmax":-8,"tmin":-14},{"d":"2026-11-26","depth":265,"snowfall":6.0,"tmax":-9,"tmin":-15},{"d":"2026-11-27","depth":266,"snowfall":null,"tmax":null,"tmin":null}]}}},
             "lastYear":{"tignes":{"base":0,"top":140}}}
        """.trimIndent()
        val (feed, generated) = SnowApi(tmp.root).parseFeed(json, stations)
        assertEquals(setOf("tignes"), feed.keys)
        val t = feed.getValue("tignes")
        assertEquals(5, t.base.nowCm)
        assertEquals(183, t.mid.nowCm)
        assertEquals(261, t.top.nowCm)
        assertTrue(t.top.nowCm!! >= t.base.nowCm!!)
        assertEquals(3, t.top.days.size)
        assertNull(t.top.days[2].snowfallCm)
        assertEquals(140, t.lastYear!!.topCm)
        assertEquals(java.time.Instant.parse("2026-11-25T20:00:00Z"), generated)
    }

    @Test fun cacheKeysDifferPerUrl() {
        val api = SnowApi(tmp.root)
        assertTrue(api.cacheFile("https://api.open-meteo.com/a") != api.cacheFile("https://api.open-meteo.com/b"))
    }
}
