package fr.neige.stations

import fr.neige.stations.data.Range
import fr.neige.stations.data.Stations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Vérifie la cohérence du fichier assets/stations.json (partagé avec le site web). */
class StationsDataTest {
    private val stations = Stations.parse(File("src/main/assets/stations.json").readText())

    @Test fun enoughStations() {
        assertTrue("au moins 150 stations", stations.size >= 150)
        assertTrue(stations.any { it.range == Range.ALPES })
        assertTrue(stations.any { it.range == Range.PYRENEES })
        assertTrue(stations.any { it.range == Range.ATLAS })
        assertTrue(stations.any { it.range == Range.TURQUIE })
    }

    @Test fun uniqueIds() {
        assertEquals(stations.size, stations.map { it.id }.toSet().size)
    }

    @Test fun altitudesAreConsistent() {
        stations.forEach {
            assertTrue("${it.id} : bas < haut", it.baseAlt < it.topAlt)
            assertTrue("${it.id} : bas plausible", it.baseAlt in 200..3000)
            assertTrue("${it.id} : haut plausible", it.topAlt in 1000..5200)
        }
    }

    /** Les coordonnées doivent tomber dans la bonne chaîne de montagnes. */
    @Test fun coordinatesInTheRightRange() {
        stations.forEach {
            when (it.range) {
                Range.ALPES -> assertTrue("${it.id} hors Alpes", it.lat in 43.8..47.8 && it.lon in 5.0..16.0)
                Range.PYRENEES -> assertTrue("${it.id} hors Pyrénées", it.lat in 42.0..43.4 && it.lon in -2.0..3.2)
                Range.ATLAS -> assertTrue("${it.id} hors Atlas", it.lat in 30.0..37.0 && it.lon in -10.0..9.0)
                Range.TURQUIE -> assertTrue("${it.id} hors Turquie", it.lat in 35.8..42.2 && it.lon in 26.0..45.0)
            }
        }
    }

    /** Les stations historiques doivent garder leur identifiant (favoris et relevés déjà enregistrés). */
    @Test fun historicIdsKept() {
        val ids = stations.map { it.id }.toSet()
        listOf("tignes", "chamonix", "valthorens", "zermatt", "saintlary", "baqueira", "grandvalira").forEach {
            assertTrue("$it manquant", it in ids)
        }
    }
}
