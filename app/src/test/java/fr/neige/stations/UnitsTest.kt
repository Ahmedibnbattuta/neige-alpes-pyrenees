package fr.neige.stations

import fr.neige.stations.ui.UnitSystem
import fr.neige.stations.ui.cmToInput
import fr.neige.stations.ui.fmtAlt
import fr.neige.stations.ui.fmtDepth
import fr.neige.stations.ui.fmtFall
import fr.neige.stations.ui.fmtRain
import fr.neige.stations.ui.fmtTemp
import fr.neige.stations.ui.fmtWind
import fr.neige.stations.ui.inputToCm
import fr.neige.stations.ui.inputToM
import fr.neige.stations.ui.mToInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

class UnitsTest {
    private val M = UnitSystem.METRIC
    private val US = UnitSystem.US

    @Test fun depth() {
        assertEquals("200 cm", fmtDepth(200, M))
        assertEquals("79 in", fmtDepth(200, US))
        assertEquals("–", fmtDepth(null, US))
    }

    @Test fun altitude() {
        assertEquals("3456 m", fmtAlt(3456, M))
        assertEquals("11,339 ft", fmtAlt(3456, US))
        assertEquals("10,827 ft", fmtAlt(3300, US))
    }

    @Test fun temperature() {
        assertEquals("-5 °C", fmtTemp(-5.0, M))
        assertEquals("23 °F", fmtTemp(-5.0, US))
        assertEquals("32 °F", fmtTemp(0.0, US))
    }

    @Test fun snowfall() {
        assertEquals("+12 cm", fmtFall(12.0, M))
        assertEquals("+4.7 in", fmtFall(12.0, US))
        assertEquals("+20 in", fmtFall(50.0, US))
    }

    @Test fun rainAndWind() {
        assertEquals("5 mm", fmtRain(5.0, M))
        assertEquals("0.2 in", fmtRain(5.0, US))
        assertEquals("80 km/h", fmtWind(80.0, M))
        assertEquals("50 mph", fmtWind(80.0, US))
    }

    /** Une valeur saisie en pouces / pieds puis relue ne doit dériver que d'une unité au plus. */
    @Test fun inputRoundTrip() {
        for (cm in listOf(0, 1, 50, 116, 200, 2000)) {
            assertTrue(abs(inputToCm(cmToInput(cm, US), US) - cm) <= 2)
            assertEquals(cm, inputToCm(cmToInput(cm, M), M))
        }
        for (m in listOf(0, 1550, 2100, 3300, 5000)) {
            assertTrue(abs(inputToM(mToInput(m, US), US) - m) <= 1)
        }
    }

    @Test fun defaultUnitsFollowCountry() {
        assertEquals(US, UnitSystem.default(Locale.US))
        assertEquals(M, UnitSystem.default(Locale.FRANCE))
        assertEquals(M, UnitSystem.default(Locale.UK))
    }
}
