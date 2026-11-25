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

    @Test fun cacheKeysDifferPerUrl() {
        val api = SnowApi(tmp.root)
        assertTrue(api.cacheFile("https://api.open-meteo.com/a") != api.cacheFile("https://api.open-meteo.com/b"))
    }
}
